package it.govpay.iban.batch.tasklet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

import it.govpay.iban.batch.repository.IbanCacheRepository;

@ExtendWith(MockitoExtension.class)
class CleanupStaleIbanCacheTaskletTest {

    @Mock
    private IbanCacheRepository repository;

    @Mock
    private StepContribution stepContribution;

    private static final ZoneId ZONE_ID = ZoneId.of("Europe/Rome");

    private CleanupStaleIbanCacheTasklet tasklet;

    @BeforeEach
    void setUp() {
        tasklet = new CleanupStaleIbanCacheTasklet(repository, ZONE_ID);
    }

    private ChunkContext createChunkContext(LocalDateTime jobStartTime) {
        JobInstance jobInstance = new JobInstance(1L, "ibanCheckJob");
        JobExecution jobExecution = new JobExecution(1L, jobInstance, new JobParameters());
        jobExecution.setStartTime(jobStartTime);
        StepExecution stepExecution = new StepExecution("cleanupStaleIbanCacheStep", jobExecution);
        return new ChunkContext(new StepContext(stepExecution));
    }

    @Test
    void execute_shouldDeleteRowsOlderThanJobStartTime() {
        LocalDateTime startTime = LocalDateTime.of(2026, 7, 1, 2, 0, 0);
        ChunkContext chunkContext = createChunkContext(startTime);
        when(repository.deleteByDataUltimaVerificaBefore(any(OffsetDateTime.class))).thenReturn(3L);

        RepeatStatus result = tasklet.execute(stepContribution, chunkContext);

        assertEquals(RepeatStatus.FINISHED, result);

        OffsetDateTime expected = startTime.atZone(ZONE_ID).toOffsetDateTime();
        verify(repository).deleteByDataUltimaVerificaBefore(expected);
    }

    @Test
    void execute_noStaleRows_shouldStillReturnFinished() {
        ChunkContext chunkContext = createChunkContext(LocalDateTime.now());
        when(repository.deleteByDataUltimaVerificaBefore(any(OffsetDateTime.class))).thenReturn(0L);

        RepeatStatus result = tasklet.execute(stepContribution, chunkContext);

        assertEquals(RepeatStatus.FINISHED, result);
    }
}
