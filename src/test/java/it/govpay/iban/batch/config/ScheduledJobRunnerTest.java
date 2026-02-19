package it.govpay.iban.batch.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;

import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.common.batch.runner.JobExecutionHelper.PreExecutionCheckResult;
import it.govpay.common.batch.runner.JobExecutionHelper.PreExecutionResult;
import it.govpay.iban.batch.Costanti;

@ExtendWith(MockitoExtension.class)
class ScheduledJobRunnerTest {

    @Mock
    private JobExecutionHelper jobExecutionHelper;

    @Mock
    private Job ibanCheckJob;

    private ScheduledJobRunner runner;

    @BeforeEach
    void setUp() {
        runner = new ScheduledJobRunner(jobExecutionHelper, ibanCheckJob);
    }

    private JobExecution createJobExecution(BatchStatus status) {
        JobInstance jobInstance = new JobInstance(1L, Costanti.IBAN_CHECK_JOB_NAME);
        JobExecution execution = new JobExecution(jobInstance, 1L, new JobParameters());
        execution.setStatus(status);
        return execution;
    }

    @Test
    void runBatchIbanCheckJob_runningOnOtherNode_shouldReturnNull() throws Exception {
        when(jobExecutionHelper.checkBeforeExecution(Costanti.IBAN_CHECK_JOB_NAME))
                .thenReturn(new PreExecutionResult(PreExecutionCheckResult.RUNNING_ON_OTHER_NODE, null, "other-cluster"));

        JobExecution result = runner.runBatchIbanCheckJob();

        assertNull(result);
        verify(jobExecutionHelper, never()).runJob(any(), any());
    }

    @Test
    void runBatchIbanCheckJob_runningOnThisNode_shouldReturnNull() throws Exception {
        when(jobExecutionHelper.checkBeforeExecution(Costanti.IBAN_CHECK_JOB_NAME))
                .thenReturn(new PreExecutionResult(PreExecutionCheckResult.RUNNING_ON_THIS_NODE, null, "this-cluster"));

        JobExecution result = runner.runBatchIbanCheckJob();

        assertNull(result);
        verify(jobExecutionHelper, never()).runJob(any(), any());
    }

    @Test
    void runBatchIbanCheckJob_canProceed_shouldLaunchJob() throws Exception {
        when(jobExecutionHelper.checkBeforeExecution(Costanti.IBAN_CHECK_JOB_NAME))
                .thenReturn(new PreExecutionResult(PreExecutionCheckResult.CAN_PROCEED, null, null));

        JobExecution mockExecution = createJobExecution(BatchStatus.COMPLETED);
        when(jobExecutionHelper.runJob(ibanCheckJob, Costanti.IBAN_CHECK_JOB_NAME))
                .thenReturn(mockExecution);

        JobExecution result = runner.runBatchIbanCheckJob();

        assertNotNull(result);
        assertEquals(BatchStatus.COMPLETED, result.getStatus());
        verify(jobExecutionHelper).runJob(ibanCheckJob, Costanti.IBAN_CHECK_JOB_NAME);
    }

    @Test
    void runBatchIbanCheckJob_staleAbandonedCanProceed_shouldLaunchNewJob() throws Exception {
        when(jobExecutionHelper.checkBeforeExecution(Costanti.IBAN_CHECK_JOB_NAME))
                .thenReturn(new PreExecutionResult(PreExecutionCheckResult.STALE_ABANDONED_CAN_PROCEED, null, null));

        JobExecution mockExecution = createJobExecution(BatchStatus.COMPLETED);
        when(jobExecutionHelper.runJob(ibanCheckJob, Costanti.IBAN_CHECK_JOB_NAME))
                .thenReturn(mockExecution);

        JobExecution result = runner.runBatchIbanCheckJob();

        assertNotNull(result);
        verify(jobExecutionHelper).runJob(ibanCheckJob, Costanti.IBAN_CHECK_JOB_NAME);
    }

    @Test
    void runBatchIbanCheckJob_staleAbandonFailed_shouldReturnNull() throws Exception {
        when(jobExecutionHelper.checkBeforeExecution(Costanti.IBAN_CHECK_JOB_NAME))
                .thenReturn(new PreExecutionResult(PreExecutionCheckResult.STALE_ABANDON_FAILED, null, null));

        JobExecution result = runner.runBatchIbanCheckJob();

        assertNull(result);
        verify(jobExecutionHelper, never()).runJob(any(), any());
    }
}
