package it.govpay.iban.batch.step2;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.annotation.AfterStep;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import it.govpay.iban.batch.Costanti;
import it.govpay.iban.batch.config.FileStorageConfig;
import it.govpay.iban.batch.dto.IbanPagopa;
import it.govpay.iban.batch.entity.IbanCacheEntity;
import it.govpay.iban.batch.entity.PagopaIbanCheckEntity;
import it.govpay.iban.batch.repository.IbanCacheRepository;
import it.govpay.iban.batch.repository.PagopaIbanCheckRepository;
import it.govpay.iban.batch.utils.CsvRowGenerator;
import lombok.extern.slf4j.Slf4j;

/**
 * Writer to save IBAN pagoPA to PAGOPA_IBAN_CHECK table, upsert pagopa_iban_cache
 * (censimento assistito, issue #34) and generate the report file.
 */
@Component
@StepScope
@Slf4j
public class IbanCheckWriter implements ItemWriter<IbanPagopa> {
    public static final List<String> OUTPUT_HEADERS = List.of("codFiscale", "name", "iban", "status", "validityDate", "descrizione", "label", "checkStato", "checkMotivo");

    public static final String STATS_SAVED_COUNT = "ibansSavedCount";
    public static final String STATS_NO_CHANGE_COUNT = "ibansNoChangeCount";

    private final PagopaIbanCheckRepository pagopaIbanCheckRepository;
    private final IbanCacheRepository ibanCacheRepository;
    private final FileStorageConfig fileStorageConfig;
    private final ZoneId applicationZoneId;
    private final CsvRowGenerator csvRowGenerator = new CsvRowGenerator();
    private StepExecution stepExecution;
    private BufferedOutputStream reportOS;

    @Value("#{stepExecutionContext['codIntermediario']}")
    private String codIntermediario;

    public IbanCheckWriter(PagopaIbanCheckRepository pagopaIbanCheckRepository,
                           IbanCacheRepository ibanCacheRepository,
                           FileStorageConfig fileStorageConfig,
                           ZoneId applicationZoneId) {
        this.pagopaIbanCheckRepository = pagopaIbanCheckRepository;
        this.ibanCacheRepository = ibanCacheRepository;
        this.fileStorageConfig = fileStorageConfig;
        this.applicationZoneId = applicationZoneId;
    }

    @BeforeStep
    public void beforeStep(StepExecution stepExecution) {
        this.stepExecution = stepExecution;
        // Inizializza i contatori nel contesto
        stepExecution.getExecutionContext().putInt(STATS_SAVED_COUNT, 0);
        stepExecution.getExecutionContext().putInt(STATS_NO_CHANGE_COUNT, 0);
        
        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = "reportCheckIban-" + codIntermediario + "-" + timestamp + ".csv";
        Path reportDir = fileStorageConfig.getReportDirectory();
        // Costruisce il percorso completo del file
        Path filePath = reportDir.resolve(fileName);
        try {
			reportOS = new BufferedOutputStream(new FileOutputStream(filePath.toFile()));
		} catch (FileNotFoundException e) {
			log.error("Cannot open report file", e);
		}
    }

    @AfterStep
    public void afterStep() {
        if (reportOS != null) {
            try {
                reportOS.flush();
                reportOS.close();
            } catch (java.io.IOException e) {
                log.error("Errore durante la chiusura del file di report", e);
            }
        }
    }

    @Override
    @Transactional
    public void write(Chunk<? extends IbanPagopa> chunk) {
        for (IbanPagopa ibanData : chunk) {
            log.info("Scrittura di {} iban per l'intermediario {}", ibanData.getIban(), ibanData.getCodIntermediario());

            IbanProcessingStats stats = new IbanProcessingStats();
            processIban(ibanData, stats);

            // Aggiorna le statistiche nel contesto dello step
            updateStepContextStats(stats);

            log.info("Intermediario {}: salvati {} IBAN, senza modifiche {} già in IBAN_ACCREDITO",
                     ibanData.getCodIntermediario(), stats.savedCount, stats.alreadyNoChangeCount);
        }
    }

