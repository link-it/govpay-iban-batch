package it.govpay.iban.batch.step3;

import java.util.List;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import it.govpay.iban.batch.dto.EnteCreditorePagopa;
import it.govpay.iban.batch.service.EntiCreditoriApiService;
import lombok.extern.slf4j.Slf4j;

/**
 * Reader for enabled intermediari to fetch the Enti Creditori registry from pagoPA.
 */
@Component
@StepScope
@Slf4j
public class EcSyncReader implements ItemReader<EnteCreditorePagopa>, StepExecutionListener {

    private final EntiCreditoriApiService entiCreditoriApiService;

    @Value("#{stepExecutionContext['codIntermediario']}")
    private String brokerCode;

    private List<EnteCreditorePagopa> enti = null;

    public EcSyncReader(EntiCreditoriApiService entiCreditoriApiService) {
        this.entiCreditoriApiService = entiCreditoriApiService;
    }

    @Override
    public EnteCreditorePagopa read() {
        if (enti == null) {
            enti = entiCreditoriApiService.getAllEntiCreditori(brokerCode);
        }
        if (enti != null && !enti.isEmpty()) {
            return enti.remove(0);
        }

        log.debug("Nessun altro ente creditore da processare (thread: {})", Thread.currentThread().getName());
        return null; // End of data
    }
}
