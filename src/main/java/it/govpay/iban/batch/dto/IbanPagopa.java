package it.govpay.iban.batch.dto;

import java.time.OffsetDateTime;

import lombok.Builder;
import lombok.Data;

/**
 * Context information for processing a single domain
 */
@Data
@Builder
public class IbanPagopa {
    private String codIntermediario;
    private String fiscalCode;
    private String name;
    private String iban;
    private String status;
    private OffsetDateTime validityDate;
    private String description;
    private String label;
    private String checkStato;
    private String checkMotivo;
}
