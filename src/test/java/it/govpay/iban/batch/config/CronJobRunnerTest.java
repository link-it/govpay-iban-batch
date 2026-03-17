package it.govpay.iban.batch.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Job;
import org.springframework.context.ApplicationContext;

import it.govpay.common.batch.runner.JobExecutionHelper;

@ExtendWith(MockitoExtension.class)
class CronJobRunnerTest {

    @Mock
    private JobExecutionHelper jobExecutionHelper;

    @Mock
    private Job ibanCheckJob;

    @Mock
    private ApplicationContext applicationContext;

    @Test
    void constructor_shouldCreateInstance() {
        CronJobRunner runner = new CronJobRunner(jobExecutionHelper, ibanCheckJob);
        assertNotNull(runner);
    }

    @Test
    void setApplicationContext_shouldNotThrow() {
        CronJobRunner runner = new CronJobRunner(jobExecutionHelper, ibanCheckJob);
        runner.setApplicationContext(applicationContext);
        assertNotNull(runner);
    }
}
