package it.govpay.iban.batch.tasklet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.repeat.RepeatStatus;

import it.govpay.iban.batch.repository.IbanPagopaTempRepository;

@ExtendWith(MockitoExtension.class)
class CleanupIbanTempTaskletTest {

    @Mock
    private IbanPagopaTempRepository ibanTempRepository;

    @Mock
    private StepContribution stepContribution;

    @Mock
    private ChunkContext chunkContext;

    private CleanupIbanTempTasklet tasklet;

    @BeforeEach
    void setUp() {
        tasklet = new CleanupIbanTempTasklet(ibanTempRepository);
    }

    @Test
    void execute_shouldDeleteAllRecordsAndReturnFinished() {
        when(ibanTempRepository.count()).thenReturn(42L);

        RepeatStatus result = tasklet.execute(stepContribution, chunkContext);

        assertEquals(RepeatStatus.FINISHED, result);
        verify(ibanTempRepository).count();
        verify(ibanTempRepository).deleteAllRecords();
    }

    @Test
    void execute_withNoRecords_shouldStillCallDeleteAndReturnFinished() {
        when(ibanTempRepository.count()).thenReturn(0L);

        RepeatStatus result = tasklet.execute(stepContribution, chunkContext);

        assertEquals(RepeatStatus.FINISHED, result);
        verify(ibanTempRepository).deleteAllRecords();
    }
}
