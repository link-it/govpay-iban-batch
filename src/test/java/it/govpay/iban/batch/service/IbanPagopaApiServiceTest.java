package it.govpay.iban.batch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import it.govpay.common.client.model.Connettore;
import it.govpay.common.client.service.ConnettoreService;
import it.govpay.common.entity.IntermediarioEntity;
import it.govpay.common.repository.IntermediarioRepository;
import it.govpay.iban.batch.config.BatchProperties;
import it.govpay.iban.batch.config.IbanPagopaApiClientConfig;
import it.govpay.iban.batch.dto.IbanPagopa;
import it.govpay.iban.batch.gde.service.GdeService;
import it.govpay.pagopa.backoffice.client.api.ExternalApisApi;
import it.govpay.pagopa.backoffice.client.model.CIIbansResource;
import it.govpay.pagopa.backoffice.client.model.CIIbansResponse;
import it.govpay.pagopa.backoffice.client.model.PageInfo;

@ExtendWith(MockitoExtension.class)
class IbanPagopaApiServiceTest {

    @Mock
    private BatchProperties batchProperties;

    @Mock
    private GdeService gdeService;

    @Mock
    private IntermediarioRepository intermediarioRepository;

    @Mock
    private ConnettoreService connettoreService;

    @Mock
    private IbanPagopaApiClientConfig ibanPagopaApiClientConfig;

    @Mock
    private ExternalApisApi externalApisApi;

    private IbanPagopaApiService service;

    private static final String COD_INTERMEDIARIO = "12345678901";
    private static final String COD_CONNETTORE = "CONN_BACKOFFICE_EC";

    @BeforeEach
    void setUp() {
        service = new IbanPagopaApiService(batchProperties, connettoreService,
                intermediarioRepository, gdeService, ibanPagopaApiClientConfig);
    }

    private IntermediarioEntity createIntermediario(String codConnettore) {
        return IntermediarioEntity.builder()
                .codIntermediario(COD_INTERMEDIARIO)
                .codConnettoreBackofficeEc(codConnettore)
                .abilitato(true)
                .build();
    }

    /**
     * Injects the mock ExternalApisApi into the apiCache, bypassing getOrCreateApi() creation logic.
     * Also stubs intermediarioRepository and connettoreService for resolveConnectorCode/getBaseUrl.
     */
    private void injectMockApi() {
        // Inject mock into apiCache
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, ExternalApisApi> apiCache =
                (ConcurrentHashMap<String, ExternalApisApi>) ReflectionTestUtils.getField(service, "apiCache");
        apiCache.put(COD_CONNETTORE, externalApisApi);

        // Stub resolveConnectorCode (used by getOrCreateApi cache lookup and getBaseUrl)
        lenient().when(intermediarioRepository.findByCodIntermediario(COD_INTERMEDIARIO))
                .thenReturn(Optional.of(createIntermediario(COD_CONNETTORE)));

        // Stub getBaseUrl -> connettoreService.getConnettore
        Connettore connettore = new Connettore();
        connettore.setUrl("https://api.pagopa.it");
        lenient().when(connettoreService.getConnettore(COD_CONNETTORE)).thenReturn(connettore);

        // Stub pageSize
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

    // ============ Validation tests (existing) ============

    @Test
    void getAllIbans_withNoIntermediario_shouldThrow() {
        when(intermediarioRepository.findByCodIntermediario(COD_INTERMEDIARIO))
                .thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> service.getAllIbans(COD_INTERMEDIARIO));
    }

    @Test
    void getAllIbans_withNullConnettoreCode_shouldThrow() {
        when(intermediarioRepository.findByCodIntermediario(COD_INTERMEDIARIO))
                .thenReturn(Optional.of(createIntermediario(null)));

        assertThrows(IllegalStateException.class, () -> service.getAllIbans(COD_INTERMEDIARIO));
    }

