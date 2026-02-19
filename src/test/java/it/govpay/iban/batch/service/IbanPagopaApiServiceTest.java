package it.govpay.iban.batch.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import it.govpay.common.client.service.ConnettoreService;
import it.govpay.common.entity.IntermediarioEntity;
import it.govpay.common.repository.IntermediarioRepository;
import it.govpay.iban.batch.config.BatchProperties;
import it.govpay.iban.batch.config.IbanPagopaApiClientConfig;
import it.govpay.iban.batch.gde.service.GdeService;

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
