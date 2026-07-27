package it.govpay.iban.batch.config;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import it.govpay.common.client.model.Connettore;
import it.govpay.common.client.service.ConnettoreService;
import it.govpay.common.entity.IntermediarioEntity;
import it.govpay.common.repository.IntermediarioRepository;
import it.govpay.pagopa.backoffice.client.ApiClient;
import it.govpay.pagopa.backoffice.client.api.ExternalApisApi;
import lombok.extern.slf4j.Slf4j;

/**
 * Risolve e crea (con cache) istanze di {@link ExternalApisApi} per intermediario,
 * seguendo la catena IntermediarioEntity.codConnettoreBackofficeEc -> Connettore.
 * Componente condiviso tra tutti i servizi che chiamano l'API pagoPA backoffice
 * (check IBAN, sync anagrafica Enti Creditori).
 */
@Component
@Slf4j
public class PagoPaApiClientFactory {

    private final IntermediarioRepository intermediarioRepository;
    private final ConnettoreService connettoreService;
    private final IbanPagopaApiClientConfig ibanPagopaApiClientConfig;

    /** Cache of ExternalApisApi instances keyed by connector code */
    private final ConcurrentHashMap<String, ExternalApisApi> apiCache = new ConcurrentHashMap<>();

    public PagoPaApiClientFactory(IntermediarioRepository intermediarioRepository,
                                  ConnettoreService connettoreService,
                                  IbanPagopaApiClientConfig ibanPagopaApiClientConfig) {
        this.intermediarioRepository = intermediarioRepository;
        this.connettoreService = connettoreService;
        this.ibanPagopaApiClientConfig = ibanPagopaApiClientConfig;
    }

    /**
     * Resolves the connector code for the given codIntermediario via IntermediarioRepository.
     */
    private String resolveConnectorCode(String codIntermediario) {
        Optional<IntermediarioEntity> intermediarioOpt = intermediarioRepository.findByCodIntermediario(codIntermediario);
        IntermediarioEntity intermediario = intermediarioOpt.orElseThrow(() ->
            new IllegalStateException("Nessun intermediario trovato: " + codIntermediario));

        String codConnettore = intermediario.getCodConnettoreBackofficeEc();
        if (codConnettore == null || codConnettore.isBlank()) {
            throw new IllegalStateException(
                "Connettore IBAN check non configurato per l'intermediario " + intermediario.getCodIntermediario());
        }

        log.debug("Intermediario {} -> Connettore IBAN check: {}",
                  intermediario.getCodIntermediario(), codConnettore);
        return codConnettore;
    }

    /**
     * Gets or creates an ExternalApisApi instance for the given intermediario.
     * Uses a cache keyed by connector code to avoid creating duplicate instances
     * for domains sharing the same intermediary.
     */
    public ExternalApisApi getOrCreateApi(String brokerCode) {
        String codConnettore = resolveConnectorCode(brokerCode);
        return apiCache.computeIfAbsent(codConnettore, code -> {
            RestTemplate restTemplate = connettoreService.getRestTemplate(code);

            // Customize JsonMapper (Jackson 3) for pagoPA date handling
            JacksonJsonHttpMessageConverter converter =
                new JacksonJsonHttpMessageConverter(ibanPagopaApiClientConfig.createPagoPAObjectMapper());
            restTemplate.getMessageConverters().removeIf(JacksonJsonHttpMessageConverter.class::isInstance);
            restTemplate.getMessageConverters().add(0, converter);

            Connettore connettore = connettoreService.getConnettore(code);
            ApiClient apiClient = new ApiClient(restTemplate);
            apiClient.setBasePath(connettore.getUrl());

            log.info("Creata istanza ExternalApisApi per connettore {} (URL: {})", code, connettore.getUrl());
            return new ExternalApisApi(apiClient);
        });
    }

    /**
     * Returns the pagoPA base URL for the given intermediario (for GDE event tracking).
     * Delegates to ConnettoreService which has its own internal caching.
     */
    public String getBaseUrl(String codIntermediario) {
        String codConnettore = resolveConnectorCode(codIntermediario);
        return connettoreService.getConnettore(codConnettore).getUrl();
    }

    /**
     * Svuota la cache delle istanze ExternalApisApi.
     * Alla prossima invocazione verranno ricreate con i dati aggiornati dal DB.
     */
    public void clearApiCache() {
        log.info("Pulizia cache ExternalApisApi ({} entries)", apiCache.size());
        apiCache.clear();
    }
}
