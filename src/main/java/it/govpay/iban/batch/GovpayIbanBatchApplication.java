package it.govpay.iban.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for GovPay IBAN Batch
 */
@SpringBootApplication
@EnableScheduling
public class GovpayIbanBatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(GovpayIbanBatchApplication.class, args);
    }
}
