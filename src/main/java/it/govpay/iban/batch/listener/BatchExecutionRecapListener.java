package it.govpay.iban.batch.listener;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

import it.govpay.common.mail.MailInfo;
import it.govpay.iban.batch.Costanti;
import it.govpay.iban.batch.config.FileStorageConfig;
import it.govpay.iban.batch.mail.MailService;
import it.govpay.iban.batch.step2.IbanCheckWriter;
import lombok.extern.slf4j.Slf4j;

/**
 * Listener che stampa un riepilogo dettagliato dell'esecuzione del batch per ogni intermediario.
 */
@Component
@Slf4j
public class BatchExecutionRecapListener implements JobExecutionListener {

	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final String MASTER_STEP_NAME = "ibanCheckAcquisitionStep";
    private static final String WORKER_STEP_PREFIX = "ibanCheckWorkerStep";

    /** Esiti del confronto nell'ordine in cui compaiono nel testo della mail. */
    private static final List<String> CHECK_STATI = List.of(
        Costanti.CHECK_OK, Costanti.CHECK_NON_CENSITO, Costanti.CHECK_INFO_DIVERSE, Costanti.CHECK_NON_ATTIVO);

    private final FileStorageConfig fileStorageConfig;
    private final MailService mailService;
    private final ZoneId applicationZoneId;

    public BatchExecutionRecapListener(FileStorageConfig fileStorageConfig, MailService mailService,
                                       ZoneId applicationZoneId) {
        this.fileStorageConfig = fileStorageConfig;
        this.mailService = mailService;
        this.applicationZoneId = applicationZoneId;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("=".repeat(80));
        log.info("INIZIO BATCH CONTROLLO IBAN");
        log.info("Job ID: {}", jobExecution.getJobInstanceId());
        log.info("Avvio: {}", LocalDateTime.now(applicationZoneId).format(TIME_FORMATTER));
        log.info("=".repeat(80));
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        log.info("=".repeat(80));
        log.info("RIEPILOGO ESECUZIONE BATCH");
        log.info("=".repeat(80));

        // Statistiche generali
        Duration duration = durata(jobExecution.getStartTime(), jobExecution.getEndTime());

        log.info("Status finale: {}", jobExecution.getStatus());
        log.info("Durata totale: {} secondi", duration.getSeconds());
        log.info("");

        // Statistiche per step
        printStepStatistics(jobExecution);

        log.info("=".repeat(80));

        // Invio report via email
        inviaReportViaMail(jobExecution);
    }

    private void inviaReportViaMail(JobExecution jobExecution) {
        if (!fileStorageConfig.isInviaMail()) {
            log.debug("Invio report via mail disabilitato");
            return;
        }

        if (fileStorageConfig.getDestinatario() == null || fileStorageConfig.getDestinatario().isEmpty()) {
            log.warn("Invio report via mail abilitato ma nessun destinatario configurato");
            return;
        }

        if (!mailService.isAbilitato()) {
            log.warn("Invio report via mail abilitato ma servizio mail non configurato o disabilitato in configurazione GovPay");
            return;
        }

        Map<String, byte[]> allegati = raccogliReportFiles(jobExecution);

        if (!contenutoValido(jobExecution, allegati)) {
            return;
        }

        try {
            MailInfo mailInfo = MailInfo.builder()
                .to(fileStorageConfig.getDestinatario())
                .oggetto(fileStorageConfig.getOggetto())
                .testo(costruisciTestoMail(jobExecution))
                .allegati(allegati)
                .build();

            mailService.inviaEmail(mailInfo);
            log.info("Report inviato via mail a {}", fileStorageConfig.getDestinatario());
        } catch (Exception e) {
            log.error("Errore durante l'invio del report via mail: {}", e.getMessage(), e);
        }
    }

    /**
     * La mail parte solo se la verifica degli IBAN e' terminata e c'e' almeno un
     * report con dati: con lo step fallito i report sono parziali e mandarli
     * farebbe passare per completa una verifica che non lo e'.
     */
    private boolean contenutoValido(JobExecution jobExecution, Map<String, byte[]> allegati) {
        BatchStatus statoVerifica = masterStep(jobExecution).map(StepExecution::getStatus).orElse(null);
        if (statoVerifica != BatchStatus.COMPLETED) {
            log.warn("Report non inviato via mail: la verifica degli IBAN non e' terminata correttamente (stato {})", statoVerifica);
            return false;
        }
        if (allegati.isEmpty()) {
            log.info("Report non inviato via mail: nessun report con dati da allegare");
            return false;
        }
        return true;
    }

    /**
     * Testo della mail: il messaggio configurato seguito dal numero di IBAN per
     * esito del confronto, in totale e per intermediario.
     */
    private String costruisciTestoMail(JobExecution jobExecution) {
        Map<String, Integer> totali = new LinkedHashMap<>();
        CHECK_STATI.forEach(stato -> totali.put(stato, 0));
        StringBuilder dettaglio = new StringBuilder();

        for (StepExecution partizione : partizioni(jobExecution)) {
            StringBuilder riga = new StringBuilder();
            for (String stato : CHECK_STATI) {
                int conteggio = partizione.getExecutionContext().getInt(IbanCheckWriter.STATS_CHECK_STATO_PREFIX + stato, 0);
                totali.merge(stato, conteggio, Integer::sum);
                if (conteggio > 0) {
                    riga.append(riga.isEmpty() ? "" : ", ").append(stato).append(' ').append(conteggio);
                }
            }
            dettaglio.append("  ").append(extractCodIntermediario(partizione)).append(": ")
                .append(riga.isEmpty() ? "nessun IBAN" : riga).append('\n');
        }

        StringBuilder testo = new StringBuilder(fileStorageConfig.getMessaggio()).append("\n\nEsito della verifica:\n");
        totali.forEach((stato, conteggio) -> testo.append("  ").append(stato).append(": ").append(conteggio).append('\n'));
        testo.append("\nDettaglio per intermediario:\n").append(dettaglio);
        return testo.toString();
    }

