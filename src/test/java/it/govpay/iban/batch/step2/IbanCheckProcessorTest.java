package it.govpay.iban.batch.step2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.govpay.iban.batch.Costanti;
import it.govpay.iban.batch.dto.IbanPagopa;
import it.govpay.iban.batch.entity.IbanAccreditoEntity;
import it.govpay.iban.batch.repository.IbanAccreditoRepository;

@ExtendWith(MockitoExtension.class)
class IbanCheckProcessorTest {

    @Mock
    private IbanAccreditoRepository ibanAccreditoRepository;

    private IbanCheckProcessor processor;

    private static final String IBAN = "IT60X0542811101000000123456";
    private static final String FISCAL_CODE = "01234567890";
    private static final String INTERMEDIARIO = "12345678901";
    private static final ZoneId ZONE_ID = ZoneId.of("Europe/Rome");

    @BeforeEach
    void setUp() {
        processor = new IbanCheckProcessor(ibanAccreditoRepository, ZONE_ID);
    }

    private IbanPagopa createIbanPagopa(String status, OffsetDateTime validityDate, String description, String name) {
        return IbanPagopa.builder()
                .codIntermediario(INTERMEDIARIO)
                .iban(IBAN)
                .fiscalCode(FISCAL_CODE)
                .status(status)
                .validityDate(validityDate)
                .description(description)
                .name(name)
                .build();
    }

    private IbanAccreditoEntity createStoredIban(Boolean abilitato, String descrizione, String intestatario) {
        return IbanAccreditoEntity.builder()
                .codIban(IBAN)
                .abilitato(abilitato)
                .descrizione(descrizione)
                .intestatario(intestatario)
                .build();
    }

    @Test
    void process_ibanNotFoundInDb_shouldSetCheckNonCensito() throws Exception {
        IbanPagopa iban = createIbanPagopa("ENABLED", null, "desc", "name");
        when(ibanAccreditoRepository.findByCodIbanAndDominioCodDominio(IBAN, FISCAL_CODE))
                .thenReturn(Collections.emptyList());

        IbanPagopa result = processor.process(iban);

        assertEquals(Costanti.CHECK_NON_CENSITO, result.getCheckStato());
    }

    @Test
    void process_ibanNotFoundInDb_nullList_shouldSetCheckNonCensito() throws Exception {
        IbanPagopa iban = createIbanPagopa("ENABLED", null, "desc", "name");
        when(ibanAccreditoRepository.findByCodIbanAndDominioCodDominio(IBAN, FISCAL_CODE))
                .thenReturn(null);

        IbanPagopa result = processor.process(iban);

        assertEquals(Costanti.CHECK_NON_CENSITO, result.getCheckStato());
    }

    @Test
    void process_ibanFoundAndAllMatch_shouldSetCheckOk() throws Exception {
        IbanPagopa iban = createIbanPagopa("ENABLED", null, "desc", "name");
        IbanAccreditoEntity stored = createStoredIban(true, "desc", "name");
        when(ibanAccreditoRepository.findByCodIbanAndDominioCodDominio(IBAN, FISCAL_CODE))
                .thenReturn(List.of(stored));

        IbanPagopa result = processor.process(iban);

        assertEquals(Costanti.CHECK_OK, result.getCheckStato());
    }

    @Test
    void process_differentAbilitazione_shouldSetCheckInfoDiverse() throws Exception {
        IbanPagopa iban = createIbanPagopa("ENABLED", null, "desc", "name");
        IbanAccreditoEntity stored = createStoredIban(false, "desc", "name");
        when(ibanAccreditoRepository.findByCodIbanAndDominioCodDominio(IBAN, FISCAL_CODE))
                .thenReturn(List.of(stored));

        IbanPagopa result = processor.process(iban);

        assertEquals(Costanti.CHECK_INFO_DIVERSE, result.getCheckStato());
        assertTrue(result.getCheckMotivo().contains("Abilitazione"));
    }

    @Test
    void process_bothDisabled_shouldSetCheckNonAttivo() throws Exception {
        IbanPagopa iban = createIbanPagopa("DISABLED", null, "desc", "name");
        IbanAccreditoEntity stored = createStoredIban(false, "desc", "name");
        when(ibanAccreditoRepository.findByCodIbanAndDominioCodDominio(IBAN, FISCAL_CODE))
                .thenReturn(List.of(stored));

        IbanPagopa result = processor.process(iban);

        assertEquals(Costanti.CHECK_NON_ATTIVO, result.getCheckStato());
    }

    @Test
    void process_differentDescrizione_shouldSetCheckInfoDiverse() throws Exception {
        IbanPagopa iban = createIbanPagopa("ENABLED", null, "descPagopa", "name");
        IbanAccreditoEntity stored = createStoredIban(true, "descGovpay", "name");
        when(ibanAccreditoRepository.findByCodIbanAndDominioCodDominio(IBAN, FISCAL_CODE))
                .thenReturn(List.of(stored));

        IbanPagopa result = processor.process(iban);

        assertEquals(Costanti.CHECK_INFO_DIVERSE, result.getCheckStato());
        assertTrue(result.getCheckMotivo().contains("Descrizione"));
    }

    @Test
    void process_differentIntestatario_shouldSetCheckInfoDiverse() throws Exception {
        IbanPagopa iban = createIbanPagopa("ENABLED", null, "desc", "namePagopa");
        IbanAccreditoEntity stored = createStoredIban(true, "desc", "nameGovpay");
        when(ibanAccreditoRepository.findByCodIbanAndDominioCodDominio(IBAN, FISCAL_CODE))
                .thenReturn(List.of(stored));

        IbanPagopa result = processor.process(iban);

        assertEquals(Costanti.CHECK_INFO_DIVERSE, result.getCheckStato());
        assertTrue(result.getCheckMotivo().contains("Intestatario"));
    }

