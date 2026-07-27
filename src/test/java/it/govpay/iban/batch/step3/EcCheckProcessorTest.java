package it.govpay.iban.batch.step3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.govpay.common.entity.DominioEntity;
import it.govpay.common.entity.StazioneEntity;
import it.govpay.common.repository.DominioRepository;
import it.govpay.iban.batch.Costanti;
import it.govpay.iban.batch.dto.EnteCreditorePagopa;

@ExtendWith(MockitoExtension.class)
class EcCheckProcessorTest {

    @Mock
    private DominioRepository dominioRepository;

    private EcCheckProcessor processor;

    private static final String TAX_CODE = "77777777777";
    private static final String INTERMEDIARIO = "12345678901";

    @BeforeEach
    void setUp() {
        processor = new EcCheckProcessor(dominioRepository);
    }

    private EnteCreditorePagopa createEnte(String companyName, String auxDigit, String segregationCode,
                                          String cbillCode, String stationId) {
        return EnteCreditorePagopa.builder()
                .codIntermediario(INTERMEDIARIO)
                .taxCode(TAX_CODE)
                .companyName(companyName)
                .auxDigit(auxDigit)
                .segregationCode(segregationCode)
                .cbillCode(cbillCode)
                .stationId(stationId)
                .build();
    }

    private DominioEntity createStoredDominio(String ragioneSociale, Integer auxDigit, Integer segregationCode,
                                              String cbill, String codStazione) {
        StazioneEntity stazione = null;
        if (codStazione != null) {
            stazione = StazioneEntity.builder().codStazione(codStazione).build();
        }
        return DominioEntity.builder()
                .codDominio(TAX_CODE)
                .ragioneSociale(ragioneSociale)
                .auxDigit(auxDigit)
                .segregationCode(segregationCode)
                .cbill(cbill)
                .stazione(stazione)
                .build();
    }

    @Test
    void process_dominioNotFound_shouldSetCheckNonCensito() throws Exception {
        EnteCreditorePagopa ente = createEnte("Comune di Alfa", "3", "01", "AAAAAA", "STZ001");
        when(dominioRepository.findByCodDominio(TAX_CODE)).thenReturn(Optional.empty());

        EnteCreditorePagopa result = processor.process(ente);

        assertEquals(Costanti.CHECK_NON_CENSITO, result.getCheckStato());
    }

    @Test
    void process_dominioFoundAndAllMatch_shouldSetCheckOk() throws Exception {
        EnteCreditorePagopa ente = createEnte("Comune di Alfa", "3", "01", "AAAAAA", "STZ001");
        DominioEntity stored = createStoredDominio("Comune di Alfa", 3, 1, "AAAAAA", "STZ001");
        when(dominioRepository.findByCodDominio(TAX_CODE)).thenReturn(Optional.of(stored));

        EnteCreditorePagopa result = processor.process(ente);

        assertEquals(Costanti.CHECK_OK, result.getCheckStato());
    }

    @Test
    void process_differentRagioneSociale_shouldSetCheckInfoDiverse() throws Exception {
        EnteCreditorePagopa ente = createEnte("Comune di Alfa Nuovo", "3", "01", "AAAAAA", "STZ001");
        DominioEntity stored = createStoredDominio("Comune di Alfa", 3, 1, "AAAAAA", "STZ001");
        when(dominioRepository.findByCodDominio(TAX_CODE)).thenReturn(Optional.of(stored));

        EnteCreditorePagopa result = processor.process(ente);

        assertEquals(Costanti.CHECK_INFO_DIVERSE, result.getCheckStato());
        assertTrue(result.getCheckMotivo().contains("RagioneSociale"));
    }

    @Test
    void process_differentAuxDigit_shouldSetCheckInfoDiverse() throws Exception {
        EnteCreditorePagopa ente = createEnte("Comune di Alfa", "9", "01", "AAAAAA", "STZ001");
        DominioEntity stored = createStoredDominio("Comune di Alfa", 3, 1, "AAAAAA", "STZ001");
        when(dominioRepository.findByCodDominio(TAX_CODE)).thenReturn(Optional.of(stored));

        EnteCreditorePagopa result = processor.process(ente);

        assertEquals(Costanti.CHECK_INFO_DIVERSE, result.getCheckStato());
        assertTrue(result.getCheckMotivo().contains("AuxDigit"));
    }

    @Test
    void process_differentSegregationCode_shouldSetCheckInfoDiverse() throws Exception {
        EnteCreditorePagopa ente = createEnte("Comune di Alfa", "3", "02", "AAAAAA", "STZ001");
        DominioEntity stored = createStoredDominio("Comune di Alfa", 3, 1, "AAAAAA", "STZ001");
        when(dominioRepository.findByCodDominio(TAX_CODE)).thenReturn(Optional.of(stored));

        EnteCreditorePagopa result = processor.process(ente);

        assertEquals(Costanti.CHECK_INFO_DIVERSE, result.getCheckStato());
        assertTrue(result.getCheckMotivo().contains("SegregationCode"));
    }