    @Test
    void getAllIbans_withBlankConnettoreCode_shouldThrow() {
        when(intermediarioRepository.findByCodIntermediario(COD_INTERMEDIARIO))
                .thenReturn(Optional.of(createIntermediario("   ")));

        assertThrows(IllegalStateException.class, () -> service.getAllIbans(COD_INTERMEDIARIO));
    }

    // ============ Single page success ============

    @Test
    void getAllIbans_singlePage_shouldReturnIbansAndCallGdeOk() throws Exception {
        injectMockApi();

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
        injectMockApi();

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
        injectMockApi();

        when(externalApisApi.getBrokerIbansWithHttpInfo(eq(COD_INTERMEDIARIO), eq(1), eq(1000), any()))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        List<IbanPagopa> result = service.getAllIbans(COD_INTERMEDIARIO);

        assertTrue(result.isEmpty());
        verify(gdeService).saveGetIbansOk(eq(COD_INTERMEDIARIO), any(), any(), eq(0), any(), eq("https://api.pagopa.it"));
    }

    // ============ Response with empty ibans list ============

    @Test
    void getAllIbans_emptyIbansList_shouldReturnEmptyList() throws Exception {
        injectMockApi();

        CIIbansResponse response = createResponse(List.of(), 1, 1);
        when(externalApisApi.getBrokerIbansWithHttpInfo(eq(COD_INTERMEDIARIO), eq(1), eq(1000), any()))
                .thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        List<IbanPagopa> result = service.getAllIbans(COD_INTERMEDIARIO);

        assertTrue(result.isEmpty());
    }

    // ============ Response with null ibans field ============

    @Test
    void getAllIbans_nullIbansField_shouldReturnEmptyList() throws Exception {
        injectMockApi();

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
        injectMockApi();

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
        injectMockApi();

        when(externalApisApi.getBrokerIbansWithHttpInfo(eq(COD_INTERMEDIARIO), eq(1), eq(1000), any()))
                .thenThrow(new ResourceAccessException("I/O error: connection timeout"));

        assertThrows(RestClientException.class, () -> service.getAllIbans(COD_INTERMEDIARIO));

        verify(gdeService).saveGetIbansKo(eq(COD_INTERMEDIARIO), any(), any(), any(), any(), eq("https://api.pagopa.it"));
    }

    // ============ Generic exception wrapping ============

    @Test
    void getAllIbans_genericException_shouldWrapInRestClientExceptionAndCallGdeKo() throws Exception {
        injectMockApi();

        when(externalApisApi.getBrokerIbansWithHttpInfo(eq(COD_INTERMEDIARIO), eq(1), eq(1000), any()))
                .thenThrow(new RuntimeException("Unexpected error"));

        RestClientException thrown = assertThrows(RestClientException.class,
                () -> service.getAllIbans(COD_INTERMEDIARIO));

        assertTrue(thrown.getMessage().contains("Fallito il recupero degli IBAN"));
        verify(gdeService).saveGetIbansKo(eq(COD_INTERMEDIARIO), any(), any(), any(), any(), eq("https://api.pagopa.it"));
    }

    // ============ API cache reuse ============

    @Test
    void getAllIbans_calledTwice_shouldReuseApiFromCache() throws Exception {
        injectMockApi();

        CIIbansResponse response = createResponse(List.of(), 1, 1);
        // Use lenient since called twice
        lenient().when(externalApisApi.getBrokerIbansWithHttpInfo(eq(COD_INTERMEDIARIO), eq(1), eq(1000), any()))
                .thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        service.getAllIbans(COD_INTERMEDIARIO);
        service.getAllIbans(COD_INTERMEDIARIO);

        // apiCache should still have exactly 1 entry (same mock reused)
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, ExternalApisApi> apiCache =
                (ConcurrentHashMap<String, ExternalApisApi>) ReflectionTestUtils.getField(service, "apiCache");
        assertEquals(1, apiCache.size());
        assertEquals(externalApisApi, apiCache.get(COD_CONNETTORE));
    }

    // ============ convertIban mapping ============

    @Test
    void getAllIbans_shouldMapAllFieldsCorrectly() throws Exception {
        injectMockApi();

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
