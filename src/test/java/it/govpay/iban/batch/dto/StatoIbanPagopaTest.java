package it.govpay.iban.batch.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StatoIbanPagopaTest {

    /** ATTIVO e' il valore che pagoPA restituisce davvero; ENABLED e' l'esempio della specifica. */
    @ParameterizedTest
    @ValueSource(strings = {"ATTIVO", "attivo", " ATTIVO ", "ENABLED", "enabled"})
    void da_valoriAttivi(String status) {
        assertEquals(StatoIbanPagopa.ATTIVO, StatoIbanPagopa.da(status));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DISATTIVO", "disattivo", "DISABLED", "disabled"})
    void da_valoriNonAttivi(String status) {
        assertEquals(StatoIbanPagopa.DISATTIVO, StatoIbanPagopa.da(status));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "SOSPESO", "ATTIVATO"})
    void da_valoriSconosciuti(String status) {
        assertEquals(StatoIbanPagopa.SCONOSCIUTO, StatoIbanPagopa.da(status));
    }

    @Test
    void da_null_isSconosciuto() {
        assertEquals(StatoIbanPagopa.SCONOSCIUTO, StatoIbanPagopa.da(null));
    }
}
