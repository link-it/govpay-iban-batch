package it.govpay.iban.batch.tasklet;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.iban.batch.repository.IbanCacheRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * Tasklet to remove stale rows from pagopa_iban_cache after a successful run:
 * any row whose data_ultima_verifica predates the current job's start time was
 * not touched by this run (IBAN no longer returned by pagoPA for its dominio).
 * Runs only if the check IBAN step completed successfully (linear job flow
 * halts on failure), so the cache is left stale rather than emptied on a
 * pagoPA outage.
 */
@Component
@Slf4j
public class CleanupStaleIbanCacheTasklet implements Tasklet {

    private final IbanCacheRepository repository;
    private final ZoneId applicationZoneId;

    public CleanupStaleIbanCacheTasklet(IbanCacheRepository repository, ZoneId applicationZoneId) {
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

        log.info("Pulizia righe stale da pagopa_iban_cache antecedenti a {}", jobStartTime);

        long deleted = repository.deleteByDataUltimaVerificaBefore(jobStartTime);

        log.info("Cancellate {} righe stale da pagopa_iban_cache", deleted);

        return RepeatStatus.FINISHED;
    }
}
