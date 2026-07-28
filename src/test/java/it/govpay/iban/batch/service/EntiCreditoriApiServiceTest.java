package it.govpay.iban.batch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import it.govpay.iban.batch.dto.EnteCreditorePagopa;
import it.govpay.iban.batch.gde.service.GdeService;
import it.govpay.pagopa.backoffice.client.api.ExternalApisApi;
import it.govpay.pagopa.backoffice.client.model.BrokerInstitutionResource;
import it.govpay.pagopa.backoffice.client.model.BrokerInstitutionsResponse;
import it.govpay.pagopa.backoffice.client.model.PageInfo;

@ExtendWith(MockitoExtension.class)
class EntiCreditoriApiServiceTest {

    @Mock
    private BatchProperties batchProperties;

    @Mock
    private GdeService gdeService;

    @Mock
    private PagoPaApiClientFactory pagoPaApiClientFactory;

    @Mock
    private ExternalApisApi externalApisApi;

    private EntiCreditoriApiService service;

    private static final String COD_INTERMEDIARIO = "12345678901";

    @BeforeEach
    void setUp() {
        service = new EntiCreditoriApiService(batchProperties, gdeService, pagoPaApiClientFactory);
    }

    private void stubApi() {
        lenient().when(pagoPaApiClientFactory.getOrCreateApi(COD_INTERMEDIARIO)).thenReturn(externalApisApi);
        lenient().when(pagoPaApiClientFactory.getBaseUrl(COD_INTERMEDIARIO)).thenReturn("https://api.pagopa.it");
        lenient().when(batchProperties.getPageSize()).thenReturn(1000);
    }

    private BrokerInstitutionResource createResource(String taxCode, String companyName) {
        BrokerInstitutionResource resource = new BrokerInstitutionResource();
        resource.setTaxCode(taxCode);
        resource.setCompanyName(companyName);
        resource.setStationId(taxCode + "001");
        resource.setAuxDigit("3");
        resource.setSegregationCode("01");
        resource.setCbillCode("AAAAAA");
        return resource;
    }

    private BrokerInstitutionsResponse createResponse(List<BrokerInstitutionResource> enti, int page, long totalPages) {
        BrokerInstitutionsResponse response = new BrokerInstitutionsResponse();
        response.setCreditorInstitutions(enti);
        PageInfo pageInfo = new PageInfo();
        pageInfo.setPage(page);
        pageInfo.setTotalPages(totalPages);
        pageInfo.setTotalElements((long) enti.size());
        pageInfo.setLimit(1000);
        response.setPageInfo(pageInfo);
        return response;
    }

    @Test
    void getAllEntiCreditori_whenFactoryThrowsIllegalState_shouldWrapInRestClientException() {
        when(pagoPaApiClientFactory.getOrCreateApi(COD_INTERMEDIARIO))
                .thenThrow(new IllegalStateException("Nessun intermediario trovato: " + COD_INTERMEDIARIO));

        // fetchEntiPage's generic catch(Exception) wraps any failure (including
        // connector-resolution errors from the factory) into a RestClientException.
        RestClientException thrown = assertThrows(RestClientException.class,
                () -> service.getAllEntiCreditori(COD_INTERMEDIARIO));
        assertTrue(thrown.getCause() instanceof IllegalStateException);
    }

    @Test
    void getAllEntiCreditori_singlePage_shouldReturnEntiAndCallGdeOk() throws Exception {
        stubApi();

        BrokerInstitutionResource ente1 = createResource("77777777777", "Comune di Alfa");
        BrokerInstitutionResource ente2 = createResource("88888888888", "Comune di Beta");
        BrokerInstitutionsResponse response = createResponse(List.of(ente1, ente2), 1, 1);

        when(externalApisApi.getBrokerInstitutionsWithHttpInfo(eq(COD_INTERMEDIARIO), eq(1), eq(1000), any()))
                .thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        List<EnteCreditorePagopa> result = service.getAllEntiCreditori(COD_INTERMEDIARIO);

        assertEquals(2, result.size());
        assertEquals("77777777777", result.get(0).getTaxCode());
        assertEquals("Comune di Alfa", result.get(0).getCompanyName());
        assertEquals("77777777777001", result.get(0).getStationId());
        assertEquals("3", result.get(0).getAuxDigit());
        assertEquals("01", result.get(0).getSegregationCode());
        assertEquals("AAAAAA", result.get(0).getCbillCode());
        assertEquals(COD_INTERMEDIARIO, result.get(0).getCodIntermediario());

        verify(gdeService).saveGetBrokerInstitutionsOk(eq(COD_INTERMEDIARIO), any(), any(), eq(2), any(), eq("https://api.pagopa.it"));
    }

