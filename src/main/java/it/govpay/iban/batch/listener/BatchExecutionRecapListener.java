package it.govpay.iban.batch.listener;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Listener che stampa un riepilogo dettagliato dell'esecuzione del batch per ogni intermediario.
 */
@Component
@Slf4j
public class BatchExecutionRecapListener implements JobExecutionListener {

	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("=".repeat(80));
        log.info("INIZIO BATCH CONTROLLO IBAN");
        log.info("Job ID: {}", jobExecution.getJobId());
        log.info("Avvio: {}", LocalDateTime.now().format(TIME_FORMATTER));
        log.info("=".repeat(80));
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        log.info("=".repeat(80));
        log.info("RIEPILOGO ESECUZIONE BATCH");
        log.info("=".repeat(80));

        // Statistiche generali
        Duration duration = Duration.between(
            jobExecution.getStartTime(),
            jobExecution.getEndTime()
        );

        log.info("Status finale: {}", jobExecution.getStatus());
        log.info("Durata totale: {} secondi", duration.getSeconds());
        log.info("");

        // Statistiche per step
        printStepStatistics(jobExecution);

        log.info("=".repeat(80));
    }

    private void printStepStatistics(JobExecution jobExecution) {
        Collection<StepExecution> stepExecutions = jobExecution.getStepExecutions();

        // Step 1: Cleanup
        stepExecutions.stream()
            .filter(se -> se.getStepName().equals("cleanupStep"))
            .findFirst()
            .ifPresent(this::printCleanupStats);

        // Step 2: Check Iban from PagoPA
        stepExecutions.stream()
            .filter(se -> se.getStepName().equals("confrontoIbanStep"))
            .findFirst()
            .ifPresent(this::printCheckStepStats);
    }

    private void printCleanupStats(StepExecution stepExecution) {
        log.info("--- STEP 1: CLEANUP IBAN_PAGOPA_TEMP ---");
        log.info("Status: {}", stepExecution.getStatus());
        long durationMs = Duration.between(stepExecution.getStartTime(), stepExecution.getEndTime()).toMillis();
        log.info("Durata: {} ms", durationMs);
        log.info("");
    }

    private void printCheckStepStats(StepExecution masterStepExecution) {
        log.info("--- STEP 2: CHECK IBAN ---");
        log.info("Status master step: {}", masterStepExecution.getStatus());

        // Statistiche aggregate dalle partizioni
        Collection<StepExecution> partitionSteps = masterStepExecution.getJobExecution().getStepExecutions().stream()
            .filter(se -> se.getStepName().startsWith("ibanCheckWorkerStep"))
            .toList();

        if (partitionSteps.isEmpty()) {
            log.info("Nessuna partizione eseguita (nessun dominio da processare)");
            log.info("");
            return;
        }

        int totalRead = 0;
        int totalWritten = 0;
        int totalErrors = 0;
        long totalDuration = 0;
        Map<String, PartitionStats> intermediariStats = new LinkedHashMap<>();

        for (StepExecution partitionExec : partitionSteps) {
            totalRead += partitionExec.getReadCount();
            totalWritten += partitionExec.getWriteCount();
            totalErrors += (int) (partitionExec.getReadSkipCount() + partitionExec.getProcessSkipCount());

            long duration = Duration.between(partitionExec.getStartTime(), partitionExec.getEndTime()).toMillis();
            totalDuration += duration;

            // Estrai codIntermediario dal nome della partizione o dal context
            String codIntermediario = extractCodIntermediario(partitionExec);
            if (codIntermediario != null) {
                PartitionStats stats = new PartitionStats();
                stats.codIntermediario = codIntermediario;
                stats.readCount = (int) partitionExec.getReadCount();
                stats.writeCount = (int) partitionExec.getWriteCount();
                stats.errorCount = (int) (partitionExec.getReadSkipCount() + partitionExec.getProcessSkipCount());
                stats.status = partitionExec.getStatus().toString();
                stats.durationMs = duration;
                intermediariStats.put(codIntermediario, stats);
            }
        }

        log.info("Partizioni totali: {}", partitionSteps.size());
        log.info("IBAN letti: {}", totalRead);
        log.info("IBAN processati: {}", totalWritten);
        log.info("Errori: {}", totalErrors);
        log.info("Durata totale: {} secondi", totalDuration / 1000);
        log.info("");

        // Dettaglio per dominio
        if (!intermediariStats.isEmpty()) {
            log.info("Dettaglio per intermediario:");
            log.info("-".repeat(80));
            log.info(String.format("%-20s %-10s %-10s %-10s %-10s %-15s %-10s",
                "INTERMEDIARIO", "LETTI", "PROCESSATI", "SKIPPATI", "ERRORI", "STATUS", "DURATA(s)"));
            log.info("-".repeat(80));

            intermediariStats.values().forEach(stats -> 
                log.info(String.format("%-20s %-10d %-10d %-10d %-15s %-10.1f",
                    stats.codIntermediario,
                    stats.readCount,
                    stats.writeCount,
                    stats.errorCount,
                    stats.status,
                    stats.durationMs / 1000.0
                ))
            );
            log.info("-".repeat(80));
        }
        log.info("");
    }

    private String extractCodIntermediario(StepExecution stepExecution) {
        // Prova a estrarre codIntermediario dall'execution context
        if (stepExecution.getExecutionContext().containsKey("codIntermediario")) {
            return stepExecution.getExecutionContext().getString("codIntermediario");
        }

        // Altrimenti dal nome dello step (formato: stepName:partition-CODDOMINIO)
        String stepName = stepExecution.getStepName();
        if (stepName.contains(":partition-")) {
            return stepName.substring(stepName.indexOf(":partition-") + 11);
        }

        return "unknown";
    }

    private static class PartitionStats {
        String codIntermediario;
        int readCount;
        int writeCount;
        int errorCount;
        String status;
        long durationMs;
    }
}
