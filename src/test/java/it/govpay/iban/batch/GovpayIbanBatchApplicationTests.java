package it.govpay.iban.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration tests for GovpayIbanBatchApplication
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.batch.job.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class GovpayIbanBatchApplicationTests {

    @Test
    void contextLoads() {
        // Test that the Spring context loads successfully
    }

    @Autowired
    private DataSource dataSource;

    /**
     * #40: il pool usa READ_COMMITTED come il core GovPay, non il default del database.
     */
    @Test
    void isolamentoReadCommitted() throws SQLException {
        assertEquals("TRANSACTION_READ_COMMITTED", dataSource.unwrap(HikariDataSource.class).getTransactionIsolation());
        try (Connection connection = dataSource.getConnection()) {
            assertEquals(Connection.TRANSACTION_READ_COMMITTED, connection.getTransactionIsolation());
        }
    }
}
