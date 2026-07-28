package it.govpay.iban.batch.step3;

import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

import it.govpay.common.entity.DominioEntity;
import it.govpay.common.entity.StazioneEntity;
import it.govpay.common.repository.DominioRepository;
import it.govpay.iban.batch.Costanti;
import it.govpay.iban.batch.dto.EnteCreditorePagopa;
import lombok.extern.slf4j.Slf4j;

/**
 * Processor to check Ente Creditore data from pagoPA against the Domini
 * already censiti on GovPay (mirror of {@code IbanCheckProcessor}).
 */
@Component
@Slf4j
public class EcCheckProcessor implements ItemProcessor<EnteCreditorePagopa, EnteCreditorePagopa> {

    private final DominioRepository dominioRepository;

    public EcCheckProcessor(DominioRepository dominioRepository) {
        this.dominioRepository = dominioRepository;
    }

    private void checkUpdated(DominioEntity dominio, EnteCreditorePagopa ente) {
        StringJoiner stringJoiner = new StringJoiner(",");
        String checkStato = Costanti.CHECK_OK;

        if (!Objects.equals(dominio.getRagioneSociale(), ente.getCompanyName())) {
            checkStato = Costanti.CHECK_INFO_DIVERSE;
            stringJoiner.add("RagioneSociale");
        }
        // Confronto numerico (non su stringa): il valore pagoPA puo' avere zero-padding
        // (es. "01") che non corrisponde alla rappresentazione decimale dell'Integer di GovPay.
        if (!Objects.equals(dominio.getAuxDigit(), parseIntOrNull(ente.getAuxDigit()))) {
            checkStato = Costanti.CHECK_INFO_DIVERSE;
            stringJoiner.add("AuxDigit");
        }
        if (!Objects.equals(dominio.getSegregationCode(), parseIntOrNull(ente.getSegregationCode()))) {
            checkStato = Costanti.CHECK_INFO_DIVERSE;
            stringJoiner.add("SegregationCode");
        }
        if (!Objects.equals(dominio.getCbill(), ente.getCbillCode())) {
            checkStato = Costanti.CHECK_INFO_DIVERSE;
            stringJoiner.add("Cbill");
        }
        String codStazione = Optional.ofNullable(dominio.getStazione()).map(StazioneEntity::getCodStazione).orElse(null);
        if (!Objects.equals(codStazione, ente.getStationId())) {
            checkStato = Costanti.CHECK_INFO_DIVERSE;
            stringJoiner.add("StationId");
        }

        ente.setCheckStato(checkStato);
        if (!stringJoiner.toString().isEmpty()) {
            ente.setCheckMotivo("Presenza di differenze: " + stringJoiner);
        }
    }

    private static Integer parseIntOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            log.warn("Valore numerico non valido da pagoPA: '{}'", value);
            return null;
        }
    }

    @Override
    public EnteCreditorePagopa process(EnteCreditorePagopa ente) throws Exception {
        log.info("Processing intermediario: {} with ente creditore: {}",
                 ente.getCodIntermediario(), ente.getTaxCode());
        Optional<DominioEntity> storedDominio = dominioRepository.findByCodDominio(ente.getTaxCode());
        if (storedDominio.isEmpty()) {
            ente.setCheckStato(Costanti.CHECK_NON_CENSITO);
        } else {
            checkUpdated(storedDominio.get(), ente);
        }
        return ente;
    }
}
