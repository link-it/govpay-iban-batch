package it.govpay.iban.batch.gde.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;

import it.govpay.gde.client.beans.CategoriaEvento;
import it.govpay.gde.client.beans.ComponenteEvento;
import it.govpay.gde.client.beans.EsitoEvento;
import it.govpay.gde.client.beans.Header;
import it.govpay.gde.client.beans.NuovoEvento;
import it.govpay.gde.client.beans.RuoloEvento;

@ExtendWith(MockitoExtension.class)
class EventoIbanMapperTest {

    private EventoIbanMapper mapper;

    private static final String CLUSTER_ID = "test-cluster";
    private static final String TIPO_EVENTO = "getAllIbans";
    private static final String TRANSACTION_ID = "txn-123";

    private OffsetDateTime dataStart;
    private OffsetDateTime dataEnd;

    @BeforeEach
    void setUp() {
        mapper = new EventoIbanMapper();
        ReflectionTestUtils.setField(mapper, "clusterId", CLUSTER_ID);
        dataStart = OffsetDateTime.of(2025, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);
        dataEnd = OffsetDateTime.of(2025, 1, 1, 10, 0, 5, 0, ZoneOffset.UTC);
    }

    @Test
    void createEvento_shouldSetBaseFields() {
        NuovoEvento evento = mapper.createEvento(TIPO_EVENTO, TRANSACTION_ID, dataStart, dataEnd);

        assertEquals(CategoriaEvento.INTERFACCIA, evento.getCategoriaEvento());
        assertEquals(CLUSTER_ID, evento.getClusterId());
        assertEquals(RuoloEvento.CLIENT, evento.getRuolo());
        assertEquals(ComponenteEvento.API_PAGOPA, evento.getComponente());
        assertEquals(TIPO_EVENTO, evento.getTipoEvento());
        assertEquals(TRANSACTION_ID, evento.getTransactionId());
        assertEquals(dataStart, evento.getDataEvento());
        assertEquals(5000L, evento.getDurataEvento());
    }

    @Test
    void createEventoOk_shouldSetEsitoOk() {
        NuovoEvento evento = mapper.createEventoOk(TIPO_EVENTO, TRANSACTION_ID, dataStart, dataEnd);

        assertEquals(EsitoEvento.OK, evento.getEsito());
        assertEquals(TIPO_EVENTO, evento.getTipoEvento());
    }

    @Test
    void createEventoKo_withHttpClientErrorException_shouldSetEsitoKo() {
        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, "body not found".getBytes(), null);

        NuovoEvento evento = mapper.createEventoKo(TIPO_EVENTO, TRANSACTION_ID, dataStart, dataEnd, null, exception);

