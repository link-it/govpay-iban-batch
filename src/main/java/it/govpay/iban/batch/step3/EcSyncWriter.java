package it.govpay.iban.batch.step3;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.iban.batch.dto.EnteCreditorePagopa;
import it.govpay.iban.batch.entity.EcCheckEntity;
import it.govpay.iban.batch.entity.EnteCreditoreCacheEntity;
import it.govpay.iban.batch.repository.EcCheckRepository;
import it.govpay.iban.batch.repository.EnteCreditoreCacheRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * Writer that upserts Enti Creditori into pagopa_ec_cache, keyed by cod_fiscale,
 * and appends the check result (computed by {@link EcCheckProcessor}) into
 * pagopa_ec_check. Every pagopa_ec_cache write stamps
 * data_ultimo_aggiornamento = now(), which is what
 * CleanupStaleEntiCreditoriTasklet later uses to purge rows no longer returned
 * by pagoPA for their broker.
 */
@Component
@Slf4j
public class EcSyncWriter implements ItemWriter<EnteCreditorePagopa> {

    private final EnteCreditoreCacheRepository repository;
    private final EcCheckRepository ecCheckRepository;
    private final ZoneId applicationZoneId;

    public EcSyncWriter(EnteCreditoreCacheRepository repository,
                        EcCheckRepository ecCheckRepository,
                        ZoneId applicationZoneId) {
        this.repository = repository;
        this.ecCheckRepository = ecCheckRepository;
        this.applicationZoneId = applicationZoneId;
    }

    @Override
    @Transactional
    public void write(Chunk<? extends EnteCreditorePagopa> chunk) {
        for (EnteCreditorePagopa ente : chunk) {
            log.debug("Upsert ente creditore {} (intermediario {})", ente.getTaxCode(), ente.getCodIntermediario());

            EnteCreditoreCacheEntity entity = repository.findByCodFiscale(ente.getTaxCode())
                    .orElseGet(EnteCreditoreCacheEntity::new);
            entity.setCodFiscale(ente.getTaxCode());
            entity.setDenominazione(ente.getCompanyName());
            entity.setStationId(ente.getStationId());
            entity.setAuxDigit(ente.getAuxDigit());
            entity.setSegregationCode(ente.getSegregationCode());
            entity.setCbillCode(ente.getCbillCode());
            entity.setDataUltimoAggiornamento(OffsetDateTime.now(applicationZoneId));

            repository.save(entity);

            EcCheckEntity checkEntity = EcCheckEntity.builder()
                    .codIntermediario(ente.getCodIntermediario())
                    .taxCode(ente.getTaxCode())
                    .companyName(ente.getCompanyName())
                    .stationId(ente.getStationId())
                    .auxDigit(ente.getAuxDigit())
                    .segregationCode(ente.getSegregationCode())
                    .cbillCode(ente.getCbillCode())
                    .checkStato(ente.getCheckStato())
                    .checkMotivo(ente.getCheckMotivo())
                    .build();
            ecCheckRepository.save(checkEntity);
        }
    }
}
