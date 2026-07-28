package it.govpay.iban.batch.tasklet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

import it.govpay.iban.batch.repository.EcCheckRepository;

@ExtendWith(MockitoExtension.class)
class CleanupEcCheckTaskletTest {

    @Mock
    private EcCheckRepository ecCheckRepository;

    @Mock
    private StepContribution stepContribution;

    @Mock
    private ChunkContext chunkContext;

    private CleanupEcCheckTasklet tasklet;

    @BeforeEach
    void setUp() {
        tasklet = new CleanupEcCheckTasklet(ecCheckRepository);
    }

    @Test
    void execute_shouldDeleteAllRecordsAndReturnFinished() {
        when(ecCheckRepository.count()).thenReturn(42L);

        RepeatStatus result = tasklet.execute(stepContribution, chunkContext);

        assertEquals(RepeatStatus.FINISHED, result);
        verify(ecCheckRepository).count();
        verify(ecCheckRepository).deleteAllRecords();
    }

    @Test
    void execute_withNoRecords_shouldStillCallDeleteAndReturnFinished() {
        when(ecCheckRepository.count()).thenReturn(0L);

        RepeatStatus result = tasklet.execute(stepContribution, chunkContext);

        assertEquals(RepeatStatus.FINISHED, result);
        verify(ecCheckRepository).deleteAllRecords();
    }
}
