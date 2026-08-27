package it.govpay.iban.batch.step2;

import it.govpay.iban.batch.Costanti;
import it.govpay.iban.batch.dto.IbanPagopa;
import it.govpay.iban.batch.entity.IbanAccreditoEntity;
import it.govpay.iban.batch.repository.IbanAccreditoRepository;
import lombok.extern.slf4j.Slf4j;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.StringJoiner;

import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * Processor to check iban data
 */
@Component
@Slf4j
public class IbanCheckProcessor implements ItemProcessor<IbanPagopa, IbanPagopa> {

	private final IbanAccreditoRepository ibanAccreditoRepository;
	private final ZoneId applicationZoneId;

	public IbanCheckProcessor(IbanAccreditoRepository ibanAccreditoRepository, ZoneId applicationZoneId) {
		this.ibanAccreditoRepository = ibanAccreditoRepository;
		this.applicationZoneId = applicationZoneId;
    }

	private void checkUpdated(IbanAccreditoEntity storedIban, IbanPagopa iban) {
		StringJoiner stringJoiner = new StringJoiner(",");
		boolean storedEnable = storedIban.getAbilitato() != null && storedIban.getAbilitato().booleanValue();
		boolean ibanEnable   = iban.getStatus().equalsIgnoreCase("ENABLED") &&
							   (iban.getValidityDate() == null || !iban.getValidityDate().isAfter(OffsetDateTime.now(applicationZoneId)));
		String checkStato = Costanti.CHECK_OK;
		if (storedEnable != ibanEnable) {
			checkStato = Costanti.CHECK_INFO_DIVERSE;
			stringJoiner.add("Abilitazione");
		} else
		if (!ibanEnable) {
			checkStato = Costanti.CHECK_NON_ATTIVO;
			stringJoiner.add("Abilitazione");
		}
		if (!java.util.Objects.equals(storedIban.getDescrizione(), iban.getDescription())) {
			checkStato = Costanti.CHECK_INFO_DIVERSE;
			stringJoiner.add("Descrizione");
		}
		if (!java.util.Objects.equals(storedIban.getIntestatario(), iban.getName())) {
			checkStato = Costanti.CHECK_INFO_DIVERSE;
			stringJoiner.add("Intestatario");
		}
		iban.setCheckStato(checkStato);
		if (!stringJoiner.toString().isEmpty())
			iban.setCheckMotivo("Presenza di differenze: " + stringJoiner.toString());
	}

    @Override
    public IbanPagopa process(IbanPagopa iban) throws Exception {
        log.info("Processing intermediario: {} with iban: {}",
                 iban.getCodIntermediario(), iban.getIban());
        List<IbanAccreditoEntity> storedIbans = ibanAccreditoRepository.findByCodIbanAndDominioCodDominio(iban.getIban(), iban.getFiscalCode());
        if (storedIbans == null || storedIbans.isEmpty()) {
        	iban.setCheckStato(Costanti.CHECK_NON_CENSITO);
        } else {
	        IbanAccreditoEntity storedIban = storedIbans.get(0);
	        checkUpdated(storedIban, iban);
        }
        return iban;
    }
}
