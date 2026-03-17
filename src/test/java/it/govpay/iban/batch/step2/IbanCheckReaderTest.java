package it.govpay.iban.batch.step2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import it.govpay.iban.batch.dto.IbanPagopa;
import it.govpay.iban.batch.service.IbanPagopaApiService;

@ExtendWith(MockitoExtension.class)
class IbanCheckReaderTest {

    @Mock
    private IbanPagopaApiService ibanPagopaApiService;

    private IbanCheckReader reader;

    private static final String BROKER_CODE = "12345678901";

    @BeforeEach
    void setUp() {
        reader = new IbanCheckReader(ibanPagopaApiService);
        ReflectionTestUtils.setField(reader, "brokerCode", BROKER_CODE);
    }

    private IbanPagopa createIbanPagopa(String iban) {
        return IbanPagopa.builder()
                .codIntermediario(BROKER_CODE)
                .iban(iban)
                .fiscalCode("01234567890")
                .status("ENABLED")
                .validityDate(OffsetDateTime.now())
                .build();
    }

    @Test
    void firstRead_shouldCallServiceAndReturnFirstIban() {
        List<IbanPagopa> ibans = new ArrayList<>(List.of(
                createIbanPagopa("IT60X0542811101000000123456"),
                createIbanPagopa("IT60X0542811101000000789012")));
        when(ibanPagopaApiService.getAllIbans(BROKER_CODE)).thenReturn(ibans);

        IbanPagopa result = reader.read();

        assertEquals("IT60X0542811101000000123456", result.getIban());
        verify(ibanPagopaApiService, times(1)).getAllIbans(BROKER_CODE);
    }

    @Test
    void subsequentReads_shouldReturnFromListWithoutCallingService() {
        List<IbanPagopa> ibans = new ArrayList<>(List.of(
                createIbanPagopa("IT60X0542811101000000123456"),
                createIbanPagopa("IT60X0542811101000000789012")));
        when(ibanPagopaApiService.getAllIbans(BROKER_CODE)).thenReturn(ibans);

        reader.read(); // first
        IbanPagopa second = reader.read();

        assertEquals("IT60X0542811101000000789012", second.getIban());
        verify(ibanPagopaApiService, times(1)).getAllIbans(BROKER_CODE);
    }

    @Test
    void read_afterListExhausted_shouldReturnNull() {
        List<IbanPagopa> ibans = new ArrayList<>(List.of(createIbanPagopa("IT60X0542811101000000123456")));
        when(ibanPagopaApiService.getAllIbans(BROKER_CODE)).thenReturn(ibans);

        reader.read(); // first and only
        IbanPagopa result = reader.read();

        assertNull(result);
    }

    @Test
    void read_emptyListFromService_shouldReturnNull() {
        when(ibanPagopaApiService.getAllIbans(BROKER_CODE)).thenReturn(new ArrayList<>());

        IbanPagopa result = reader.read();

        assertNull(result);
    }
}
