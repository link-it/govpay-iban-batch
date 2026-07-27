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
import it.govpay.iban.batch.dto.IbanPagopa;
import it.govpay.iban.batch.gde.service.GdeService;
import it.govpay.pagopa.backoffice.client.model.CIIbansResource;
import it.govpay.pagopa.backoffice.client.model.CIIbansResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for interacting with pagoPA API.
 * Resolves the IBAN PagoPA connector per-domain via {@link PagoPaApiClientFactory},
 * following the chain: IntermediarioEntity.codConnettoreBackofficeEc
 */
@Service
@Slf4j
public class IbanPagopaApiService {

    private final BatchProperties batchProperties;
    private final GdeService gdeService;
    private final PagoPaApiClientFactory pagoPaApiClientFactory;

    public IbanPagopaApiService(BatchProperties batchProperties,
                                GdeService gdeService,
                                PagoPaApiClientFactory pagoPaApiClientFactory) {
        this.batchProperties = batchProperties;
        this.gdeService = gdeService;
        this.pagoPaApiClientFactory = pagoPaApiClientFactory;
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
                pagoPaApiClientFactory.getOrCreateApi(brokerCode).getBrokerIbansWithHttpInfo(
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
		gdeService.saveGetIbansKo(codIntermediario, startTime, endTime, lastResponseEntity, e, pagoPaApiClientFactory.getBaseUrl(codIntermediario));
	}

	private void saveGetIbansOk(String codIntermediario, OffsetDateTime startTime,
			List<IbanPagopa> allIbans, ResponseEntity<CIIbansResponse> lastResponseEntity) {
		OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC);
		gdeService.saveGetIbansOk(codIntermediario, startTime, endTime, allIbans.size(), lastResponseEntity, pagoPaApiClientFactory.getBaseUrl(codIntermediario));
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
