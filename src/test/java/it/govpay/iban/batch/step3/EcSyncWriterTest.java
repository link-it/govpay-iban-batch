package it.govpay.iban.batch.step3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.item.Chunk;

import it.govpay.iban.batch.dto.EnteCreditorePagopa;
import it.govpay.iban.batch.entity.EcCheckEntity;
import it.govpay.iban.batch.entity.EnteCreditoreCacheEntity;
import it.govpay.iban.batch.repository.EcCheckRepository;
import it.govpay.iban.batch.repository.EnteCreditoreCacheRepository;

@ExtendWith(MockitoExtension.class)
class EcSyncWriterTest {

    @Mock
    private EnteCreditoreCacheRepository repository;

    @Mock
    private EcCheckRepository ecCheckRepository;

    private static final ZoneId ZONE_ID = ZoneId.of("Europe/Rome");

    private EcSyncWriter writer;

    @BeforeEach
    void setUp() {
        writer = new EcSyncWriter(repository, ecCheckRepository, ZONE_ID);
    }

    private EnteCreditorePagopa createEnte(String checkStato, String checkMotivo) {
        return EnteCreditorePagopa.builder()
                .codIntermediario("12345678901")
                .taxCode("77777777777")
                .companyName("Comune di Alfa")
                .stationId("77777777777001")
                .auxDigit("3")
                .segregationCode("01")
                .cbillCode("AAAAAA")
                .checkStato(checkStato)
                .checkMotivo(checkMotivo)
                .build();
    }

    @Test
    void write_newEnte_shouldInsertCacheAndCheck() {
        when(repository.findByCodFiscale("77777777777")).thenReturn(Optional.empty());

        writer.write(new Chunk<>(createEnte("OK", null)));

        ArgumentCaptor<EnteCreditoreCacheEntity> cacheCaptor = ArgumentCaptor.forClass(EnteCreditoreCacheEntity.class);
        verify(repository).save(cacheCaptor.capture());
        EnteCreditoreCacheEntity saved = cacheCaptor.getValue();
        assertEquals("77777777777", saved.getCodFiscale());
        assertEquals("Comune di Alfa", saved.getDenominazione());
        assertEquals("77777777777001", saved.getStationId());
        assertEquals("3", saved.getAuxDigit());
        assertEquals("01", saved.getSegregationCode());
        assertEquals("AAAAAA", saved.getCbillCode());
        assertNotNull(saved.getDataUltimoAggiornamento());

        ArgumentCaptor<EcCheckEntity> tempCaptor = ArgumentCaptor.forClass(EcCheckEntity.class);
        verify(ecCheckRepository).save(tempCaptor.capture());
        EcCheckEntity temp = tempCaptor.getValue();
        assertEquals("12345678901", temp.getCodIntermediario());
        assertEquals("77777777777", temp.getTaxCode());
        assertEquals("OK", temp.getCheckStato());
    }

    @Test
    void write_existingEnte_shouldUpdateCache() {
        EnteCreditoreCacheEntity existing = new EnteCreditoreCacheEntity();
        existing.setId(1L);
        existing.setCodFiscale("77777777777");
        when(repository.findByCodFiscale("77777777777")).thenReturn(Optional.of(existing));

        writer.write(new Chunk<>(createEnte("INFO_DIVERSE", "Presenza di differenze: RagioneSociale")));

        ArgumentCaptor<EnteCreditoreCacheEntity> cacheCaptor = ArgumentCaptor.forClass(EnteCreditoreCacheEntity.class);
        verify(repository).save(cacheCaptor.capture());
        assertEquals(1L, cacheCaptor.getValue().getId());
        assertEquals("Comune di Alfa", cacheCaptor.getValue().getDenominazione());

        ArgumentCaptor<EcCheckEntity> tempCaptor = ArgumentCaptor.forClass(EcCheckEntity.class);
        verify(ecCheckRepository).save(tempCaptor.capture());
        assertEquals("INFO_DIVERSE", tempCaptor.getValue().getCheckStato());
        assertEquals("Presenza di differenze: RagioneSociale", tempCaptor.getValue().getCheckMotivo());
    }

    @Test
    void write_multipleItems_shouldProcessEach() {
        when(repository.findByCodFiscale(any())).thenReturn(Optional.empty());

        EnteCreditorePagopa ente1 = createEnte("OK", null);
        EnteCreditorePagopa ente2 = createEnte("NON_CENSITO", null);
        ente2.setTaxCode("88888888888");

        writer.write(new Chunk<>(java.util.List.of(ente1, ente2)));

        verify(repository, org.mockito.Mockito.times(2)).save(any());
        verify(ecCheckRepository, org.mockito.Mockito.times(2)).save(any());
    }
}
