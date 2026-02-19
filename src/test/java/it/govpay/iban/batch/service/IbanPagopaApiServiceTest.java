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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

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
}
