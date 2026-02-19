package it.govpay.iban.batch.utils;

import com.opencsv.CSVWriter;
import java.io.StringWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class CsvRowGenerator {

    public String generateCsvRow(Map<String, String> data, List<String> headers) {
        StringWriter stringWriter = new StringWriter();
        try (CSVWriter csvWriter = new CSVWriter(stringWriter)) {
            String[] row = new String[headers.size()];
            for (int i = 0; i < headers.size(); i++) {
                String header = headers.get(i);
                String value = data.getOrDefault(header, "");
                row[i] = value;
            }
            csvWriter.writeNext(row);
        } catch (IOException e) {
            throw new RuntimeException("Fail to generate csv row data", e);
        }
        return stringWriter.toString().trim();
    }


}