    private void updateStepContextStats(IbanProcessingStats stats) {
        if (stepExecution != null) {
            int currentSaved = stepExecution.getExecutionContext().getInt(STATS_SAVED_COUNT, 0);
            int currentNoChange = stepExecution.getExecutionContext().getInt(STATS_NO_CHANGE_COUNT, 0);

            stepExecution.getExecutionContext().putInt(STATS_SAVED_COUNT, currentSaved + stats.savedCount);
            stepExecution.getExecutionContext().putInt(STATS_NO_CHANGE_COUNT, currentNoChange + stats.alreadyNoChangeCount);
        }
    }

    /**
     * Processes a single IBAN and updates statistics.
     *
     * @param iban IBAN data to process
     * @param stats statistics object to update
     */
    private void processIban(IbanPagopa ibanPagopa, IbanProcessingStats stats) {
        // IBAN: inserire in PAGOPA_IBAN_CHECK
        PagopaIbanCheckEntity ibanCheck = PagopaIbanCheckEntity.builder()
            .brokerCode(ibanPagopa.getCodIntermediario())
            .ciFiscalCode(ibanPagopa.getFiscalCode())
            .ciName(ibanPagopa.getName())
            .iban(ibanPagopa.getIban())
            .status(ibanPagopa.getStatus())
            .validityDate(ibanPagopa.getValidityDate())
            .description(ibanPagopa.getDescription())
            .label(ibanPagopa.getLabel())
            .checkStato(ibanPagopa.getCheckStato())
            .checkMotivo(ibanPagopa.getCheckMotivo())
            .build();

        pagopaIbanCheckRepository.save(ibanCheck);
        stats.savedCount++;
        if (ibanPagopa.getCheckStato().equals(Costanti.CHECK_OK))
        	stats.alreadyNoChangeCount++;

        upsertIbanCache(ibanPagopa);

        try {
            Map<String, String> outputData = new HashMap<>();
            outputData.put("codFiscale", ibanPagopa.getFiscalCode());
            outputData.put("name", ibanPagopa.getName());
            outputData.put("iban", ibanPagopa.getIban());
            outputData.put("status", ibanPagopa.getStatus());
            outputData.put("validityDate", ibanPagopa.getValidityDate().toString());
            outputData.put("descrizione", ibanPagopa.getDescription());
            outputData.put("label", ibanPagopa.getLabel());
            outputData.put("checkStato", ibanPagopa.getCheckStato());
            outputData.put("checkMotivo", ibanPagopa.getCheckMotivo());
            log.debug("Output Data: {}", outputData);

            String csvRow = csvRowGenerator.generateCsvRow(outputData, OUTPUT_HEADERS);
            log.info("Generated row: {}", csvRow);
            reportOS.write(csvRow.getBytes());
        } catch (Exception e) {
            log.error("Error producing CSV row from json", e);
        }

    }

    /**
     * Upserts pagopa_iban_cache keyed by (codDominio, iban), for the assisted
     * enrollment lookup (issue #34). "attivo" reflects only pagoPA's status
     * (ENABLED), deliberately ignoring validityDate: its real semantics
     * ("Activation Date" per spec vs. a possible expiration reading) are
     * unconfirmed, so it is not used for any derived state here.
     */
    private void upsertIbanCache(IbanPagopa ibanPagopa) {
        IbanCacheEntity cacheEntity = ibanCacheRepository
                .findByCodDominioAndIban(ibanPagopa.getFiscalCode(), ibanPagopa.getIban())
                .orElseGet(IbanCacheEntity::new);
        cacheEntity.setCodDominio(ibanPagopa.getFiscalCode());
        cacheEntity.setIban(ibanPagopa.getIban());
        cacheEntity.setAttivo("ENABLED".equalsIgnoreCase(ibanPagopa.getStatus()));
        cacheEntity.setDataUltimaVerifica(OffsetDateTime.now(applicationZoneId));
        ibanCacheRepository.save(cacheEntity);
    }

    /**
     * Helper class to track header processing statistics.
     */
    private static class IbanProcessingStats {
        int savedCount = 0;
        int alreadyNoChangeCount = 0;
    }
}
