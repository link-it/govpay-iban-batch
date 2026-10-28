package it.govpay.iban.batch.listener;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepExecution;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import it.govpay.common.mail.MailInfo;
import it.govpay.iban.batch.Costanti;
import it.govpay.iban.batch.config.FileStorageConfig;
import it.govpay.iban.batch.mail.MailService;
import it.govpay.iban.batch.step2.IbanCheckWriter;

class BatchExecutionRecapListenerTest {

    private static final ZoneId ZONE_ID = ZoneId.of("Europe/Rome");

    private BatchExecutionRecapListener listener;

    private final Logger listenerLogger = (Logger) LoggerFactory.getLogger(BatchExecutionRecapListener.class);
    private final ListAppender<ILoggingEvent> logAppender = new ListAppender<>();

    @BeforeEach
    void setUp() {
        logAppender.start();
        listenerLogger.addAppender(logAppender);
        FileStorageConfig fileStorageConfig = mock(FileStorageConfig.class);
        when(fileStorageConfig.isInviaMail()).thenReturn(false);
        when(fileStorageConfig.getReportDirectory()).thenReturn(Path.of("/tmp"));
        when(fileStorageConfig.getDestinatario()).thenReturn(Collections.emptyList());
        MailService mailService = mock(MailService.class);
        listener = new BatchExecutionRecapListener(fileStorageConfig, mailService, ZONE_ID);
    }

    @AfterEach
    void tearDown() {
        listenerLogger.detachAppender(logAppender);
    }

    /** Il riepilogo e' fatto solo di log: e' l'unico modo di verificare che sia stato stampato. */
    private String logs() {
        return String.join("\n", logAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList());
    }

