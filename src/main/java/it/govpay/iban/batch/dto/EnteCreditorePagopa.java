package it.govpay.iban.batch.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Anagrafica di un Ente Creditore restituita da pagoPA
 * (getBrokerInstitutions), prima della persistenza in cache.
 */
@Data
@Builder
public class EnteCreditorePagopa {

    private String codIntermediario;
    private String taxCode;
    private String companyName;
    private String stationId;
    private String auxDigit;
    private String segregationCode;
    private String cbillCode;
    private String checkStato;
    private String checkMotivo;
}