    private Optional<StepExecution> masterStep(JobExecution jobExecution) {
        return jobExecution.getStepExecutions().stream()
            .filter(se -> se.getStepName().equals(MASTER_STEP_NAME))
            .findFirst();
    }

    private List<StepExecution> partizioni(JobExecution jobExecution) {
        return jobExecution.getStepExecutions().stream()
            .filter(se -> se.getStepName().startsWith(WORKER_STEP_PREFIX))
            .toList();
    }

    /**
     * Raccoglie i file di report CSV scritti in questa esecuzione. Ogni partizione
     * registra il percorso del proprio report nel suo ExecutionContext
     * ({@link IbanCheckWriter#REPORT_FILE_KEY}); le partizioni senza IBAN non
     * scrivono un file e quindi non compaiono. Un report con la sola intestazione
     * (nessuna riga di dati scritta) viene escluso.
     */
    private Map<String, byte[]> raccogliReportFiles(JobExecution jobExecution) {
        Map<String, byte[]> allegati = new LinkedHashMap<>();
        partizioni(jobExecution).stream()
            .filter(se -> se.getExecutionContext().containsKey(IbanCheckWriter.REPORT_FILE_KEY))
            .map(se -> Path.of(se.getExecutionContext().getString(IbanCheckWriter.REPORT_FILE_KEY)))
            .forEach(file -> leggiReport(file)
                .filter(BatchExecutionRecapListener::haRigheDiDati)
                .ifPresent(contenuto -> allegati.put(file.getFileName().toString(), contenuto)));
        return allegati;
    }

    /** Vero se oltre all'intestazione c'e' almeno una riga (ogni riga e' terminata da newline). */
    private static boolean haRigheDiDati(byte[] contenuto) {
        int righe = 0;
        for (byte b : contenuto) {
            if (b == '\n' && ++righe > 1) {
                return true;
            }
        }
        return false;
    }

    /**
     * Legge il contenuto di un singolo file di report. Un errore di lettura non
     * interrompe la raccolta degli altri allegati: viene loggato e il file viene
     * semplicemente escluso dalla mail.
     */
    private Optional<byte[]> leggiReport(Path file) {
        try {
            byte[] contenuto = Files.readAllBytes(file);
            log.debug("Allegato file report: {}", file.getFileName());
            return Optional.of(contenuto);
        } catch (IOException e) {
            log.error("Errore lettura file report {}: {}", file, e.getMessage());
            return Optional.empty();
        }
    }

    private void printStepStatistics(JobExecution jobExecution) {
        // Step 1: Check Iban from PagoPA (master step partizionato)
        masterStep(jobExecution).ifPresent(this::printCheckStepStats);
    }

    private void printCheckStepStats(StepExecution masterStepExecution) {
        log.info("--- STEP 1: CHECK IBAN ---");
        log.info("Status master step: {}", masterStepExecution.getStatus());

        // Statistiche aggregate dalle partizioni
        Collection<StepExecution> partitionSteps = partizioni(masterStepExecution.getJobExecution());

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

            long partDuration = durata(partitionExec.getStartTime(), partitionExec.getEndTime()).toMillis();
            totalDuration += partDuration;

            // Estrai codIntermediario dal nome della partizione o dal context
            String codIntermediario = extractCodIntermediario(partitionExec);
            if (codIntermediario != null) {
                PartitionStats stats = new PartitionStats();
                stats.codIntermediario = codIntermediario;
                stats.readCount = (int) partitionExec.getReadCount();
                stats.writeCount = (int) partitionExec.getWriteCount();
                stats.errorCount = (int) (partitionExec.getReadSkipCount() + partitionExec.getProcessSkipCount());
                stats.status = partitionExec.getStatus().toString();
                stats.durationMs = partDuration;
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
            log.info(String.format("%-20s %-10s %-10s %-10s %-15s %-10s",
                "INTERMEDIARIO", "LETTI", "PROCESSATI", "ERRORI", "STATUS", "DURATA(s)"));
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

    /**
     * Calcola la durata fra due istanti espressi da Spring Batch come
     * {@link LocalDateTime}, tipo che non porta con se' il fuso orario: prima del
     * calcolo i timestamp vengono ancorati al timezone applicativo e convertiti in
     * {@link Instant}, cosi' l'intervallo resta corretto anche a cavallo del cambio
     * fra ora legale e ora solare.
     *
     * @return la durata fra i due istanti, {@link Duration#ZERO} se uno dei due manca
     */
    private Duration durata(LocalDateTime inizio, LocalDateTime fine) {
        if (inizio == null || fine == null) {
            return Duration.ZERO;
        }
        return Duration.between(toInstant(inizio), toInstant(fine));
    }

    private Instant toInstant(LocalDateTime dateTime) {
        return dateTime.atZone(applicationZoneId).toInstant();
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
