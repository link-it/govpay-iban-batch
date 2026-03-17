package it.govpay.iban.batch.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import it.govpay.common.client.model.Connettore;
import it.govpay.common.client.service.ConnettoreService;
import it.govpay.common.entity.IntermediarioEntity;
import it.govpay.common.repository.IntermediarioRepository;
import it.govpay.iban.batch.config.BatchProperties;
import it.govpay.iban.batch.config.IbanPagopaApiClientConfig;
import it.govpay.iban.batch.dto.IbanPagopa;
import it.govpay.iban.batch.gde.service.GdeService;
import it.govpay.pagopa.backoffice.client.ApiClient;
import it.govpay.pagopa.backoffice.client.api.ExternalApisApi;
import it.govpay.pagopa.backoffice.client.model.CIIbansResource;
import it.govpay.pagopa.backoffice.client.model.CIIbansResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for interacting with pagoPA API.
 * Resolves the IBAN PagoPA connector per-domain via IntermediarioRepository,
 * following the chain: IntermediarioEntity.codConnettoreBackofficeEc
 */
@Service
@Slf4j
public class IbanPagopaApiService {

    private final BatchProperties batchProperties;
    private final GdeService gdeService;
    private final IntermediarioRepository intermediarioRepository;
    private final ConnettoreService connettoreService;
    private final IbanPagopaApiClientConfig ibanPagopaApiClientConfig;

    /** Cache of ExternalApisApi instances keyed by connector code */
    private final ConcurrentHashMap<String, ExternalApisApi> apiCache = new ConcurrentHashMap<>();

