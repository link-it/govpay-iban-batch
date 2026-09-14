package it.govpay.iban.batch.step2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.Chunk;
import org.springframework.test.util.ReflectionTestUtils;

import com.opencsv.CSVReader;

import it.govpay.iban.batch.Costanti;
import it.govpay.iban.batch.config.FileStorageConfig;
import it.govpay.iban.batch.dto.IbanPagopa;
import it.govpay.iban.batch.entity.IbanPagopaTempEntity;
import it.govpay.iban.batch.repository.IbanPagopaTempRepository;

@ExtendWith(MockitoExtension.class)
class IbanCheckWriterTest {

    @Mock
    private IbanPagopaTempRepository ibanTempRepository;

    @Mock
    private FileStorageConfig fileStorageConfig;

    @TempDir
    Path tempDir;

    private IbanCheckWriter writer;

    private static final String COD_INTERMEDIARIO = "12345678901";

    @BeforeEach
    void setUp() {
        writer = new IbanCheckWriter(ibanTempRepository, fileStorageConfig);
        ReflectionTestUtils.setField(writer, "codIntermediario", COD_INTERMEDIARIO);
    }

    private IbanPagopa createIbanPagopa(String checkStato) {
        return createIbanPagopa(checkStato, "Comune Test");
    }

    private IbanPagopa createIbanPagopa(String checkStato, String name) {
        return IbanPagopa.builder()
                .codIntermediario(COD_INTERMEDIARIO)
                .fiscalCode("01234567890")
                .name(name)
                .iban("IT60X0542811101000000123456")
                .status("ENABLED")
                .validityDate(OffsetDateTime.of(2025, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC))
                .description("Conto corrente")
                .label("label-test")
                .checkStato(checkStato)
                .checkMotivo(null)
                .build();
    }

    private StepExecution createStepExecution() {
        JobInstance jobInstance = new JobInstance(1L, "ibanCheckJob");
        JobExecution jobExecution = new JobExecution(jobInstance, 1L, new JobParameters());
        return new StepExecution("ibanCheckWorkerStep", jobExecution);
    }

    @Test
    void write_shouldSaveEntityToRepository() throws Exception {
        when(fileStorageConfig.getReportDirectory()).thenReturn(tempDir);

        StepExecution stepExecution = createStepExecution();
        writer.beforeStep(stepExecution);

        IbanPagopa iban = createIbanPagopa(Costanti.CHECK_OK);
        writer.write(new Chunk<>(iban));

        ArgumentCaptor<IbanPagopaTempEntity> captor = ArgumentCaptor.forClass(IbanPagopaTempEntity.class);
        verify(ibanTempRepository).save(captor.capture());

        IbanPagopaTempEntity saved = captor.getValue();
        assertEquals(COD_INTERMEDIARIO, saved.getBrokerCode());
        assertEquals("01234567890", saved.getCiFiscalCode());
        assertEquals("Comune Test", saved.getCiName());
        assertEquals("IT60X0542811101000000123456", saved.getIban());
        assertEquals("ENABLED", saved.getStatus());
        assertEquals("Conto corrente", saved.getDescription());
        assertEquals("label-test", saved.getLabel());
        assertEquals(Costanti.CHECK_OK, saved.getCheckStato());

        writer.afterStep();
    }

    @Test
    void write_checkOk_shouldIncrementBothCounters() throws Exception {
        when(fileStorageConfig.getReportDirectory()).thenReturn(tempDir);

        StepExecution stepExecution = createStepExecution();
        writer.beforeStep(stepExecution);

        IbanPagopa iban = createIbanPagopa(Costanti.CHECK_OK);
        writer.write(new Chunk<>(iban));

        assertEquals(1, stepExecution.getExecutionContext().getInt(IbanCheckWriter.STATS_SAVED_COUNT));
        assertEquals(1, stepExecution.getExecutionContext().getInt(IbanCheckWriter.STATS_NO_CHANGE_COUNT));

        writer.afterStep();
    }

    @Test
    void write_checkNonCensito_shouldIncrementOnlySavedCount() throws Exception {
        when(fileStorageConfig.getReportDirectory()).thenReturn(tempDir);

        StepExecution stepExecution = createStepExecution();
        writer.beforeStep(stepExecution);

        IbanPagopa iban = createIbanPagopa(Costanti.CHECK_NON_CENSITO);
        writer.write(new Chunk<>(iban));

        assertEquals(1, stepExecution.getExecutionContext().getInt(IbanCheckWriter.STATS_SAVED_COUNT));
        assertEquals(0, stepExecution.getExecutionContext().getInt(IbanCheckWriter.STATS_NO_CHANGE_COUNT));

        writer.afterStep();
    }

