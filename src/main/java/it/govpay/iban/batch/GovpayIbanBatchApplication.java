package it.govpay.iban.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Classe principale di GovPay IBAN Batch.
 * <p>
 * Estende {@link SpringBootServletInitializer} per il deploy del war (profilo
 * Maven {@code war}) su application server; con il fat jar parte da {@link #main}.
 */
@SpringBootApplication(scanBasePackages = {"it.govpay.iban.batch", "it.govpay.common.client"})
@EntityScan(basePackages = {"it.govpay.iban.batch", "it.govpay.common.client", "it.govpay.common.entity"})
@EnableJpaRepositories(basePackages = {"it.govpay.iban.batch"})
@EnableScheduling
public class GovpayIbanBatchApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(GovpayIbanBatchApplication.class, args);
    }
}
