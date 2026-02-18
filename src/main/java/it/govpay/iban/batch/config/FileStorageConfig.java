package it.govpay.iban.batch.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
@Data
public class FileStorageConfig {

    @Value("${govpay.report.dir}")
    private Path reportDirectory;

}
