package it.govpay.iban.batch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import it.govpay.iban.batch.config.BatchProperties;
import it.govpay.iban.batch.config.PagoPaApiClientFactory;
import it.govpay.iban.batch.dto.IbanPagopa;
import it.govpay.iban.batch.gde.service.GdeService;
import it.govpay.pagopa.backoffice.client.api.ExternalApisApi;
import it.govpay.pagopa.backoffice.client.model.CIIbansResource;
import it.govpay.pagopa.backoffice.client.model.CIIbansResponse;
import it.govpay.pagopa.backoffice.client.model.PageInfo;

/**
 * La risoluzione del connettore e la cache delle istanze {@link ExternalApisApi}
 * sono state estratte in {@link PagoPaApiClientFactory} (vedi
 * {@code PagoPaApiClientFactoryTest}). Qui si testa solo la logica propria di
 * {@link IbanPagopaApiService}: paginazione, gestione errori, tracking GDE.
 */
@ExtendWith(MockitoExtension.class)
class IbanPagopaApiServiceTest {

    @Mock
    private BatchProperties batchProperties;

    @Mock
    private GdeService gdeService;

    @Mock
    private PagoPaApiClientFactory pagoPaApiClientFactory;

    @Mock
    private ExternalApisApi externalApisApi;

    private IbanPagopaApiService service;

    private static final String COD_INTERMEDIARIO = "12345678901";

    @BeforeEach
    void setUp() {
        service = new IbanPagopaApiService(batchProperties, gdeService, pagoPaApiClientFactory);
    }

    private void stubApi() {
        lenient().when(pagoPaApiClientFactory.getOrCreateApi(COD_INTERMEDIARIO)).thenReturn(externalApisApi);
        lenient().when(pagoPaApiClientFactory.getBaseUrl(COD_INTERMEDIARIO)).thenReturn("https://api.pagopa.it");
        lenient().when(batchProperties.getPageSize()).thenReturn(1000);
    }

    private CIIbansResource createIbanResource(String iban, String fiscalCode, String name) {
        CIIbansResource resource = new CIIbansResource();
        resource.setIban(iban);
        resource.setCiFiscalCode(fiscalCode);
        resource.setCiName(name);
        resource.setStatus("ENABLED");
        resource.setDescription("Conto corrente");
        resource.setLabel("label");
        resource.setValidityDate(OffsetDateTime.now());
        return resource;
    }

    private CIIbansResponse createResponse(List<CIIbansResource> ibans, int page, long totalPages) {
        CIIbansResponse response = new CIIbansResponse();
        response.setIbans(ibans);
        PageInfo pageInfo = new PageInfo();
        pageInfo.setPage(page);
        pageInfo.setTotalPages(totalPages);
        pageInfo.setTotalElements((long) ibans.size());
        pageInfo.setLimit(1000);
        response.setPageInfo(pageInfo);
        return response;
    }

    // ============ Propagazione errori dal client factory ============

    @Test
    void getAllIbans_whenFactoryThrowsIllegalState_shouldWrapInRestClientException() {
        when(pagoPaApiClientFactory.getOrCreateApi(COD_INTERMEDIARIO))
                .thenThrow(new IllegalStateException("Nessun intermediario trovato: " + COD_INTERMEDIARIO));

        // fetchIbansPage's generic catch(Exception) wraps any failure (including
        // connector-resolution errors from the factory) into a RestClientException.
        RestClientException thrown = assertThrows(RestClientException.class,
                () -> service.getAllIbans(COD_INTERMEDIARIO));
        assertTrue(thrown.getCause() instanceof IllegalStateException);
    }

    // ============ Single page success ============

