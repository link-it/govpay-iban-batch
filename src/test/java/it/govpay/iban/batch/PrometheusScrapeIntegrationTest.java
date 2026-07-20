package it.govpay.iban.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import it.govpay.common.batch.service.JobConcurrencyService;
import it.govpay.common.entity.IntermediarioEntity;
import it.govpay.common.repository.IntermediarioRepository;
import it.govpay.iban.batch.config.ScheduledJobRunner;
import it.govpay.iban.batch.config.TestScheduledJobRunnerConfig;
import it.govpay.iban.batch.repository.IbanPagopaTempRepository;

/**
 * Verifica end-to-end dell'esposizione delle metriche Prometheus:
 * <ul>
 *   <li>lo scrape {@code GET /actuator/prometheus} risponde in formato
 *       testuale Prometheus, con il tag comune {@code application};</li>
 *   <li>l'esecuzione del job pubblica le metriche standard di Spring Batch
 *       ({@code spring_batch_job}).</li>
 * </ul>
 *
 * <p>Il servizio non ha una porta management separata: essendo l'unico
 * server web presente quello dell'actuator, scrape e health rispondono sulla
 * stessa porta (qui random per il test).
 */
@SpringBootTest(classes = GovpayIbanBatchApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestScheduledJobRunnerConfig.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.batch.job.enabled=false",
        // application.properties imposta initialDelayString=1 (1ms): senza questo
        // override lo @Scheduled reale lancia ibanCheckJob quasi subito, in
        // corsa con la chiamata esplicita del test sullo stesso job.
        "scheduler.initialDelayString=3600000"
})
class PrometheusScrapeIntegrationTest {

    @LocalServerPort
    private int serverPort;

    @Autowired
    private ScheduledJobRunner batchScheduler;

    @MockitoBean
    private JobConcurrencyService jobConcurrencyService = mock(JobConcurrencyService.class);
    @MockitoBean
    private IntermediarioRepository intermediarioRepository = mock(IntermediarioRepository.class);
    @MockitoBean
    private IbanPagopaTempRepository ibanPagopaTempRepository = mock(IbanPagopaTempRepository.class);

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void scrapeReturnsPrometheusFormatWithApplicationTag() throws Exception {
        HttpResponse<String> response = get("/actuator/prometheus");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("text/plain");
        assertThat(response.body()).contains("# TYPE jvm_memory_used_bytes gauge");
        assertThat(response.body()).contains("application=\"govpay-iban-batch\"");
    }

    @Test
    void batchJobExecutionProducesSpringBatchMetrics() throws Exception {
        // Job "a vuoto": nessun intermediario -> nessuna partizione da eseguire.
        // Basta a far girare il job (anche se termina senza item) e a produrre
        // le metriche standard spring_batch_job/step di Micrometer.
        when(jobConcurrencyService.getCurrentRunningJobExecution(any())).thenReturn(null);
        when(intermediarioRepository.findAll()).thenReturn(List.<IntermediarioEntity>of());

        JobExecution execution = batchScheduler.runBatchIbanCheckJob();
        assertThat(execution).isNotNull();

        String scrape = get("/actuator/prometheus").body();
        assertThat(scrape).contains("spring_batch_job_seconds_count");
        assertThat(scrape).contains("spring_batch_job_name=\"ibanCheckJob\"");
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + serverPort + path))
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