    @Test
    void process_multipleDifferences_shouldListAllInMotivo() throws Exception {
        IbanPagopa iban = createIbanPagopa("DISABLED", null, "descPagopa", "namePagopa");
        IbanAccreditoEntity stored = createStoredIban(true, "descGovpay", "nameGovpay");
        when(ibanAccreditoRepository.findByCodIbanAndDominioCodDominio(IBAN, FISCAL_CODE))
                .thenReturn(List.of(stored));

        IbanPagopa result = processor.process(iban);

        assertEquals(Costanti.CHECK_INFO_DIVERSE, result.getCheckStato());
        assertTrue(result.getCheckMotivo().contains("Abilitazione"));
        assertTrue(result.getCheckMotivo().contains("Descrizione"));
        assertTrue(result.getCheckMotivo().contains("Intestatario"));
    }

    @Test
    void process_validityDateInFuture_ibanConsideredActive() throws Exception {
        OffsetDateTime futureDate = OffsetDateTime.now().plusDays(30);
        IbanPagopa iban = createIbanPagopa("ENABLED", futureDate, "desc", "name");
        IbanAccreditoEntity stored = createStoredIban(true, "desc", "name");
        when(ibanAccreditoRepository.findByCodIbanAndDominioCodDominio(IBAN, FISCAL_CODE))
                .thenReturn(List.of(stored));

        IbanPagopa result = processor.process(iban);

        // validityDate in future + ENABLED => isAfter(now) is true => ibanEnable = false (because of the !isAfter condition)
        // stored is true, iban is false => different abilitazione
        assertEquals(Costanti.CHECK_INFO_DIVERSE, result.getCheckStato());
        assertTrue(result.getCheckMotivo().contains("Abilitazione"));
    }

    @Test
    void process_validityDateInPast_ibanConsideredActive() throws Exception {
        OffsetDateTime pastDate = OffsetDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        IbanPagopa iban = createIbanPagopa("ENABLED", pastDate, "desc", "name");
        IbanAccreditoEntity stored = createStoredIban(true, "desc", "name");
        when(ibanAccreditoRepository.findByCodIbanAndDominioCodDominio(IBAN, FISCAL_CODE))
                .thenReturn(List.of(stored));

        IbanPagopa result = processor.process(iban);

        // validityDate in past + ENABLED => isAfter(now) is false => ibanEnable = ENABLED && !false = true
        // stored is true => same => CHECK_OK
        assertEquals(Costanti.CHECK_OK, result.getCheckStato());
    }

    // ============ Valori di status restituiti da pagoPA ============

    /** pagoPA restituisce ATTIVO, non ENABLED: un IBAN attivo su entrambi i lati e' OK. */
    @Test
    void process_statusAttivo_andEnabledOnGovpay_shouldSetCheckOk() throws Exception {
        IbanPagopa iban = createIbanPagopa("ATTIVO", OffsetDateTime.now().minusDays(30), "desc", "name");
        when(ibanAccreditoRepository.findByCodIbanAndDominioCodDominio(IBAN, FISCAL_CODE))
                .thenReturn(List.of(createStoredIban(true, "desc", "name")));

        IbanPagopa result = processor.process(iban);

        assertEquals(Costanti.CHECK_OK, result.getCheckStato());
        assertNull(result.getCheckMotivo());
    }

    @Test
    void process_statusDisattivo_andDisabledOnGovpay_shouldSetCheckNonAttivo() throws Exception {
        IbanPagopa iban = createIbanPagopa("DISATTIVO", null, "desc", "name");
        when(ibanAccreditoRepository.findByCodIbanAndDominioCodDominio(IBAN, FISCAL_CODE))
                .thenReturn(List.of(createStoredIban(false, "desc", "name")));

        IbanPagopa result = processor.process(iban);

        assertEquals(Costanti.CHECK_NON_ATTIVO, result.getCheckStato());
    }

    @Test
    void process_statusSconosciuto_shouldBeTreatedAsNonAttivoAndReported() throws Exception {
        IbanPagopa iban = createIbanPagopa("SOSPESO", null, "desc", "name");
        when(ibanAccreditoRepository.findByCodIbanAndDominioCodDominio(IBAN, FISCAL_CODE))
                .thenReturn(List.of(createStoredIban(true, "desc", "name")));

        IbanPagopa result = processor.process(iban);

        assertEquals(Costanti.CHECK_INFO_DIVERSE, result.getCheckStato());
        assertEquals("Presenza di differenze: Abilitazione; Stato pagoPA non riconosciuto: SOSPESO", result.getCheckMotivo());
    }

    @Test
    void process_statusSconosciuto_ibanNonCensito_shouldStillBeReported() throws Exception {
        IbanPagopa iban = createIbanPagopa("SOSPESO", null, "desc", "name");
        when(ibanAccreditoRepository.findByCodIbanAndDominioCodDominio(IBAN, FISCAL_CODE)).thenReturn(List.of());

        IbanPagopa result = processor.process(iban);

        assertEquals(Costanti.CHECK_NON_CENSITO, result.getCheckStato());
        assertEquals("Stato pagoPA non riconosciuto: SOSPESO", result.getCheckMotivo());
    }
}
