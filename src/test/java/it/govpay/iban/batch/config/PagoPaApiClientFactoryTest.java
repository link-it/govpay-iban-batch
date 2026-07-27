package it.govpay.iban.batch.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import tools.jackson.databind.json.JsonMapper;

import it.govpay.common.client.model.Connettore;
import it.govpay.common.client.service.ConnettoreService;
import it.govpay.common.entity.IntermediarioEntity;
import it.govpay.common.repository.IntermediarioRepository;
import it.govpay.pagopa.backoffice.client.api.ExternalApisApi;

@ExtendWith(MockitoExtension.class)
class PagoPaApiClientFactoryTest {

    @Mock
    private IntermediarioRepository intermediarioRepository;

    @Mock
    private ConnettoreService connettoreService;

    @Mock
    private IbanPagopaApiClientConfig ibanPagopaApiClientConfig;

    private PagoPaApiClientFactory factory;

    private static final String COD_INTERMEDIARIO = "12345678901";
    private static final String COD_CONNETTORE = "CONN_BACKOFFICE_EC";

    @BeforeEach
    void setUp() {
        factory = new PagoPaApiClientFactory(intermediarioRepository, connettoreService, ibanPagopaApiClientConfig);
    }

    private IntermediarioEntity createIntermediario(String codConnettore) {
        return IntermediarioEntity.builder()
                .codIntermediario(COD_INTERMEDIARIO)
                .codConnettoreBackofficeEc(codConnettore)
                .abilitato(true)
                .build();
    }

    @Test
    void getOrCreateApi_withNoIntermediario_shouldThrow() {
        when(intermediarioRepository.findByCodIntermediario(COD_INTERMEDIARIO))
                .thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> factory.getOrCreateApi(COD_INTERMEDIARIO));
    }

    @Test
    void getOrCreateApi_withNullConnettoreCode_shouldThrow() {
        when(intermediarioRepository.findByCodIntermediario(COD_INTERMEDIARIO))
                .thenReturn(Optional.of(createIntermediario(null)));

        assertThrows(IllegalStateException.class, () -> factory.getOrCreateApi(COD_INTERMEDIARIO));
    }

    @Test
    void getOrCreateApi_withBlankConnettoreCode_shouldThrow() {
        when(intermediarioRepository.findByCodIntermediario(COD_INTERMEDIARIO))
                .thenReturn(Optional.of(createIntermediario("   ")));

        assertThrows(IllegalStateException.class, () -> factory.getOrCreateApi(COD_INTERMEDIARIO));
    }

    @Test
    void getOrCreateApi_calledTwice_shouldReuseCachedInstance() {
        when(intermediarioRepository.findByCodIntermediario(COD_INTERMEDIARIO))
                .thenReturn(Optional.of(createIntermediario(COD_CONNETTORE)));

        RestTemplate restTemplate = new RestTemplate();
        when(connettoreService.getRestTemplate(COD_CONNETTORE)).thenReturn(restTemplate);
        when(ibanPagopaApiClientConfig.createPagoPAObjectMapper()).thenReturn(mock(JsonMapper.class));

        Connettore connettore = new Connettore();
        connettore.setUrl("https://api.pagopa.it");
        when(connettoreService.getConnettore(COD_CONNETTORE)).thenReturn(connettore);

        ExternalApisApi first = factory.getOrCreateApi(COD_INTERMEDIARIO);
        ExternalApisApi second = factory.getOrCreateApi(COD_INTERMEDIARIO);

        assertSame(first, second);
    }

    @Test
    void getBaseUrl_shouldResolveViaConnettoreService() {
        when(intermediarioRepository.findByCodIntermediario(COD_INTERMEDIARIO))
                .thenReturn(Optional.of(createIntermediario(COD_CONNETTORE)));

        Connettore connettore = new Connettore();
        connettore.setUrl("https://api.pagopa.it");
        when(connettoreService.getConnettore(COD_CONNETTORE)).thenReturn(connettore);

        assertEquals("https://api.pagopa.it", factory.getBaseUrl(COD_INTERMEDIARIO));
    }

    @Test
    void clearApiCache_thenSubsequentCallRecreatesInstance() {
        when(intermediarioRepository.findByCodIntermediario(COD_INTERMEDIARIO))
                .thenReturn(Optional.of(createIntermediario(COD_CONNETTORE)));

        RestTemplate restTemplate = new RestTemplate();
        when(connettoreService.getRestTemplate(COD_CONNETTORE)).thenReturn(restTemplate);
        when(ibanPagopaApiClientConfig.createPagoPAObjectMapper()).thenReturn(mock(JsonMapper.class));

        Connettore connettore = new Connettore();
        connettore.setUrl("https://api.pagopa.it");
        when(connettoreService.getConnettore(COD_CONNETTORE)).thenReturn(connettore);

        ExternalApisApi first = factory.getOrCreateApi(COD_INTERMEDIARIO);
        factory.clearApiCache();
        ExternalApisApi second = factory.getOrCreateApi(COD_INTERMEDIARIO);

        assertEquals(first.getClass(), second.getClass());
        org.junit.jupiter.api.Assertions.assertNotSame(first, second);
    }
}