    @Test
    void getAllIbans_singlePage_shouldReturnIbansAndCallGdeOk() throws Exception {
        stubApi();

        CIIbansResource iban1 = createIbanResource("IT60X0542811101000000123456", "01234567890", "Comune A");
        CIIbansResource iban2 = createIbanResource("IT60X0542811101000000789012", "09876543210", "Comune B");
        CIIbansResponse response = createResponse(List.of(iban1, iban2), 1, 1);

        when(externalApisApi.getBrokerIbansWithHttpInfo(eq(COD_INTERMEDIARIO), eq(1), eq(1000), any()))
                .thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        List<IbanPagopa> result = service.getAllIbans(COD_INTERMEDIARIO);

        assertEquals(2, result.size());
        assertEquals("IT60X0542811101000000123456", result.get(0).getIban());
        assertEquals("01234567890", result.get(0).getFiscalCode());
        assertEquals("Comune A", result.get(0).getName());
        assertEquals(COD_INTERMEDIARIO, result.get(0).getCodIntermediario());
        assertEquals("ENABLED", result.get(0).getStatus());
        assertEquals("IT60X0542811101000000789012", result.get(1).getIban());

        // Verify GDE OK event was sent
        verify(gdeService).saveGetIbansOk(eq(COD_INTERMEDIARIO), any(), any(), eq(2), any(), eq("https://api.pagopa.it"));
    }

    // ============ Multi-page pagination ============

    @Test
    void getAllIbans_multiPage_shouldPaginateAndReturnAll() throws Exception {
        stubApi();

        CIIbansResource iban1 = createIbanResource("IT11111111111111111111111111", "01234567890", "Comune A");
        CIIbansResponse page1 = createResponse(List.of(iban1), 1, 2);

        CIIbansResource iban2 = createIbanResource("IT22222222222222222222222222", "09876543210", "Comune B");
        CIIbansResponse page2 = createResponse(List.of(iban2), 2, 2);

        when(externalApisApi.getBrokerIbansWithHttpInfo(eq(COD_INTERMEDIARIO), eq(1), eq(1000), any()))
                .thenReturn(new ResponseEntity<>(page1, HttpStatus.OK));
        when(externalApisApi.getBrokerIbansWithHttpInfo(eq(COD_INTERMEDIARIO), eq(2), eq(1000), any()))
                .thenReturn(new ResponseEntity<>(page2, HttpStatus.OK));

        List<IbanPagopa> result = service.getAllIbans(COD_INTERMEDIARIO);

        assertEquals(2, result.size());
        assertEquals("IT11111111111111111111111111", result.get(0).getIban());
        assertEquals("IT22222222222222222222222222", result.get(1).getIban());
    }

    // ============ Response with null body ============

    @Test
    void getAllIbans_nullBody_shouldReturnEmptyList() throws Exception {
        stubApi();

        when(externalApisApi.getBrokerIbansWithHttpInfo(eq(COD_INTERMEDIARIO), eq(1), eq(1000), any()))
                .thenReturn(new ResponseEntity<>((CIIbansResponse) null, HttpStatus.OK));

        List<IbanPagopa> result = service.getAllIbans(COD_INTERMEDIARIO);

        assertTrue(result.isEmpty());
        verify(gdeService).saveGetIbansOk(eq(COD_INTERMEDIARIO), any(), any(), eq(0), any(), eq("https://api.pagopa.it"));
    }

    // ============ Response with empty ibans list ============

    @Test
    void getAllIbans_emptyIbansList_shouldReturnEmptyList() throws Exception {
        stubApi();

        CIIbansResponse response = createResponse(List.of(), 1, 1);
        when(externalApisApi.getBrokerIbansWithHttpInfo(eq(COD_INTERMEDIARIO), eq(1), eq(1000), any()))
                .thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        List<IbanPagopa> result = service.getAllIbans(COD_INTERMEDIARIO);

        assertTrue(result.isEmpty());
    }

    // ============ Response with null ibans field ============

    @Test
    void getAllIbans_nullIbansField_shouldReturnEmptyList() throws Exception {
        stubApi();

        CIIbansResponse response = new CIIbansResponse();
        response.setIbans(null);
        PageInfo pageInfo = new PageInfo();
        pageInfo.setPage(1);
        pageInfo.setTotalPages(1L);
        response.setPageInfo(pageInfo);

        when(externalApisApi.getBrokerIbansWithHttpInfo(eq(COD_INTERMEDIARIO), eq(1), eq(1000), any()))
                .thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        List<IbanPagopa> result = service.getAllIbans(COD_INTERMEDIARIO);

        assertTrue(result.isEmpty());
    }