    @Test
    void write_multipleItems_shouldSaveAllAndAccumulateStats() throws Exception {
        when(fileStorageConfig.getReportDirectory()).thenReturn(tempDir);

        StepExecution stepExecution = createStepExecution();
        writer.beforeStep(stepExecution);

        IbanPagopa iban1 = createIbanPagopa(Costanti.CHECK_OK);
        IbanPagopa iban2 = createIbanPagopa(Costanti.CHECK_INFO_DIVERSE);
        IbanPagopa iban3 = createIbanPagopa(Costanti.CHECK_OK);
        writer.write(new Chunk<>(iban1, iban2, iban3));

        verify(ibanTempRepository, times(3)).save(any(IbanPagopaTempEntity.class));
        assertEquals(3, stepExecution.getExecutionContext().getInt(IbanCheckWriter.STATS_SAVED_COUNT));
        assertEquals(2, stepExecution.getExecutionContext().getInt(IbanCheckWriter.STATS_NO_CHANGE_COUNT));

        writer.afterStep();
    }

    // ============ Report CSV ============

    /** Il nome del file contiene un timestamp, quindi va cercato nella directory di report. */
    private Path reportFile() throws IOException {
        try (Stream<Path> files = Files.list(tempDir)) {
            return files
                    .filter(f -> f.getFileName().toString().startsWith("reportCheckIban-" + COD_INTERMEDIARIO + "-"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Report CSV non generato in " + tempDir));
        }
    }

    @Test
    void write_multipleItems_shouldWriteOneCsvLinePerItem() throws Exception {
        when(fileStorageConfig.getReportDirectory()).thenReturn(tempDir);

        writer.beforeStep(createStepExecution());
        writer.write(new Chunk<>(
                createIbanPagopa(Costanti.CHECK_OK),
                createIbanPagopa(Costanti.CHECK_NON_CENSITO),
                createIbanPagopa(Costanti.CHECK_OK)));
        writer.afterStep();

        // Ogni record deve stare su una riga propria: senza terminatore di riga i
        // record finivano concatenati su un'unica riga e il report era inutilizzabile.
        assertEquals(3, Files.readAllLines(reportFile(), StandardCharsets.UTF_8).size());

        // E il file deve restare un CSV valido: tre record, ognuno con tutte le colonne.
        try (CSVReader reader = new CSVReader(Files.newBufferedReader(reportFile(), StandardCharsets.UTF_8))) {
            List<String[]> record = reader.readAll();
            assertEquals(3, record.size());
            for (String[] colonne : record) {
                assertEquals(IbanCheckWriter.OUTPUT_HEADERS.size(), colonne.length,
                        "numero di colonne inatteso: " + String.join("|", colonne));
            }
        }
    }

    @Test
    void write_singleItem_shouldTerminateTheOnlyLine() throws Exception {
        when(fileStorageConfig.getReportDirectory()).thenReturn(tempDir);

        writer.beforeStep(createStepExecution());
        writer.write(new Chunk<>(createIbanPagopa(Costanti.CHECK_OK)));
        writer.afterStep();

        // Anche l'ultima riga e' terminata: un file che non finisce con newline fa
        // inglobare l'ultimo record a chi concatena piu' report.
        String contenuto = Files.readString(reportFile(), StandardCharsets.UTF_8);
        assertTrue(contenuto.endsWith("\n"), "il file non termina con un newline");
        assertEquals(1, Files.readAllLines(reportFile(), StandardCharsets.UTF_8).size());
    }

    @Test
    void write_nameWithAccents_shouldBeWrittenAsUtf8() throws Exception {
        when(fileStorageConfig.getReportDirectory()).thenReturn(tempDir);

        writer.beforeStep(createStepExecution());
        writer.write(new Chunk<>(createIbanPagopa(Costanti.CHECK_OK, "Citt\u00e0 di Sant'Ang\u00e8lo")));
        writer.afterStep();

        // Le denominazioni che arrivano da pagoPA contengono accenti: la codifica del
        // report non deve dipendere dal default della piattaforma.
        String contenuto = Files.readString(reportFile(), StandardCharsets.UTF_8);
        assertTrue(contenuto.contains("Citt\u00e0 di Sant'Ang\u00e8lo"), "denominazione accentata non leggibile: " + contenuto);
    }
}
