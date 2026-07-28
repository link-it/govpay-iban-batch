package it.govpay.iban.batch.config;

import it.govpay.iban.batch.Costanti;
import it.govpay.iban.batch.dto.EnteCreditorePagopa;
import it.govpay.iban.batch.dto.IbanPagopa;
import it.govpay.iban.batch.step2.IbanCheckProcessor;
import it.govpay.iban.batch.step2.IbanCheckReader;
import it.govpay.iban.batch.step2.IbanCheckWriter;
import it.govpay.iban.batch.step3.EcCheckProcessor;
import it.govpay.iban.batch.step3.EcSyncReader;
import it.govpay.iban.batch.step3.EcSyncWriter;
import it.govpay.iban.batch.tasklet.CleanupEcCheckTasklet;
import it.govpay.iban.batch.tasklet.CleanupPagopaIbanCheckTasklet;
import it.govpay.iban.batch.tasklet.CleanupStaleEntiCreditoriTasklet;
import it.govpay.iban.batch.tasklet.CleanupStaleIbanCacheTasklet;
import lombok.extern.slf4j.Slf4j;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
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
     * Main Check IBAN Job: cleanup -> check IBAN -> sync anagrafica Enti Creditori -> cleanup stale EC cache
     */
    @Bean
    public Job ibanCheckJob(
        Step cleanupStep,
        Step cleanupEcCheckStep,
        Step ibanCheckAcquisitionStep,
        Step cleanupStaleIbanCacheStep,
        Step ecSyncAcquisitionStep,
        Step cleanupStaleEntiCreditoriStep,
        it.govpay.iban.batch.listener.BatchExecutionRecapListener batchExecutionRecapListener
    ) {
        return new JobBuilder(Costanti.IBAN_CHECK_JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .listener(batchExecutionRecapListener)
            .start(cleanupStep)
            .next(cleanupEcCheckStep)
            .next(ibanCheckAcquisitionStep)
            .next(cleanupStaleIbanCacheStep)
            .next(ecSyncAcquisitionStep)
            .next(cleanupStaleEntiCreditoriStep)
            .build();
    }

    /**
     * Step 1: Cleanup PAGOPA_IBAN_CHECK table
     */
    @Bean
    public Step cleanupStep(CleanupPagopaIbanCheckTasklet cleanupPagopaIbanCheckTasklet) {
        return new StepBuilder("cleanupStep", jobRepository)
            .tasklet(cleanupPagopaIbanCheckTasklet, transactionManager)
            .build();
    }

    /**
     * Step 1b: Cleanup PAGOPA_EC_CHECK table
     */
    @Bean
    public Step cleanupEcCheckStep(CleanupEcCheckTasklet cleanupEcCheckTasklet) {
        return new StepBuilder("cleanupEcCheckStep", jobRepository)
            .tasklet(cleanupEcCheckTasklet, transactionManager)
            .build();
    }

    /**
     * Step 2: Check IBAN (PARTITIONED by intermediario)
     */
    @Bean
    public Step ibanCheckAcquisitionStep(
    	it.govpay.iban.batch.partitioner.IntermediarioPartitioner intermediarioPartitioner,
        Step ibanCheckWorkerStep,
        SimpleAsyncTaskExecutor taskExecutor
    ) {
        return new StepBuilder("ibanCheckAcquisitionStep", jobRepository)
        	.partitioner("ibanCheckAcquisitionStep", intermediarioPartitioner)
            .step(ibanCheckWorkerStep)
            .gridSize(batchProperties.getThreadPoolSize()) // Numero di partizioni parallele
            .taskExecutor(taskExecutor)
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
     * Step 2b: Cleanup delle righe stale in pagopa_iban_cache (solo se lo Step 2 e' COMPLETED)
     */
    @Bean
    public Step cleanupStaleIbanCacheStep(CleanupStaleIbanCacheTasklet cleanupStaleIbanCacheTasklet) {
        return new StepBuilder("cleanupStaleIbanCacheStep", jobRepository)
            .tasklet(cleanupStaleIbanCacheTasklet, transactionManager)
            .build();
    }

    /**
     * Step 3: Sync anagrafica Enti Creditori (PARTITIONED by intermediario, stesso partitioner dello Step 2)
     */
    @Bean
    public Step ecSyncAcquisitionStep(
    	it.govpay.iban.batch.partitioner.IntermediarioPartitioner intermediarioPartitioner,
        Step ecSyncWorkerStep,
        SimpleAsyncTaskExecutor taskExecutor
    ) {
        return new StepBuilder("ecSyncAcquisitionStep", jobRepository)
        	.partitioner("ecSyncAcquisitionStep", intermediarioPartitioner)
            .step(ecSyncWorkerStep)
            .gridSize(batchProperties.getThreadPoolSize())
            .taskExecutor(taskExecutor)
            .build();
    }

    /**
     * worker Step: sync Enti Creditori per un intermediario
     */
    @Bean
    public Step ecSyncWorkerStep(
        EcSyncReader ecSyncReader,
        EcCheckProcessor ecCheckProcessor,
        EcSyncWriter ecSyncWriter
    ) {
        return new StepBuilder("ecSyncWorkerStep", jobRepository)
            .<EnteCreditorePagopa, EnteCreditorePagopa>chunk(batchProperties.getEcSyncChunkSize(), transactionManager)
            .reader(ecSyncReader)
            .processor(ecCheckProcessor)
            .writer(ecSyncWriter)
            .listener(ecSyncReader) // Register reader as step listener for queue reset
            .build();
    }

    /**
     * Step 4: Cleanup delle righe stale in pagopa_ec_cache (solo se lo Step 3 e' COMPLETED)
     */
    @Bean
    public Step cleanupStaleEntiCreditoriStep(CleanupStaleEntiCreditoriTasklet cleanupStaleEntiCreditoriTasklet) {
        return new StepBuilder("cleanupStaleEntiCreditoriStep", jobRepository)
            .tasklet(cleanupStaleEntiCreditoriTasklet, transactionManager)
            .build();
    }
}