    public IbanPagopaApiService(BatchProperties batchProperties,
                                ConnettoreService connettoreService,
                                IntermediarioRepository intermediarioRepository,
                                GdeService gdeService,
                                IbanPagopaApiClientConfig ibanPagopaApiClientConfig) {
        this.batchProperties = batchProperties;
        this.connettoreService = connettoreService;
        this.intermediarioRepository = intermediarioRepository;
        this.gdeService = gdeService;
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
    private ExternalApisApi getOrCreateApi(String brokerCode) {
        String codConnettore = resolveConnectorCode(brokerCode);
        return apiCache.computeIfAbsent(codConnettore, code -> {
            RestTemplate restTemplate = connettoreService.getRestTemplate(code);

            // Customize ObjectMapper for pagoPA date handling
            MappingJackson2HttpMessageConverter converter =
                new MappingJackson2HttpMessageConverter(ibanPagopaApiClientConfig.createPagoPAObjectMapper());
            restTemplate.getMessageConverters().removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
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
    private String getBaseUrl(String codIntermediario) {
        String codConnettore = resolveConnectorCode(codIntermediario);
        return connettoreService.getConnettore(codConnettore).getUrl();
    }

	private void logNoResponse(String codIntermediario, ResponseEntity<CIIbansResponse> lastResponseEntity,
			                   Long currentPage, PageFetchResult<CIIbansResponse> result) {
		if (result.success && lastResponseEntity != null) {
		    log.warn("Risposta con body vuoto per l'intermediario {} alla pagina {}", codIntermediario, currentPage);
		}
	}

	private ResponseEntity<CIIbansResponse> fetchIbansLoop(String codIntermediario, List<IbanPagopa> allIbans) {
		ResponseEntity<CIIbansResponse> lastResponseEntity = null;
        Long currentPage = 1L;
        boolean hasMorePages = true;

		while (hasMorePages) {
		    PageFetchResult<CIIbansResponse> result = fetchIbansPage(codIntermediario, currentPage);

		    lastResponseEntity = result.responseEntity;

		    CIIbansResponse response = (result.success && lastResponseEntity != null) ? lastResponseEntity.getBody() : null;

		    if (response == null) {
		        logNoResponse(codIntermediario, lastResponseEntity, currentPage, result);
		        hasMorePages = false;
		    } else {
		        logInfoResponseOk(codIntermediario, response);
		        aggiungiIbanRicevutiAllElenco(codIntermediario, allIbans, currentPage, response);

		        hasMorePages = response.getPageInfo().getPage() < response.getPageInfo().getTotalPages();
		        currentPage++;
		    }
		}
		return lastResponseEntity;
	}

    /**
     * Get all IBANs for a intermediario with pagination
     */
    public List<IbanPagopa> getAllIbans(String codIntermediario) throws RestClientException {
        log.debug("Recupero degli IBAN per l'intermediario {}", codIntermediario);

        OffsetDateTime startTime = OffsetDateTime.now(ZoneOffset.UTC);
        List<IbanPagopa> allIbans = new ArrayList<>();
        ResponseEntity<CIIbansResponse> lastResponseEntity = null;

        try {
            lastResponseEntity = fetchIbansLoop(codIntermediario, allIbans);

            log.info("Recuperati in totale {} ibans per l'intermediario {}", allIbans.size(), codIntermediario);

            saveGetIbansOk(codIntermediario, startTime, allIbans, lastResponseEntity);

            return allIbans;

        } catch (RestClientException e) {
            saveGetIbansKo(codIntermediario, startTime, lastResponseEntity, e);
            throw e;
        }
    }

    /**
     * Fetches a single page of IBANs from the API.
     * Extracted to avoid nested try blocks (SonarQube java:S1141).
     */
    private PageFetchResult<CIIbansResponse> fetchIbansPage( String brokerCode, Long currentPage) throws RestClientException {
        try {
            log.debug("Chiamata API per l'intermediario {} pagina {}", brokerCode, currentPage);

            ResponseEntity<CIIbansResponse> responseEntity =
                getOrCreateApi(brokerCode).getBrokerIbansWithHttpInfo(
                	brokerCode,
                	Integer.valueOf(currentPage.intValue()),         // page
                    Integer.valueOf(batchProperties.getPageSize()),  // size
                    null
                );

            return new PageFetchResult<>(responseEntity, true);

        } catch (org.springframework.web.client.ResourceAccessException e) {
            boolean shouldContinue = !gestioneRispostaVuota(brokerCode, currentPage, e);
            return new PageFetchResult<>(null, shouldContinue);
        } catch (Exception e) {
            log.error("Errore nel recupero degli IBAN per l'intermediario {} alla pagina {}: {}",
            		  brokerCode, currentPage, e.getMessage());
            log.error(e.getMessage(), e);
            throw new RestClientException("Fallito il recupero degli IBAN per l'intermediario " + brokerCode, e);
        }
    }

	private boolean gestioneRispostaVuota(String codIntermediario, Long currentPage, org.springframework.web.client.ResourceAccessException e) {
		// Gestione risposta vuota (connessione chiusa) - normale quando non ci sono flussi disponibili
		if (e.getMessage() != null && e.getMessage().contains("closed")) {
		    log.info("Nessun IBAN disponibile per l'intermediario {} (risposta vuota)", codIntermediario);
		    return true;
		} else {
		    log.error("Errore I/O nel recupero degli IBAN per l'intermediario {} alla pagina {}: {}",
		    		  codIntermediario, currentPage, e.getMessage());
		    throw new RestClientException("Fallito il recupero degli IBAN per l'intermediario " + codIntermediario, e);
		}
	}

	private IbanPagopa convertIban(String codIntermediario, CIIbansResource ciIban) {
		return IbanPagopa.builder()
				.codIntermediario(codIntermediario)
				.fiscalCode(ciIban.getCiFiscalCode())
				.name(ciIban.getCiName())
				.description(ciIban.getDescription())
				.iban(ciIban.getIban())
				.label(ciIban.getLabel())
				.status(ciIban.getStatus())
				.validityDate(ciIban.getValidityDate())
				.build();
	}

	private void aggiungiIbanRicevutiAllElenco(String codIntermediario, List<IbanPagopa> allIbans, Long currentPage, CIIbansResponse response) {
		if (response.getIbans() != null && !response.getIbans().isEmpty()) {
		    allIbans.addAll(response.getIbans().stream().map(ciIban -> convertIban(codIntermediario, ciIban)).toList());
		    log.info("Recuperata pagina {} con {} IBAN per l'intermediario {}",
		        currentPage, response.getIbans().size(), codIntermediario);
		} else {
		    log.info("Pagina {} ha restituito dati vuoti per l'organizzazione {}", currentPage, codIntermediario);
		}
	}

	private void logInfoResponseOk(String codIntermediario, CIIbansResponse response) {
		log.info("Chiamata API completata per l'intermediario {}, risposta ricevuta: ibans={}",
			codIntermediario,
		    response.getIbans() != null ? response.getIbans().size() + " iban" : "null");
	}

	private void saveGetIbansKo(String codIntermediario, OffsetDateTime startTime,
			ResponseEntity<CIIbansResponse> lastResponseEntity, RestClientException e) {
		OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC);
		gdeService.saveGetIbansKo(codIntermediario, startTime, endTime, lastResponseEntity, e, getBaseUrl(codIntermediario));
	}

	private void saveGetIbansOk(String codIntermediario, OffsetDateTime startTime,
			List<IbanPagopa> allIbans, ResponseEntity<CIIbansResponse> lastResponseEntity) {
		OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC);
		gdeService.saveGetIbansOk(codIntermediario, startTime, endTime, allIbans.size(), lastResponseEntity, getBaseUrl(codIntermediario));
	}

    /**
     * Svuota la cache delle istanze ExternalApisApi.
     * Alla prossima invocazione verranno ricreate con i dati aggiornati dal DB.
     */
    public void clearApiCache() {
        log.info("Pulizia cache ExternalApisApi ({} entries)", apiCache.size());
        apiCache.clear();
    }

    /**
     * Helper class to encapsulate the result of a page fetch operation.
     * Used to avoid nested try blocks.
     */
    private static class PageFetchResult<T> {
        final ResponseEntity<T> responseEntity;
        final boolean success;

        PageFetchResult(ResponseEntity<T> responseEntity, boolean success) {
            this.responseEntity = responseEntity;
            this.success = success;
        }
    }
}
