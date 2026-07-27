package it.govpay.iban.batch.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import it.govpay.iban.batch.config.BatchProperties;
import it.govpay.iban.batch.config.PagoPaApiClientFactory;
import it.govpay.iban.batch.dto.EnteCreditorePagopa;
import it.govpay.iban.batch.gde.service.GdeService;
import it.govpay.pagopa.backoffice.client.model.BrokerInstitutionResource;
import it.govpay.pagopa.backoffice.client.model.BrokerInstitutionsResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for retrieving the Enti Creditori (Creditor Institutions) registry
 * from the pagoPA API, for a given intermediario/broker. Mirrors the pagination
 * and error-handling structure of {@link IbanPagopaApiService#getAllIbans}.
 */
@Service
@Slf4j
public class EntiCreditoriApiService {

    private final BatchProperties batchProperties;
    private final GdeService gdeService;
    private final PagoPaApiClientFactory pagoPaApiClientFactory;

    public EntiCreditoriApiService(BatchProperties batchProperties,
                                   GdeService gdeService,
                                   PagoPaApiClientFactory pagoPaApiClientFactory) {
        this.batchProperties = batchProperties;
        this.gdeService = gdeService;
        this.pagoPaApiClientFactory = pagoPaApiClientFactory;
    }

    /**
     * Get all Enti Creditori for an intermediario with pagination
     */
    public List<EnteCreditorePagopa> getAllEntiCreditori(String codIntermediario) throws RestClientException {
        log.debug("Recupero dell'anagrafica Enti Creditori per l'intermediario {}", codIntermediario);

        OffsetDateTime startTime = OffsetDateTime.now(ZoneOffset.UTC);
        List<EnteCreditorePagopa> allEnti = new ArrayList<>();
        ResponseEntity<BrokerInstitutionsResponse> lastResponseEntity = null;

        try {
            lastResponseEntity = fetchEntiLoop(codIntermediario, allEnti);

            log.info("Recuperati in totale {} enti creditori per l'intermediario {}", allEnti.size(), codIntermediario);

            saveGetEntiOk(codIntermediario, startTime, allEnti, lastResponseEntity);

            return allEnti;

        } catch (RestClientException e) {
            saveGetEntiKo(codIntermediario, startTime, lastResponseEntity, e);
            throw e;
        }
    }

    private ResponseEntity<BrokerInstitutionsResponse> fetchEntiLoop(String codIntermediario, List<EnteCreditorePagopa> allEnti) {
        ResponseEntity<BrokerInstitutionsResponse> lastResponseEntity = null;
        Long currentPage = 1L;
        boolean hasMorePages = true;

        while (hasMorePages) {
            PageFetchResult<BrokerInstitutionsResponse> result = fetchEntiPage(codIntermediario, currentPage);

            lastResponseEntity = result.responseEntity;

            BrokerInstitutionsResponse response = (result.success && lastResponseEntity != null) ? lastResponseEntity.getBody() : null;

            if (response == null) {
                if (result.success && lastResponseEntity != null) {
                    log.warn("Risposta con body vuoto per l'intermediario {} alla pagina {}", codIntermediario, currentPage);
                }
                hasMorePages = false;
            } else {
                aggiungiEntiRicevutiAllElenco(codIntermediario, allEnti, currentPage, response);
                hasMorePages = response.getPageInfo().getPage() < response.getPageInfo().getTotalPages();
                currentPage++;
            }
        }
        return lastResponseEntity;
    }

    /**
     * Fetches a single page of Enti Creditori from the API.
     * Extracted to avoid nested try blocks (SonarQube java:S1141).
     */
    private PageFetchResult<BrokerInstitutionsResponse> fetchEntiPage(String brokerCode, Long currentPage) throws RestClientException {
        try {
            log.debug("Chiamata API creditor_institutions per l'intermediario {} pagina {}", brokerCode, currentPage);

            ResponseEntity<BrokerInstitutionsResponse> responseEntity =
                pagoPaApiClientFactory.getOrCreateApi(brokerCode).getBrokerInstitutionsWithHttpInfo(
                    brokerCode,
                    Integer.valueOf(currentPage.intValue()),         // page
                    Integer.valueOf(batchProperties.getPageSize()),  // limit
                    null
                );

            return new PageFetchResult<>(responseEntity, true);

        } catch (org.springframework.web.client.ResourceAccessException e) {
            boolean shouldContinue = !gestioneRispostaVuota(brokerCode, currentPage, e);
            return new PageFetchResult<>(null, shouldContinue);
        } catch (Exception e) {
            log.error("Errore nel recupero degli enti creditori per l'intermediario {} alla pagina {}: {}",
                      brokerCode, currentPage, e.getMessage());
            log.error(e.getMessage(), e);
            throw new RestClientException("Fallito il recupero degli enti creditori per l'intermediario " + brokerCode, e);
        }
    }

    private boolean gestioneRispostaVuota(String codIntermediario, Long currentPage, org.springframework.web.client.ResourceAccessException e) {
        // Gestione risposta vuota (connessione chiusa) - normale quando non ci sono enti disponibili
        if (e.getMessage() != null && e.getMessage().contains("closed")) {
            log.info("Nessun ente creditore disponibile per l'intermediario {} (risposta vuota)", codIntermediario);
            return true;
        } else {
            log.error("Errore I/O nel recupero degli enti creditori per l'intermediario {} alla pagina {}: {}",
                      codIntermediario, currentPage, e.getMessage());
            throw new RestClientException("Fallito il recupero degli enti creditori per l'intermediario " + codIntermediario, e);
        }
    }

    private EnteCreditorePagopa convertEnte(String codIntermediario, BrokerInstitutionResource resource) {
        return EnteCreditorePagopa.builder()
                .codIntermediario(codIntermediario)
                .taxCode(resource.getTaxCode())
                .companyName(resource.getCompanyName())
                .stationId(resource.getStationId())
                .auxDigit(resource.getAuxDigit())
                .segregationCode(resource.getSegregationCode())
                .cbillCode(resource.getCbillCode())
                .build();
    }

    private void aggiungiEntiRicevutiAllElenco(String codIntermediario, List<EnteCreditorePagopa> allEnti, Long currentPage, BrokerInstitutionsResponse response) {
        if (response.getCreditorInstitutions() != null && !response.getCreditorInstitutions().isEmpty()) {
            allEnti.addAll(response.getCreditorInstitutions().stream()
                    .map(resource -> convertEnte(codIntermediario, resource)).toList());
            log.info("Recuperata pagina {} con {} enti creditori per l'intermediario {}",
                    currentPage, response.getCreditorInstitutions().size(), codIntermediario);
        } else {
            log.info("Pagina {} ha restituito dati vuoti per l'intermediario {}", currentPage, codIntermediario);
        }
    }

    private void saveGetEntiKo(String codIntermediario, OffsetDateTime startTime,
            ResponseEntity<BrokerInstitutionsResponse> lastResponseEntity, RestClientException e) {
        OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC);
        gdeService.saveGetBrokerInstitutionsKo(codIntermediario, startTime, endTime, lastResponseEntity, e,
                pagoPaApiClientFactory.getBaseUrl(codIntermediario));
    }

    private void saveGetEntiOk(String codIntermediario, OffsetDateTime startTime,
            List<EnteCreditorePagopa> allEnti, ResponseEntity<BrokerInstitutionsResponse> lastResponseEntity) {
        OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC);
        gdeService.saveGetBrokerInstitutionsOk(codIntermediario, startTime, endTime, allEnti.size(), lastResponseEntity,
                pagoPaApiClientFactory.getBaseUrl(codIntermediario));
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
