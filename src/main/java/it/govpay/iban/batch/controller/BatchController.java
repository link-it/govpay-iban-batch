package it.govpay.iban.batch.controller;

import java.time.ZoneId;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import it.govpay.common.batch.controller.AbstractBatchController;
import it.govpay.common.batch.dto.BatchStatusInfo;
import it.govpay.common.batch.dto.LastExecutionInfo;
import it.govpay.common.batch.dto.NextExecutionInfo;
import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.common.client.service.ConnettoreService;
import it.govpay.iban.batch.Costanti;
import it.govpay.iban.batch.service.IbanPagopaApiService;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller REST per l'esecuzione manuale e il monitoraggio dei job batch.
 */
@Slf4j
@RestController
@RequestMapping("/api/batch")
public class BatchController extends AbstractBatchController {

    private final Job ibanCheckJob;
    private final ConnettoreService connettoreService;
    private final IbanPagopaApiService ibanPagopaApiService;

    public BatchController(
            JobExecutionHelper jobExecutionHelper,
            JobExplorer jobExplorer,
            @Qualifier("ibanCheckJob") Job ibanCheckJob,
            ConnettoreService connettoreService,
            IbanPagopaApiService ibanPagopaApiService,
            Environment environment,
            ZoneId applicationZoneId,
            @Value("${scheduler.ibanCheckJob.fixedDelayString:7200000}") long schedulerIntervalMillis) {
        super(jobExecutionHelper, jobExplorer, environment, applicationZoneId, schedulerIntervalMillis);
        this.ibanCheckJob = ibanCheckJob;
        this.connettoreService = connettoreService;
        this.ibanPagopaApiService = ibanPagopaApiService;
    }

    @Override
    protected Job getJob() {
        return ibanCheckJob;
    }

    @Override
    protected String getJobName() {
        return Costanti.IBAN_CHECK_JOB_NAME;
    }

    @Override
    protected ResponseEntity<String> clearCache() {
        log.info("Svuotamento cache in corso...");
        connettoreService.clearCache();
        ibanPagopaApiService.clearApiCache();
        log.info("Cache svuotate con successo");
        return ResponseEntity.ok("Cache svuotate con successo");
    }

    @GetMapping("/run")
    public ResponseEntity<Object> eseguiJobEndpoint(
            @RequestParam(name = "force", required = false, defaultValue = "false") boolean force) {
        return eseguiJob(force);
    }

    @GetMapping("/status")
    public ResponseEntity<BatchStatusInfo> getStatusEndpoint() {
        return getStatus();
    }

    @GetMapping("/lastExecution")
    public ResponseEntity<LastExecutionInfo> getLastExecutionEndpoint() {
        return getLastExecution();
    }

    @GetMapping("/nextExecution")
    public ResponseEntity<NextExecutionInfo> getNextExecutionEndpoint() {
        return getNextExecution();
    }
}
