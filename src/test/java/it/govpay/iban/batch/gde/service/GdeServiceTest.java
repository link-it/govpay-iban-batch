package it.govpay.iban.batch.gde.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.govpay.common.client.model.Connettore;
import it.govpay.common.configurazione.service.ConfigurazioneService;
import it.govpay.common.gde.GdeEventInfo;
import it.govpay.gde.client.beans.EsitoEvento;
import it.govpay.gde.client.beans.Header;
import it.govpay.gde.client.beans.NuovoEvento;
import it.govpay.iban.batch.gde.mapper.EventoIbanMapper;
import it.govpay.pagopa.backoffice.client.model.CIIbansResponse;

@ExtendWith(MockitoExtension.class)
class GdeServiceTest {

    @Mock
    private EventoIbanMapper eventoIbanMapper;

    @Mock
    private ConfigurazioneService configurazioneService;

    @Mock
    private RestTemplate restTemplate;

    private GdeService gdeService;

    private OffsetDateTime dataStart;
    private OffsetDateTime dataEnd;

    @BeforeEach
    void setUp() {
        // Use a synchronous executor for deterministic tests
        gdeService = new GdeService(new ObjectMapper(), (Runnable::run), eventoIbanMapper, configurazioneService);
        dataStart = OffsetDateTime.of(2025, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);
        dataEnd = OffsetDateTime.of(2025, 1, 1, 10, 0, 5, 0, ZoneOffset.UTC);
    }

    @Test
    void sendEventAsync_shouldPostEvent() {
        when(configurazioneService.isServizioGDEAbilitato()).thenReturn(true);
        Connettore connettore = mock(Connettore.class);
        when(connettore.getUrl()).thenReturn("http://gde-host");
        when(configurazioneService.getServizioGDE()).thenReturn(connettore);
        when(configurazioneService.getRestTemplateGDE()).thenReturn(restTemplate);

        NuovoEvento evento = new NuovoEvento();
        evento.setTipoEvento("getAllIbans");

        gdeService.sendEventAsync(evento);

        verify(restTemplate).postForEntity(eq("http://gde-host/eventi"), eq(evento), eq(Void.class));
    }

    @Test
    void sendEventAsync_withException_shouldNotPropagate() {
        when(configurazioneService.isServizioGDEAbilitato()).thenReturn(true);
        Connettore connettore = mock(Connettore.class);
        when(connettore.getUrl()).thenReturn("http://gde-host");
        when(configurazioneService.getServizioGDE()).thenReturn(connettore);
        when(configurazioneService.getRestTemplateGDE()).thenReturn(restTemplate);
        when(restTemplate.postForEntity(anyString(), any(), eq(Void.class)))
                .thenThrow(new RestClientException("connection error"));

        NuovoEvento evento = new NuovoEvento();
        evento.setTipoEvento("getAllIbans");

        // Should not throw
        gdeService.sendEventAsync(evento);
    }

    @Test
    void sendEventAsync_withDisabledConnector_shouldNotCallRestTemplate() {
        when(configurazioneService.isServizioGDEAbilitato()).thenReturn(false);

        NuovoEvento evento = new NuovoEvento();
        evento.setTipoEvento("getAllIbans");

        gdeService.sendEventAsync(evento);

        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }

    @Test
    void saveGetIbansOk_shouldCreateOkEventAndSend() {
        when(configurazioneService.isServizioGDEAbilitato()).thenReturn(true);
        Connettore connettore = mock(Connettore.class);
        when(connettore.getUrl()).thenReturn("http://gde-host");
        when(configurazioneService.getServizioGDE()).thenReturn(connettore);
        when(configurazioneService.getRestTemplateGDE()).thenReturn(restTemplate);

        NuovoEvento evento = new NuovoEvento();
        when(eventoIbanMapper.createEventoOk(anyString(), anyString(), any(), any())).thenReturn(evento);

        ResponseEntity<CIIbansResponse> responseEntity = ResponseEntity.ok(new CIIbansResponse());

        gdeService.saveGetIbansOk("12345678901", dataStart, dataEnd, 42, responseEntity, "http://pagopa.it");

        verify(eventoIbanMapper).createEventoOk(eq("getAllIbans"), anyString(), eq(dataStart), eq(dataEnd));
        verify(eventoIbanMapper).setParametriRichiesta(eq(evento), anyString(), eq("GET"), any(List.class));
        verify(eventoIbanMapper).setParametriRisposta(eq(evento), eq(dataEnd), eq(responseEntity), eq(null));
        verify(restTemplate).postForEntity(anyString(), eq(evento), eq(Void.class));
    }

    @Test
    void saveGetIbansKo_shouldCreateKoEventAndSend() {
        when(configurazioneService.isServizioGDEAbilitato()).thenReturn(true);
        Connettore connettore = mock(Connettore.class);
        when(connettore.getUrl()).thenReturn("http://gde-host");
        when(configurazioneService.getServizioGDE()).thenReturn(connettore);
        when(configurazioneService.getRestTemplateGDE()).thenReturn(restTemplate);

        NuovoEvento evento = new NuovoEvento();
        RestClientException exception = new RestClientException("timeout");
        when(eventoIbanMapper.createEventoKo(anyString(), anyString(), any(), any(), any(), eq(exception))).thenReturn(evento);

        ResponseEntity<CIIbansResponse> responseEntity = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();

        gdeService.saveGetIbansKo("12345678901", dataStart, dataEnd, responseEntity, exception, "http://pagopa.it");

        verify(eventoIbanMapper).createEventoKo(eq("getAllIbans"), anyString(), eq(dataStart), eq(dataEnd), eq(null), eq(exception));
        verify(eventoIbanMapper).setParametriRichiesta(eq(evento), anyString(), eq("GET"), any(List.class));
        verify(eventoIbanMapper).setParametriRisposta(eq(evento), eq(dataEnd), eq(null), eq(exception));
        verify(restTemplate).postForEntity(anyString(), eq(evento), eq(Void.class));
    }

    @Test
    void convertToGdeEvent_shouldThrowUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> {
            // Access via reflection since convertToGdeEvent is protected
            java.lang.reflect.Method method = GdeService.class.getDeclaredMethod("convertToGdeEvent", GdeEventInfo.class);
            method.setAccessible(true);
            try {
                method.invoke(gdeService, (GdeEventInfo) null);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }
}
