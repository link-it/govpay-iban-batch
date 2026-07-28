package it.govpay.iban.batch.tasklet;

import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.iban.batch.repository.EcCheckRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * Tasklet to clean up PAGOPA_EC_CHECK table before starting the batch process
 */
@Component
@Slf4j
public class CleanupEcCheckTasklet implements Tasklet {

    private final EcCheckRepository ecCheckRepository;

    public CleanupEcCheckTasklet(EcCheckRepository ecCheckRepository) {
        this.ecCheckRepository = ecCheckRepository;
    }

    @Override
    @Transactional
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        log.info("Starting cleanup of PAGOPA_EC_CHECK table");

        long count = ecCheckRepository.count();
        ecCheckRepository.deleteAllRecords();

        log.info("Deleted {} records from PAGOPA_EC_CHECK table", count);

        return RepeatStatus.FINISHED;
    }
}
