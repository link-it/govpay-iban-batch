package it.govpay.iban.batch.config;

import java.nio.file.Path;
import java.util.List;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Data
public class FileStorageConfig {

    @Value("${govpay.report.dir}")
    private Path reportDirectory;

    @Value("${govpay.report.invia_mail:false}")
    private boolean inviaMail;

    @Value("${govpay.report.oggetto:Report Check IBAN}")
    private String oggetto;

    @Value("${govpay.report.messaggio:In allegato il report di verifica IBAN.}")
    private String messaggio;

    @Value("${govpay.report.destinatario:}")
    private List<String> destinatario;

}
