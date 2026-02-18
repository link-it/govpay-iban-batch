package it.govpay.iban.batch.step2;

import java.util.List;

import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import it.govpay.iban.batch.dto.IbanPagopa;
import it.govpay.iban.batch.service.IbanPagopaApiService;
import lombok.extern.slf4j.Slf4j;

/**
 * Reader for enabled intermediari to fetch IBAN from PagoPA.
 */
@Component
@StepScope
@Slf4j
public class IbanCheckReader implements ItemReader<IbanPagopa>, StepExecutionListener {

	private final IbanPagopaApiService ibanPagopaApiService;
	
    @Value("#{stepExecutionContext['codIntermediario']}")
    private String brokerCode;

    @Value("#{stepExecutionContext['partitionNumber']}")
    private Integer partitionNumber;

    @Value("#{stepExecutionContext['totalPartitions']}")
    private Integer totalPartitions;

	private List<IbanPagopa> ibans = null;;

    public IbanCheckReader(IbanPagopaApiService ibanPagopaApiService) {
    	this.ibanPagopaApiService = ibanPagopaApiService;
    }

    @Override
    public IbanPagopa read() {
    	if ( ibans == null ) {
    		ibans  = ibanPagopaApiService.getAllIbans(brokerCode);
    	}
    	if ( ibans != null && !ibans.isEmpty() )
    		return ibans.remove(0);

        log.debug("Nessun altro iban da processare (thread: {})", Thread.currentThread().getName());
        return null; // End of data
    }

}