        assertEquals(EsitoEvento.KO, evento.getEsito());
        assertEquals("404", evento.getSottotipoEsito());
        assertEquals("body not found", evento.getDettaglioEsito());
    }

    @Test
    void createEventoKo_withHttpServerErrorException_shouldSetEsitoFail() {
        HttpServerErrorException exception = HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", HttpHeaders.EMPTY, "server error".getBytes(), null);

        NuovoEvento evento = mapper.createEventoKo(TIPO_EVENTO, TRANSACTION_ID, dataStart, dataEnd, null, exception);

        assertEquals(EsitoEvento.FAIL, evento.getEsito());
        assertEquals("500", evento.getSottotipoEsito());
    }

    @Test
    void createEventoKo_withGenericRestClientException_shouldSetEsitoFail() {
        RestClientException exception = new RestClientException("Connection refused");

        NuovoEvento evento = mapper.createEventoKo(TIPO_EVENTO, TRANSACTION_ID, dataStart, dataEnd, null, exception);

        assertEquals(EsitoEvento.FAIL, evento.getEsito());
        assertEquals("500", evento.getSottotipoEsito());
        assertEquals("Connection refused", evento.getDettaglioEsito());
    }

    @Test
    void createEventoKo_withResponseEntityAndNoException_shouldSetEsitoBasedOnStatus() {
        ResponseEntity<String> responseEntity = ResponseEntity.status(HttpStatus.BAD_REQUEST).body("bad request");

        NuovoEvento evento = mapper.createEventoKo(TIPO_EVENTO, TRANSACTION_ID, dataStart, dataEnd, responseEntity, null);

        assertEquals(EsitoEvento.KO, evento.getEsito());
        assertEquals("400", evento.getSottotipoEsito());
        assertEquals("Bad Request", evento.getDettaglioEsito());
    }

    @Test
    void createEventoKo_withResponseEntity5xxAndNoException_shouldSetEsitoFail() {
        ResponseEntity<String> responseEntity = ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("gateway error");

        NuovoEvento evento = mapper.createEventoKo(TIPO_EVENTO, TRANSACTION_ID, dataStart, dataEnd, responseEntity, null);

        assertEquals(EsitoEvento.FAIL, evento.getEsito());
        assertEquals("502", evento.getSottotipoEsito());
    }

    @Test
    void setParametriRichiesta_shouldSetUrlMethodHeaders() {
        NuovoEvento evento = mapper.createEvento(TIPO_EVENTO, TRANSACTION_ID, dataStart, dataEnd);
        List<Header> headers = new ArrayList<>();
        Header header = new Header();
        header.setNome("Accept");
        header.setValore("application/json");
        headers.add(header);

        mapper.setParametriRichiesta(evento, "https://api.pagopa.it/ibans", "GET", headers);

        assertEquals("https://api.pagopa.it/ibans", evento.getParametriRichiesta().getUrl());
        assertEquals("GET", evento.getParametriRichiesta().getMethod());
        assertEquals(dataStart, evento.getParametriRichiesta().getDataOraRichiesta());
        assertEquals(1, evento.getParametriRichiesta().getHeaders().size());
        assertEquals("Accept", evento.getParametriRichiesta().getHeaders().get(0).getNome());
    }

    @Test
    void setParametriRisposta_withResponseEntity_shouldSetStatusAndHeaders() {
        NuovoEvento evento = mapper.createEvento(TIPO_EVENTO, TRANSACTION_ID, dataStart, dataEnd);
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("Content-Type", "application/json");
        ResponseEntity<String> responseEntity = new ResponseEntity<>("ok", httpHeaders, HttpStatus.OK);

        mapper.setParametriRisposta(evento, dataEnd, responseEntity, null);

        assertEquals(BigDecimal.valueOf(200), evento.getParametriRisposta().getStatus());
        assertEquals(dataEnd, evento.getParametriRisposta().getDataOraRisposta());
        assertEquals(1, evento.getParametriRisposta().getHeaders().size());
        assertEquals("Content-Type", evento.getParametriRisposta().getHeaders().get(0).getNome());
    }

    @Test
    void setParametriRisposta_withHttpStatusCodeException_shouldSetStatusFromException() {
        NuovoEvento evento = mapper.createEvento(TIPO_EVENTO, TRANSACTION_ID, dataStart, dataEnd);
        HttpHeaders exceptionHeaders = new HttpHeaders();
        exceptionHeaders.add("X-Error", "true");
        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.FORBIDDEN, "Forbidden", exceptionHeaders, null, null);

        mapper.setParametriRisposta(evento, dataEnd, null, exception);

        assertEquals(BigDecimal.valueOf(403), evento.getParametriRisposta().getStatus());
        assertEquals(1, evento.getParametriRisposta().getHeaders().size());
        assertEquals("X-Error", evento.getParametriRisposta().getHeaders().get(0).getNome());
    }

    @Test
    void setParametriRisposta_withGenericRestClientException_shouldSetStatus500() {
        NuovoEvento evento = mapper.createEvento(TIPO_EVENTO, TRANSACTION_ID, dataStart, dataEnd);
        RestClientException exception = new RestClientException("timeout");

        mapper.setParametriRisposta(evento, dataEnd, null, exception);

        assertEquals(BigDecimal.valueOf(500), evento.getParametriRisposta().getStatus());
    }

    @Test
    void setParametriRisposta_withNullResponseAndNullException_shouldSetStatus500() {
        NuovoEvento evento = mapper.createEvento(TIPO_EVENTO, TRANSACTION_ID, dataStart, dataEnd);

        mapper.setParametriRisposta(evento, dataEnd, null, null);

        assertEquals(BigDecimal.valueOf(500), evento.getParametriRisposta().getStatus());
    }

    @Test
    void truncate_nullValue_returnsNull() {
        assertNull(EventoIbanMapper.truncate(null, 100));
    }

    @Test
    void truncate_shortValue_returnsUnchanged() {
        assertEquals("short", EventoIbanMapper.truncate("short", 100));
    }

    @Test
    void truncate_longValue_returnsTruncated() {
        String longValue = "a".repeat(1500);
        String result = EventoIbanMapper.truncate(longValue, 1000);
        assertEquals(1000, result.length());
    }
}
