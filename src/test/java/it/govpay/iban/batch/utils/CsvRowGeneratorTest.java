package it.govpay.iban.batch.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CsvRowGeneratorTest {

    private CsvRowGenerator csvRowGenerator;

    @BeforeEach
    void setUp() {
        csvRowGenerator = new CsvRowGenerator();
    }

    @Test
    void generateCsvRow_allValuesPresent_shouldGenerateCorrectCsv() {
        Map<String, String> data = new HashMap<>();
        data.put("col1", "value1");
        data.put("col2", "value2");
        data.put("col3", "value3");
        List<String> headers = List.of("col1", "col2", "col3");

        String result = csvRowGenerator.generateCsvRow(data, headers);

        assertEquals("\"value1\",\"value2\",\"value3\"", result);
    }

    @Test
    void generateCsvRow_missingValues_shouldUseEmptyString() {
        Map<String, String> data = new HashMap<>();
        data.put("col1", "value1");
        // col2 missing
        data.put("col3", "value3");
        List<String> headers = List.of("col1", "col2", "col3");

        String result = csvRowGenerator.generateCsvRow(data, headers);

        assertEquals("\"value1\",\"\",\"value3\"", result);
    }

    @Test
    void generateCsvRow_headersOrder_shouldMatchColumnOrder() {
        Map<String, String> data = new HashMap<>();
        data.put("b", "second");
        data.put("a", "first");
        data.put("c", "third");
        List<String> headers = List.of("a", "b", "c");

        String result = csvRowGenerator.generateCsvRow(data, headers);

        assertEquals("\"first\",\"second\",\"third\"", result);
    }
}