    // ============ ResourceAccessException with "closed" ============

    @Test
    void getAllIbans_resourceAccessExceptionClosed_shouldReturnEmptyList() throws Exception {
        stubApi();

        when(externalApisApi.getBrokerIbansWithHttpInfo(eq(COD_INTERMEDIARIO), eq(1), eq(1000), any()))
                .thenThrow(new ResourceAccessException("I/O error: connection closed"));

        List<IbanPagopa> result = service.getAllIbans(COD_INTERMEDIARIO);

        assertTrue(result.isEmpty());
        // GDE OK event should be sent (empty result is still success)
        verify(gdeService).saveGetIbansOk(eq(COD_INTERMEDIARIO), any(), any(), eq(0), any(), eq("https://api.pagopa.it"));
    }

    // ============ ResourceAccessException generic (not "closed") ============

    @Test
    void getAllIbans_resourceAccessExceptionGeneric_shouldThrowAndCallGdeKo() throws Exception {
        stubApi();

        when(externalApisApi.getBrokerIbansWithHttpInfo(eq(COD_INTERMEDIARIO), eq(1), eq(1000), any()))
                .thenThrow(new ResourceAccessException("I/O error: connection timeout"));

        assertThrows(RestClientException.class, () -> service.getAllIbans(COD_INTERMEDIARIO));

        verify(gdeService).saveGetIbansKo(eq(COD_INTERMEDIARIO), any(), any(), any(), any(), eq("https://api.pagopa.it"));
    }

    // ============ Generic exception wrapping ============

    @Test
    void getAllIbans_genericException_shouldWrapInRestClientExceptionAndCallGdeKo() throws Exception {
        stubApi();

        when(externalApisApi.getBrokerIbansWithHttpInfo(eq(COD_INTERMEDIARIO), eq(1), eq(1000), any()))
                .thenThrow(new RuntimeException("Unexpected error"));

        RestClientException thrown = assertThrows(RestClientException.class,
                () -> service.getAllIbans(COD_INTERMEDIARIO));

        assertTrue(thrown.getMessage().contains("Fallito il recupero degli IBAN"));
        verify(gdeService).saveGetIbansKo(eq(COD_INTERMEDIARIO), any(), any(), any(), any(), eq("https://api.pagopa.it"));
    }

    // ============ convertIban mapping ============

    @Test
    void getAllIbans_shouldMapAllFieldsCorrectly() throws Exception {
        stubApi();

        OffsetDateTime validityDate = OffsetDateTime.now();
        CIIbansResource resource = new CIIbansResource();
        resource.setIban("IT99999999999999999999999999");
        resource.setCiFiscalCode("FISCAL_CODE");
        resource.setCiName("CI_NAME");
        resource.setStatus("DISABLED");
        resource.setDescription("DESCRIPTION");
        resource.setLabel("LABEL");
        resource.setValidityDate(validityDate);

        CIIbansResponse response = createResponse(List.of(resource), 1, 1);
        when(externalApisApi.getBrokerIbansWithHttpInfo(eq(COD_INTERMEDIARIO), eq(1), eq(1000), any()))
                .thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        List<IbanPagopa> result = service.getAllIbans(COD_INTERMEDIARIO);

        assertEquals(1, result.size());
        IbanPagopa mapped = result.get(0);
        assertEquals(COD_INTERMEDIARIO, mapped.getCodIntermediario());
        assertEquals("IT99999999999999999999999999", mapped.getIban());
        assertEquals("FISCAL_CODE", mapped.getFiscalCode());
        assertEquals("CI_NAME", mapped.getName());
        assertEquals("DISABLED", mapped.getStatus());
        assertEquals("DESCRIPTION", mapped.getDescription());
        assertEquals("LABEL", mapped.getLabel());
        assertEquals(validityDate, mapped.getValidityDate());
    }
}
