package it.govpay.iban.batch.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for batch processing
 */
@Configuration
@ConfigurationProperties(prefix = "govpay.batch")
@Data
public class BatchProperties {

    /**
     * Thread pool size for parallel processing (Step 2)
     */
    private int threadPoolSize = 5;

    /**     
     * Chunk size for Step 2 - Check IBAN
     */     
    private int checkIbanChunkSize = 1;

    /**
     * Enable/disable automatic scheduling
     */
    private boolean enabled = true;

    /**
     * Number of retries for failed API calls
     */
    private int maxRetries = 3;

    /**
     * Page size for paginated requests to pagoPA API
     */
    private int pageSize = 1000;
}