    private JobExecution createJobExecution() {
        JobInstance jobInstance = new JobInstance(1L, "ibanCheckJob");
        JobExecution execution = new JobExecution(1L, jobInstance, new JobParameters());
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
    void afterJob_withWorkerSteps_shouldPrintPerIntermediarioStats() {
        JobExecution jobExecution = createJobExecution();

        StepExecution workerStep1 = createStepExecution(jobExecution, "ibanCheckWorkerStep:partition-11111111111");
        workerStep1.setReadCount(100);
        workerStep1.setWriteCount(100);

        StepExecution workerStep2 = createStepExecution(jobExecution, "ibanCheckWorkerStep:partition-22222222222");
        workerStep2.setReadCount(50);
        workerStep2.setWriteCount(50);

        StepExecution masterStep = createStepExecution(jobExecution, "ibanCheckAcquisitionStep");

        jobExecution.addStepExecutions(List.of(workerStep1, workerStep2, masterStep));

        assertDoesNotThrow(() -> listener.afterJob(jobExecution));

        String logs = logs();
        assertTrue(logs.contains("Dettaglio per intermediario"), logs);
        assertTrue(logs.contains("IBAN letti: 150"), logs);
        assertTrue(logs.contains("11111111111"), logs);
        assertTrue(logs.contains("22222222222"), logs);
    }

    @Test
    void afterJob_withWorkerStepWithCodIntermediarioInContext_shouldExtractFromContext() {
        JobExecution jobExecution = createJobExecution();

        StepExecution workerStep = createStepExecution(jobExecution, "ibanCheckWorkerStep");
        workerStep.getExecutionContext().putString("codIntermediario", "99999999999");
        workerStep.setReadCount(25);
        workerStep.setWriteCount(25);

        StepExecution masterStep = createStepExecution(jobExecution, "ibanCheckAcquisitionStep");

        jobExecution.addStepExecutions(List.of(workerStep, masterStep));

        assertDoesNotThrow(() -> listener.afterJob(jobExecution));

        assertTrue(logs().contains("99999999999"), logs());
    }

    @Test
    void afterJob_withMasterStepButNoWorkerSteps_shouldPrintNoPartitions() {
        JobExecution jobExecution = createJobExecution();

        StepExecution masterStep = createStepExecution(jobExecution, "ibanCheckAcquisitionStep");
        jobExecution.addStepExecutions(List.of(masterStep));

        assertDoesNotThrow(() -> listener.afterJob(jobExecution));

        assertTrue(logs().contains("Nessuna partizione eseguita"), logs());
    }

    // ============ Invio del report via mail ============

    private static final String INTESTAZIONE = "\"codFiscale\",\"name\"\n";

    @TempDir
    Path reportDir;

    private MailService mailService;
    private BatchExecutionRecapListener mailListener;

    @BeforeEach
    void setUpMail() {
        FileStorageConfig fileStorageConfig = mock(FileStorageConfig.class);
        when(fileStorageConfig.isInviaMail()).thenReturn(true);
        when(fileStorageConfig.getDestinatario()).thenReturn(List.of("operatore@ente.it"));
        when(fileStorageConfig.getMessaggio()).thenReturn("In allegato il report di verifica IBAN.");
        mailService = mock(MailService.class);
        when(mailService.isAbilitato()).thenReturn(true);
        mailListener = new BatchExecutionRecapListener(fileStorageConfig, mailService, ZONE_ID);
    }

    /** Partizione che ha scritto un report con il contenuto dato e i conteggi per esito indicati. */
    private StepExecution partizione(JobExecution jobExecution, String codIntermediario, String contenuto,
                                     Map<String, Integer> conteggi) throws Exception {
        StepExecution partizione = createStepExecution(jobExecution, "ibanCheckWorkerStep:partition-" + codIntermediario);
        partizione.getExecutionContext().putString("codIntermediario", codIntermediario);
        if (contenuto != null) {
            // Timestamp nel nome diverso dall'avvio del job: e' il caso reale, perche' ogni
            // partizione apre il suo report quando scrive la prima riga.
            Path report = Files.writeString(reportDir.resolve("reportCheckIban-" + codIntermediario + "-20260101_000007.csv"),
                    contenuto, StandardCharsets.UTF_8);
            partizione.getExecutionContext().putString(IbanCheckWriter.REPORT_FILE_KEY, report.toString());
        }
        conteggi.forEach((stato, n) -> partizione.getExecutionContext().putInt(IbanCheckWriter.STATS_CHECK_STATO_PREFIX + stato, n));
        return partizione;
    }

    private MailInfo mailInviata() {
        ArgumentCaptor<MailInfo> captor = ArgumentCaptor.forClass(MailInfo.class);
        verify(mailService).inviaEmail(captor.capture());
        return captor.getValue();
    }

    @Test
    void afterJob_shouldAttachTheReportsWrittenByThePartitions() throws Exception {
        JobExecution jobExecution = createJobExecution();
        mailListener.beforeJob(jobExecution);
        jobExecution.addStepExecutions(List.of(
            createStepExecution(jobExecution, "ibanCheckAcquisitionStep"),
            partizione(jobExecution, "11111111111", INTESTAZIONE + "riga1\n", Map.of(Costanti.CHECK_OK, 1)),
            partizione(jobExecution, "22222222222", INTESTAZIONE + "riga2\n", Map.of(Costanti.CHECK_NON_CENSITO, 1)),
            // Partizione senza IBAN: nessun report, non deve comparire fra gli allegati.
            partizione(jobExecution, "33333333333", null, Map.of())));

        mailListener.afterJob(jobExecution);

        Map<String, byte[]> allegati = mailInviata().getAllegati();
        assertEquals(List.of("reportCheckIban-11111111111-20260101_000007.csv", "reportCheckIban-22222222222-20260101_000007.csv"),
                List.copyOf(allegati.keySet()));
        assertArrayEquals((INTESTAZIONE + "riga1\n").getBytes(StandardCharsets.UTF_8),
                allegati.get("reportCheckIban-11111111111-20260101_000007.csv"));
    }

    @Test
    void afterJob_mailTextShouldReportCountsByCheckStato() throws Exception {
        JobExecution jobExecution = createJobExecution();
        jobExecution.addStepExecutions(List.of(
            createStepExecution(jobExecution, "ibanCheckAcquisitionStep"),
            partizione(jobExecution, "11111111111", INTESTAZIONE + "r\nr\nr\n",
                Map.of(Costanti.CHECK_OK, 2, Costanti.CHECK_NON_CENSITO, 1)),
            partizione(jobExecution, "22222222222", INTESTAZIONE + "r\n", Map.of(Costanti.CHECK_NON_ATTIVO, 1)),
            partizione(jobExecution, "33333333333", null, Map.of())));

        mailListener.afterJob(jobExecution);

        String testo = mailInviata().getTesto();
        assertTrue(testo.startsWith("In allegato il report di verifica IBAN."), testo);
        assertTrue(testo.contains("  OK: 2\n  NON_CENSITO: 1\n  INFO_DIVERSE: 0\n  NON_ATTIVO: 1\n"), testo);
        assertTrue(testo.contains("  11111111111: OK 2, NON_CENSITO 1\n"), testo);
        assertTrue(testo.contains("  22222222222: NON_ATTIVO 1\n"), testo);
        assertTrue(testo.contains("  33333333333: nessun IBAN\n"), testo);
    }

    @Test
    void afterJob_checkStepFailed_shouldNotSendMail() throws Exception {
        JobExecution jobExecution = createJobExecution();
        StepExecution masterStep = createStepExecution(jobExecution, "ibanCheckAcquisitionStep");
        masterStep.setStatus(BatchStatus.FAILED);
        jobExecution.addStepExecutions(List.of(masterStep,
            partizione(jobExecution, "11111111111", INTESTAZIONE + "riga1\n", Map.of(Costanti.CHECK_OK, 1))));

        mailListener.afterJob(jobExecution);

        // Report parziale: non va spacciato per una verifica completa.
        verify(mailService, never()).inviaEmail(any());
    }

    @Test
    void afterJob_noReportWithData_shouldNotSendMail() throws Exception {
        JobExecution jobExecution = createJobExecution();
        jobExecution.addStepExecutions(List.of(
            createStepExecution(jobExecution, "ibanCheckAcquisitionStep"),
            // Solo intestazione: nessuna riga di dati scritta.
            partizione(jobExecution, "11111111111", INTESTAZIONE, Map.of()),
            partizione(jobExecution, "22222222222", null, Map.of())));

        mailListener.afterJob(jobExecution);

        verify(mailService, never()).inviaEmail(any());
    }
}
