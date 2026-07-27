package it.govpay.iban.batch.tasklet;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.iban.batch.repository.EnteCreditoreCacheRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * Tasklet to remove stale rows from pagopa_ec_cache after a successful sync:
 * any row whose data_ultimo_aggiornamento predates the current job's start time
 * was not touched by this run (Ente Creditore no longer returned by pagoPA for
 * its broker). Runs only if the sync step completed successfully, since a
 * failed/partitioned step halts the linear job flow before reaching this step -
 * the cache is left stale rather than emptied on a pagoPA outage.
 */
@Component
@Slf4j
public class CleanupStaleEntiCreditoriTasklet implements Tasklet {

    private final EnteCreditoreCacheRepository repository;
    private final ZoneId applicationZoneId;

    public CleanupStaleEntiCreditoriTasklet(EnteCreditoreCacheRepository repository, ZoneId applicationZoneId) {
        this.repository = repository;
        this.applicationZoneId = applicationZoneId;
    }

    @Override
    @Transactional
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        OffsetDateTime jobStartTime = chunkContext.getStepContext().getStepExecution()
                .getJobExecution().getStartTime()
                .atZone(applicationZoneId)
                .toOffsetDateTime();

        log.info("Pulizia righe stale da pagopa_ec_cache antecedenti a {}", jobStartTime);

        long deleted = repository.deleteByDataUltimoAggiornamentoBefore(jobStartTime);

        log.info("Cancellate {} righe stale da pagopa_ec_cache", deleted);

        return RepeatStatus.FINISHED;
    }
}
