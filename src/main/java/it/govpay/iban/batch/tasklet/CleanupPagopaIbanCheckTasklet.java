package it.govpay.iban.batch.tasklet;

import it.govpay.iban.batch.repository.PagopaIbanCheckRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tasklet to clean up PAGOPA_IBAN_CHECK table before starting the batch process
 */
@Component
@Slf4j
public class CleanupPagopaIbanCheckTasklet implements Tasklet {

    private final PagopaIbanCheckRepository ibanCheckRepository;

    public CleanupPagopaIbanCheckTasklet(PagopaIbanCheckRepository ibanCheckRepository) {
        this.ibanCheckRepository = ibanCheckRepository;
    }

    @Override
    @Transactional
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        log.info("Starting cleanup of PAGOPA_IBAN_CHECK table");

        long count = ibanCheckRepository.count();
        ibanCheckRepository.deleteAllRecords();

        log.info("Deleted {} records from PAGOPA_IBAN_CHECK table", count);

        return RepeatStatus.FINISHED;
    }
}
