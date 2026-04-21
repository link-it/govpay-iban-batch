package it.govpay.iban.batch.gde.service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.govpay.common.client.gde.HttpDataHolder;
import it.govpay.common.configurazione.service.ConfigurazioneService;
import it.govpay.common.gde.AbstractGdeService;
import it.govpay.common.gde.GdeEventInfo;
import it.govpay.common.gde.GdeUtils;
import it.govpay.common.configurazione.model.GdeInterfaccia;
import it.govpay.common.configurazione.model.Giornale;
import it.govpay.gde.client.beans.ComponenteEvento;
import it.govpay.gde.client.beans.NuovoEvento;
import it.govpay.iban.batch.Costanti;
import it.govpay.iban.batch.gde.mapper.EventoIbanMapper;
import it.govpay.pagopa.backoffice.client.model.CIIbansResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for sending IBAN check events to the GDE microservice.
 * <p>
 * Extends {@link AbstractGdeService} from govpay-common for RestTemplate-based
 * async event sending via ConfigurazioneService.
 * <p>
 * Events include:
 * - IOrganizationsController_getAllPublishedFlows: Fetching list of published flows
 * - IOrganizationsController_getSinglePublishedFlow: Fetching single flow details
 * - PROCESS_FLOW: Processing flow data (internal operation)
 * - SAVE_FLOW: Saving flow data (internal operation)
 */
@Slf4j
@Service
public class GdeService extends AbstractGdeService {

	private final EventoIbanMapper eventoIbanMapper;
    private final ConfigurazioneService configurazioneService;

    public GdeService(ObjectMapper objectMapper,
                      @Qualifier("asyncHttpExecutor") Executor asyncHttpExecutor,
                      EventoIbanMapper eventoIbanMapper,
                      ConfigurazioneService configurazioneService) {
        super(objectMapper, asyncHttpExecutor, configurazioneService);
        this.eventoIbanMapper = eventoIbanMapper;
        this.configurazioneService = configurazioneService;
    }

    @Override
    protected String getGdeEndpoint() {
        return configurazioneService.getServizioGDE().getUrl() + "/eventi";
    }

    @Override
    protected GdeInterfaccia getConfigurazioneComponente(ComponenteEvento componente, Giornale giornale) {
        return GdeUtils.getConfigurazioneComponente(componente, giornale);
    }

    @Override
    protected NuovoEvento convertToGdeEvent(GdeEventInfo eventInfo) {
        throw new UnsupportedOperationException(
                "GdeService usa sendEventAsync(NuovoEvento) direttamente, non il pattern GdeEventInfo");
    }

    /**
     * Sends an event to GDE asynchronously using the inherited async executor
     * and RestTemplate from ConfigurazioneService.
     *
     * @param nuovoEvento Event to send
     */
    public void sendEventAsync(NuovoEvento nuovoEvento) {
        if (!isAbilitato()) {
            log.debug("Connettore GDE disabilitato, evento {} non inviato", nuovoEvento.getTipoEvento());
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                getGdeRestTemplate().postForEntity(getGdeEndpoint(), nuovoEvento, Void.class);
                log.debug("Evento {} inviato con successo al GDE", nuovoEvento.getTipoEvento());
            } catch (Exception ex) {
                log.warn("Impossibile inviare evento {} al GDE (il batch continua normalmente): {}",
                        nuovoEvento.getTipoEvento(), ex.getMessage());
                log.debug("Dettaglio errore GDE:", ex);
            } finally {
                HttpDataHolder.clear();
            }
        }, this.asyncExecutor);
    }

    /**
     * Builds the URL for getAllIbans using GdeUtils.buildUrl().
     */ 
    private String buildGetAllIbansUrl(String pagoPABaseUrl, String codIntermediario) {
        Map<String, String> pathParams = Map.of("{brokerCode}", codIntermediario);
        return GdeUtils.buildUrl(pagoPABaseUrl, Costanti.PATH_GET_ALL_IBANS, pathParams, null);
    }   

    /**
     * Sets the response payload on the event using the common GdeUtils.extractResponsePayload().
     */
    private void setResponsePayload(NuovoEvento nuovoEvento, ResponseEntity<?> responseEntity,
                                     RestClientException exception) {
        if (nuovoEvento.getParametriRisposta() != null) {
            nuovoEvento.getParametriRisposta().setPayload(
                extractResponsePayload(responseEntity, exception));
        }
    }

	public void saveGetIbansKo(String codIntermediario, OffsetDateTime dataStart, OffsetDateTime dataEnd,
			ResponseEntity<CIIbansResponse> responseEntity, RestClientException exception, String pagoPABaseUrl) {
        String transactionId = UUID.randomUUID().toString();
        String url = buildGetAllIbansUrl(pagoPABaseUrl, codIntermediario);

        NuovoEvento nuovoEvento = eventoIbanMapper.createEventoKo(
                Costanti.OPERATION_GET_ALL_IBANS, transactionId, dataStart, dataEnd,
                null, exception);

        eventoIbanMapper.setParametriRichiesta(nuovoEvento, url, "GET", GdeUtils.getCapturedRequestHeadersAsGdeHeaders());
        eventoIbanMapper.setParametriRisposta(nuovoEvento, dataEnd, null, exception);

        setResponsePayload(nuovoEvento, responseEntity, exception);

        sendEventAsync(nuovoEvento);
	}

	public void saveGetIbansOk(String codIntermediario, OffsetDateTime dataStart, OffsetDateTime dataEnd, int ibansCount,
			ResponseEntity<CIIbansResponse> responseEntity, String pagoPABaseUrl) {
        String transactionId = UUID.randomUUID().toString();
        String url = buildGetAllIbansUrl(pagoPABaseUrl, codIntermediario);

        NuovoEvento nuovoEvento = eventoIbanMapper.createEventoOk(
                Costanti.OPERATION_GET_ALL_IBANS, transactionId, dataStart, dataEnd);

        nuovoEvento.setDettaglioEsito(String.format("Retrieved %d ibans", ibansCount));

        eventoIbanMapper.setParametriRichiesta(nuovoEvento, url, "GET", GdeUtils.getCapturedRequestHeadersAsGdeHeaders());
        eventoIbanMapper.setParametriRisposta(nuovoEvento, dataEnd, responseEntity, null);

        setResponsePayload(nuovoEvento, responseEntity, null);
        sendEventAsync(nuovoEvento);
	}

}
