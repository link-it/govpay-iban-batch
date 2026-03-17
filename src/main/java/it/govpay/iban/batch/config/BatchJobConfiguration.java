package it.govpay.iban.batch.config;

import it.govpay.iban.batch.Costanti;
import it.govpay.iban.batch.dto.IbanPagopa;
import it.govpay.iban.batch.step2.IbanCheckProcessor;
import it.govpay.iban.batch.step2.IbanCheckReader;
import it.govpay.iban.batch.step2.IbanCheckWriter;
import it.govpay.iban.batch.tasklet.CleanupIbanTempTasklet;
import lombok.extern.slf4j.Slf4j;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Configuration for Check IBAN Batch Job
 */
@Configuration
@Slf4j
public class BatchJobConfiguration {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final BatchProperties batchProperties;

    public BatchJobConfiguration(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        BatchProperties batchProperties
    ) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.batchProperties = batchProperties;
    }

    /**
     * Main Check IBAN Job with 2 steps
     */
    @Bean
    public Job ibanCheckJob(
        Step cleanupStep,
        Step ibanCheckAcquisitionStep,
        it.govpay.iban.batch.listener.BatchExecutionRecapListener batchExecutionRecapListener
    ) {
        return new JobBuilder(Costanti.IBAN_CHECK_JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .listener(batchExecutionRecapListener)
            .start(cleanupStep)
            .next(ibanCheckAcquisitionStep)
            .build();
    }

    /**
     * Step 1: Cleanup IBAN_PAGOPA_TEMP table
     */
    @Bean
    public Step cleanupStep(CleanupIbanTempTasklet cleanupIbanTempTasklet) {
        return new StepBuilder("cleanupStep", jobRepository)
            .tasklet(cleanupIbanTempTasklet, transactionManager)
            .build();
    }

    /**
     * Step 2: Check IBAN (PARTITIONED by intermediario)
     */
    @Bean
    public Step ibanCheckAcquisitionStep(
    	it.govpay.iban.batch.partitioner.IntermediarioPartitioner intermediarioPartitioner,
        Step ibanCheckWorkerStep
    ) {
        return new StepBuilder("ibanCheckAcquisitionStep", jobRepository)
        	.partitioner("ibanCheckAcquisitionStep", intermediarioPartitioner)
            .step(ibanCheckWorkerStep)
            .gridSize(batchProperties.getThreadPoolSize()) // Numero di partizioni parallele
            .taskExecutor(taskExecutor())
            .build();
    }

    /**
     * worker Step: Check IBAN per un intermediario
     */
    @Bean
    public Step ibanCheckWorkerStep(
        IbanCheckReader ibanCheckReader,
        IbanCheckProcessor ibanCheckProcessor,
        IbanCheckWriter ibanCheckWriter
    ) {
        return new StepBuilder("ibanCheckWorkerStep", jobRepository)
            .<IbanPagopa, IbanPagopa>chunk(batchProperties.getCheckIbanChunkSize(), transactionManager)
            .reader(ibanCheckReader)
            .processor(ibanCheckProcessor)
            .writer(ibanCheckWriter)
            .listener(ibanCheckReader) // Register reader as step listener for queue reset
            .build();
    }

    /**
     * Task executor for parallel processing in Step 2
     */
    @Bean
    public SimpleAsyncTaskExecutor taskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("iban-batch-");
        executor.setConcurrencyLimit(batchProperties.getThreadPoolSize());
        return executor;
    }
}
