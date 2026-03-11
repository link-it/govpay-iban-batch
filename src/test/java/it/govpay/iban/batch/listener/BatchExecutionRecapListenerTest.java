package it.govpay.iban.batch.listener;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;

import it.govpay.iban.batch.config.FileStorageConfig;
import it.govpay.iban.batch.mail.MailService;

class BatchExecutionRecapListenerTest {

    private BatchExecutionRecapListener listener;

    @BeforeEach
    void setUp() {
        FileStorageConfig fileStorageConfig = mock(FileStorageConfig.class);
        when(fileStorageConfig.isInviaMail()).thenReturn(false);
        when(fileStorageConfig.getReportDirectory()).thenReturn(Path.of("/tmp"));
        when(fileStorageConfig.getDestinatario()).thenReturn(Collections.emptyList());
        MailService mailService = mock(MailService.class);
        listener = new BatchExecutionRecapListener(fileStorageConfig, mailService);
    }

    private JobExecution createJobExecution() {
        JobInstance jobInstance = new JobInstance(1L, "ibanCheckJob");
        JobExecution execution = new JobExecution(jobInstance, 1L, new JobParameters());
        execution.setStatus(BatchStatus.COMPLETED);
        execution.setStartTime(LocalDateTime.now().minusMinutes(5));
        execution.setEndTime(LocalDateTime.now());
        execution.setExitStatus(ExitStatus.COMPLETED);
        return execution;
    }

    private StepExecution createStepExecution(JobExecution jobExecution, String stepName) {
        StepExecution stepExecution = new StepExecution(stepName, jobExecution);
        stepExecution.setStatus(BatchStatus.COMPLETED);
        stepExecution.setStartTime(LocalDateTime.now().minusMinutes(1));
        stepExecution.setEndTime(LocalDateTime.now());
        return stepExecution;
    }

    @Test
    void beforeJob_shouldNotThrow() {
        JobExecution jobExecution = createJobExecution();

        assertDoesNotThrow(() -> listener.beforeJob(jobExecution));
    }

    @Test
    void afterJob_withNoSteps_shouldNotThrow() {
        JobExecution jobExecution = createJobExecution();

        assertDoesNotThrow(() -> listener.afterJob(jobExecution));
    }

    @Test
    void afterJob_withCleanupStep_shouldPrintStats() {
        JobExecution jobExecution = createJobExecution();
        StepExecution cleanupStep = createStepExecution(jobExecution, "cleanupStep");
        jobExecution.addStepExecutions(List.of(cleanupStep));

        assertDoesNotThrow(() -> listener.afterJob(jobExecution));
    }

    @Test
    void afterJob_withWorkerSteps_shouldPrintPerIntermediarioStats() {
        JobExecution jobExecution = createJobExecution();

        StepExecution workerStep1 = createStepExecution(jobExecution, "ibanCheckWorkerStep:partition-11111111111");
        workerStep1.setReadCount(100);
        workerStep1.setWriteCount(100);

        StepExecution workerStep2 = createStepExecution(jobExecution, "ibanCheckWorkerStep:partition-22222222222");
        workerStep2.setReadCount(50);
        workerStep2.setWriteCount(50);

        StepExecution confrontoStep = createStepExecution(jobExecution, "confrontoIbanStep");

        jobExecution.addStepExecutions(List.of(workerStep1, workerStep2, confrontoStep));

        assertDoesNotThrow(() -> listener.afterJob(jobExecution));
    }

    @Test
    void afterJob_withWorkerStepWithCodIntermediarioInContext_shouldExtractFromContext() {
        JobExecution jobExecution = createJobExecution();

        StepExecution workerStep = createStepExecution(jobExecution, "ibanCheckWorkerStep");
        workerStep.getExecutionContext().putString("codIntermediario", "99999999999");
        workerStep.setReadCount(25);
        workerStep.setWriteCount(25);

        StepExecution confrontoStep = createStepExecution(jobExecution, "confrontoIbanStep");

        jobExecution.addStepExecutions(List.of(workerStep, confrontoStep));

        assertDoesNotThrow(() -> listener.afterJob(jobExecution));
    }

    @Test
    void afterJob_withConfrOntoIbanStepButNoWorkerSteps_shouldPrintNoPartitions() {
        JobExecution jobExecution = createJobExecution();

        StepExecution confrontoStep = createStepExecution(jobExecution, "confrontoIbanStep");
        jobExecution.addStepExecutions(List.of(confrontoStep));

        assertDoesNotThrow(() -> listener.afterJob(jobExecution));
    }
}
