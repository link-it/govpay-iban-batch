package it.govpay.iban.batch.dto;

import java.util.Locale;

/**
 * Stato di un IBAN su pagoPA, ricavato dal campo {@code status} della risposta
 * di {@code GET /brokers/{brokerCode}/ibans}.
 * <p>
 * La specifica OpenAPI non definisce i valori ammessi (riporta solo l'esempio
 * {@code ENABLED}). Il backoffice pagoPA li calcola all'esportazione dalla data di
 * attivazione: {@code ATTIVO} se {@code validityDate} e' passata, altrimenti
 * {@code DISATTIVO} (pagopa-selfcare-ms-backoffice-backend,
 * {@code ConvertIbanDetailsToIbanEntity}). {@code ENABLED} e {@code DISABLED}
 * restano riconosciuti per compatibilita' con l'esempio della specifica.
 * Qualsiasi altro valore e' {@link #SCONOSCIUTO}.
 */
public enum StatoIbanPagopa {

    ATTIVO,
    DISATTIVO,
    SCONOSCIUTO;

    public static StatoIbanPagopa da(String status) {
        if (status == null) {
            return SCONOSCIUTO;
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "ATTIVO", "ENABLED" -> ATTIVO;
            case "DISATTIVO", "DISABLED" -> DISATTIVO;
            default -> SCONOSCIUTO;
        };
    }
}
