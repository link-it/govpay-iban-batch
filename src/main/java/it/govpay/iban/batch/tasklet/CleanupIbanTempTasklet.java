package it.govpay.iban.batch.tasklet;

import it.govpay.iban.batch.repository.IbanPagopaTempRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tasklet to clean up IBAN_PAGOPA_TEMP table before starting the batch process
 */
@Component
@Slf4j
public class CleanupIbanTempTasklet implements Tasklet {

    private final IbanPagopaTempRepository ibanTempRepository;

    public CleanupIbanTempTasklet(IbanPagopaTempRepository ibanTempRepository) {
        this.ibanTempRepository = ibanTempRepository;
    }

    @Override
    @Transactional
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        log.info("Starting cleanup of IBAN_PAGOPA_TEMP table");

        long count = ibanTempRepository.count();
        ibanTempRepository.deleteAllRecords();

        log.info("Deleted {} records from IBAN_PAGOPA_TEMP table", count);

        return RepeatStatus.FINISHED;
    }
}
