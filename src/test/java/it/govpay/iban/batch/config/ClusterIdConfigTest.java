package it.govpay.iban.batch.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import it.govpay.common.batch.config.ClusterIdPredefinito;
import it.govpay.common.batch.metrics.BatchMetricsNodo;
import it.govpay.common.batch.runner.JobExecutionHelper;
import it.govpay.iban.batch.GovpayIbanBatchApplication;
import it.govpay.iban.batch.gde.mapper.EventoIbanMapper;

/**
 * Verifica che il cluster-id del nodo sia lo stesso per il {@link JobExecutionHelper},
 * per gli eventi del GDE e per il tag {@code nodo} delle metriche (govpay-common#35, #36).
 */
class ClusterIdConfigTest {

    @Nested
    @SpringBootTest(classes = GovpayIbanBatchApplication.class)
    @ActiveProfiles("test")
    class SenzaProperty {

        @Autowired
        private JobExecutionHelper jobExecutionHelper;

        @Autowired
        private EventoIbanMapper eventoIbanMapper;

        @Autowired
        private BatchMetricsNodo batchMetricsNodo;

        @Test
        void usaIlClusterIdPredefinitoDelNodo() {
            String atteso = ClusterIdPredefinito.valore();

            assertThat(jobExecutionHelper.getClusterId()).isEqualTo(atteso);
            assertThat(ReflectionTestUtils.getField(eventoIbanMapper, "clusterId")).isEqualTo(atteso);
            assertThat(batchMetricsNodo.valore()).isEqualTo(atteso);
        }
    }

    @Nested
    @SpringBootTest(classes = GovpayIbanBatchApplication.class)
    @ActiveProfiles("test")
    @TestPropertySource(properties = "govpay.batch.cluster-id=nodo-1")
    class ConProperty {

        @Autowired
        private JobExecutionHelper jobExecutionHelper;

        @Autowired
        private EventoIbanMapper eventoIbanMapper;

        @Autowired
        private BatchMetricsNodo batchMetricsNodo;

        @Test
        void usaIlClusterIdConfigurato() {
            assertThat(jobExecutionHelper.getClusterId()).isEqualTo("nodo-1");
            assertThat(ReflectionTestUtils.getField(eventoIbanMapper, "clusterId")).isEqualTo("nodo-1");
            assertThat(batchMetricsNodo.valore()).isEqualTo("nodo-1");
            assertThat(batchMetricsNodo.fonte()).isEqualTo(BatchMetricsNodo.PROPERTY_CLUSTER_ID);
        }
    }
}
