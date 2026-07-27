package it.govpay.iban.batch.tasklet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

import it.govpay.iban.batch.repository.PagopaIbanCheckRepository;

@ExtendWith(MockitoExtension.class)
class CleanupPagopaIbanCheckTaskletTest {

    @Mock
    private PagopaIbanCheckRepository pagopaIbanCheckRepository;

    @Mock
    private StepContribution stepContribution;

    @Mock
    private ChunkContext chunkContext;

    private CleanupPagopaIbanCheckTasklet tasklet;

    @BeforeEach
    void setUp() {
        tasklet = new CleanupPagopaIbanCheckTasklet(pagopaIbanCheckRepository);
    }

    @Test
    void execute_shouldDeleteAllRecordsAndReturnFinished() {
        when(pagopaIbanCheckRepository.count()).thenReturn(42L);

        RepeatStatus result = tasklet.execute(stepContribution, chunkContext);

        assertEquals(RepeatStatus.FINISHED, result);
        verify(pagopaIbanCheckRepository).count();
        verify(pagopaIbanCheckRepository).deleteAllRecords();
    }

    @Test
    void execute_withNoRecords_shouldStillCallDeleteAndReturnFinished() {
        when(pagopaIbanCheckRepository.count()).thenReturn(0L);

        RepeatStatus result = tasklet.execute(stepContribution, chunkContext);

        assertEquals(RepeatStatus.FINISHED, result);
        verify(pagopaIbanCheckRepository).deleteAllRecords();
    }
}