    @Test
    void getAllEntiCreditori_multiPage_shouldPaginateAndReturnAll() throws Exception {
        stubApi();

        BrokerInstitutionsResponse page1 = createResponse(List.of(createResource("77777777777", "Comune di Alfa")), 1, 2);
        BrokerInstitutionsResponse page2 = createResponse(List.of(createResource("88888888888", "Comune di Beta")), 2, 2);

        when(externalApisApi.getBrokerInstitutionsWithHttpInfo(eq(COD_INTERMEDIARIO), eq(1), eq(1000), any()))
                .thenReturn(new ResponseEntity<>(page1, HttpStatus.OK));
        when(externalApisApi.getBrokerInstitutionsWithHttpInfo(eq(COD_INTERMEDIARIO), eq(2), eq(1000), any()))
                .thenReturn(new ResponseEntity<>(page2, HttpStatus.OK));

        List<EnteCreditorePagopa> result = service.getAllEntiCreditori(COD_INTERMEDIARIO);

        assertEquals(2, result.size());
        assertEquals("77777777777", result.get(0).getTaxCode());
        assertEquals("88888888888", result.get(1).getTaxCode());
    }

    @Test
    void getAllEntiCreditori_nullBody_shouldReturnEmptyList() throws Exception {
        stubApi();

        when(externalApisApi.getBrokerInstitutionsWithHttpInfo(eq(COD_INTERMEDIARIO), eq(1), eq(1000), any()))
                .thenReturn(new ResponseEntity<>((BrokerInstitutionsResponse) null, HttpStatus.OK));

        List<EnteCreditorePagopa> result = service.getAllEntiCreditori(COD_INTERMEDIARIO);

        assertTrue(result.isEmpty());
        verify(gdeService).saveGetBrokerInstitutionsOk(eq(COD_INTERMEDIARIO), any(), any(), eq(0), any(), eq("https://api.pagopa.it"));
    }

    @Test
    void getAllEntiCreditori_resourceAccessExceptionClosed_shouldReturnEmptyList() throws Exception {
        stubApi();

        when(externalApisApi.getBrokerInstitutionsWithHttpInfo(eq(COD_INTERMEDIARIO), eq(1), eq(1000), any()))
                .thenThrow(new ResourceAccessException("I/O error: connection closed"));

        List<EnteCreditorePagopa> result = service.getAllEntiCreditori(COD_INTERMEDIARIO);

        assertTrue(result.isEmpty());
        verify(gdeService).saveGetBrokerInstitutionsOk(eq(COD_INTERMEDIARIO), any(), any(), eq(0), any(), eq("https://api.pagopa.it"));
    }

    @Test
    void getAllEntiCreditori_resourceAccessExceptionGeneric_shouldThrowAndCallGdeKo() throws Exception {
        stubApi();

        when(externalApisApi.getBrokerInstitutionsWithHttpInfo(eq(COD_INTERMEDIARIO), eq(1), eq(1000), any()))
                .thenThrow(new ResourceAccessException("I/O error: connection timeout"));

        assertThrows(RestClientException.class, () -> service.getAllEntiCreditori(COD_INTERMEDIARIO));

        verify(gdeService).saveGetBrokerInstitutionsKo(eq(COD_INTERMEDIARIO), any(), any(), any(), any(), eq("https://api.pagopa.it"));
    }

    @Test
    void getAllEntiCreditori_genericException_shouldWrapInRestClientExceptionAndCallGdeKo() throws Exception {
        stubApi();

        when(externalApisApi.getBrokerInstitutionsWithHttpInfo(eq(COD_INTERMEDIARIO), eq(1), eq(1000), any()))
                .thenThrow(new RuntimeException("Unexpected error"));

        RestClientException thrown = assertThrows(RestClientException.class,
                () -> service.getAllEntiCreditori(COD_INTERMEDIARIO));

        assertTrue(thrown.getMessage().contains("Fallito il recupero degli enti creditori"));
        verify(gdeService).saveGetBrokerInstitutionsKo(eq(COD_INTERMEDIARIO), any(), any(), any(), any(), eq("https://api.pagopa.it"));
    }
}
