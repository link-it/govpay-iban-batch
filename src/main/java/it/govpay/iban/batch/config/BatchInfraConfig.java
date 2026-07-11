package it.govpay.iban.batch.config;

import java.time.ZoneId;

import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.common.batch.service.JobConcurrencyService;

/**
 * Configurazione dei bean infrastrutturali per la gestione batch multi-nodo.
 * <p>
 * Fornisce i bean di govpay-common necessari per:
 * <ul>
 *   <li>{@link JobConcurrencyService} — prevenzione esecuzione concorrente e gestione job stale</li>
 *   <li>{@link JobExecutionHelper} — esecuzione job con parametri standard e controllo pre-esecuzione</li>
 * </ul>
 */
@Configuration
public class BatchInfraConfig {

    @Bean
    public JobConcurrencyService jobConcurrencyService(
            JobRepository jobRepository,
            @Value("${govpay.batch.stale-threshold-minutes:120}") int staleThresholdMinutes) {
        return new JobConcurrencyService(jobRepository, staleThresholdMinutes);
    }

    @Bean
    public JobExecutionHelper jobExecutionHelper(
            JobOperator jobOperator,
            JobConcurrencyService jobConcurrencyService,
            @Value("${govpay.batch.cluster-id:GovPay-iban-Batch}") String clusterId,
            ZoneId applicationZoneId) {
        return new JobExecutionHelper(jobOperator, jobConcurrencyService, clusterId, applicationZoneId);
    }

    /**
     * Task executor per l'elaborazione parallela delle partizioni dello Step 2.
     * <p>
     * Definito qui (e non in {@link it.govpay.iban.batch.config.BatchJobConfiguration},
     * che dipende dal {@code transactionManager}) per evitare un ciclo di creazione bean:
     * in Spring Boot 4 la {@code entityManagerFactoryBuilder} inietta la mappa di tutti i
     * bean {@code AsyncTaskExecutor}, forzandone l'istanziazione durante il setup JPA.
     */
    @Bean
    public SimpleAsyncTaskExecutor taskExecutor(BatchProperties batchProperties) {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("iban-batch-");
        executor.setConcurrencyLimit(batchProperties.getThreadPoolSize());
        return executor;
    }
}