    @Test
    void process_segregationCodeZeroPadded_shouldBeConsideredEqual() throws Exception {
        // pagoPA restituisce "01" come stringa; GovPay lo salva come Integer 1: sono lo stesso valore.
        EnteCreditorePagopa ente = createEnte("Comune di Alfa", "3", "01", "AAAAAA", "STZ001");
        DominioEntity stored = createStoredDominio("Comune di Alfa", 3, 1, "AAAAAA", "STZ001");
        when(dominioRepository.findByCodDominio(TAX_CODE)).thenReturn(Optional.of(stored));

        EnteCreditorePagopa result = processor.process(ente);

        assertEquals(Costanti.CHECK_OK, result.getCheckStato());
    }

    @Test
    void process_bothSegregationCodeNull_shouldSetCheckOk() throws Exception {
        EnteCreditorePagopa ente = createEnte("Comune di Alfa", "3", null, "AAAAAA", "STZ001");
        DominioEntity stored = createStoredDominio("Comune di Alfa", 3, null, "AAAAAA", "STZ001");
        when(dominioRepository.findByCodDominio(TAX_CODE)).thenReturn(Optional.of(stored));

        EnteCreditorePagopa result = processor.process(ente);

        assertEquals(Costanti.CHECK_OK, result.getCheckStato());
    }

    @Test
    void process_differentCbill_shouldSetCheckInfoDiverse() throws Exception {
        EnteCreditorePagopa ente = createEnte("Comune di Alfa", "3", "01", "BBBBBB", "STZ001");
        DominioEntity stored = createStoredDominio("Comune di Alfa", 3, 1, "AAAAAA", "STZ001");
        when(dominioRepository.findByCodDominio(TAX_CODE)).thenReturn(Optional.of(stored));

        EnteCreditorePagopa result = processor.process(ente);

        assertEquals(Costanti.CHECK_INFO_DIVERSE, result.getCheckStato());
        assertTrue(result.getCheckMotivo().contains("Cbill"));
    }

    @Test
    void process_differentStationId_shouldSetCheckInfoDiverse() throws Exception {
        EnteCreditorePagopa ente = createEnte("Comune di Alfa", "3", "01", "AAAAAA", "STZ999");
        DominioEntity stored = createStoredDominio("Comune di Alfa", 3, 1, "AAAAAA", "STZ001");
        when(dominioRepository.findByCodDominio(TAX_CODE)).thenReturn(Optional.of(stored));

        EnteCreditorePagopa result = processor.process(ente);

        assertEquals(Costanti.CHECK_INFO_DIVERSE, result.getCheckStato());
        assertTrue(result.getCheckMotivo().contains("StationId"));
    }

    @Test
    void process_dominioWithoutStazione_pagopaHasStationId_shouldSetCheckInfoDiverse() throws Exception {
        EnteCreditorePagopa ente = createEnte("Comune di Alfa", "3", "01", "AAAAAA", "STZ001");
        DominioEntity stored = createStoredDominio("Comune di Alfa", 3, 1, "AAAAAA", null);
        when(dominioRepository.findByCodDominio(TAX_CODE)).thenReturn(Optional.of(stored));

        EnteCreditorePagopa result = processor.process(ente);

        assertEquals(Costanti.CHECK_INFO_DIVERSE, result.getCheckStato());
        assertTrue(result.getCheckMotivo().contains("StationId"));
    }

    @Test
    void process_multipleDifferences_shouldListAllInMotivo() throws Exception {
        EnteCreditorePagopa ente = createEnte("Comune Nuovo", "9", "02", "BBBBBB", "STZ999");
        DominioEntity stored = createStoredDominio("Comune di Alfa", 3, 1, "AAAAAA", "STZ001");
        when(dominioRepository.findByCodDominio(TAX_CODE)).thenReturn(Optional.of(stored));

        EnteCreditorePagopa result = processor.process(ente);

        assertEquals(Costanti.CHECK_INFO_DIVERSE, result.getCheckStato());
        assertTrue(result.getCheckMotivo().contains("RagioneSociale"));
        assertTrue(result.getCheckMotivo().contains("AuxDigit"));
        assertTrue(result.getCheckMotivo().contains("SegregationCode"));
        assertTrue(result.getCheckMotivo().contains("Cbill"));
        assertTrue(result.getCheckMotivo().contains("StationId"));
    }
}
