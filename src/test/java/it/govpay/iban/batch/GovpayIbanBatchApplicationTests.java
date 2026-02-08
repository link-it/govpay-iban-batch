package it.govpay.iban.batch;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests for GovpayIbanBatchApplication
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.batch.job.enabled=false"
})
class GovpayIbanBatchApplicationTests {

    @Test
    void contextLoads() {
        // Test that the Spring context loads successfully
    }
}
