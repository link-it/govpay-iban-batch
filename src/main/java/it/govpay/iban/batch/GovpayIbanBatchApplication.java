package it.govpay.iban.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for GovPay IBAN Batch
 */
@SpringBootApplication(scanBasePackages = {"it.govpay.iban.batch", "it.govpay.common.client"})
@EntityScan(basePackages = {"it.govpay.iban.batch", "it.govpay.common.client", "it.govpay.common.entity"})
@EnableJpaRepositories(basePackages = {"it.govpay.iban.batch"})
@EnableScheduling
public class GovpayIbanBatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(GovpayIbanBatchApplication.class, args);
    }
}
