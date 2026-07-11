package it.govpay.iban.batch.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;

class IbanPagopaApiClientConfigTest {

    private IbanPagopaApiClientConfig config;

    @BeforeEach
    void setUp() {
        config = new IbanPagopaApiClientConfig();
        ReflectionTestUtils.setField(config, "timezone", "Europe/Rome");
    }

    @Test
    void createPagoPAObjectMapper_shouldReturnNonNull() {
        ObjectMapper mapper = config.createPagoPAObjectMapper();
        assertNotNull(mapper);
    }

    @Test
    void createPagoPAObjectMapper_shouldNotWriteDatesAsTimestamps() {
        ObjectMapper mapper = config.createPagoPAObjectMapper();
        assertFalse(mapper.isEnabled(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS));
    }

    @Test
    void createPagoPAObjectMapper_shouldWriteDatesWithZoneId() {
        ObjectMapper mapper = config.createPagoPAObjectMapper();
        assertTrue(mapper.isEnabled(DateTimeFeature.WRITE_DATES_WITH_ZONE_ID));
    }

    @Test
    void createPagoPAObjectMapper_shouldSerializeOffsetDateTime() throws JacksonException {
        ObjectMapper mapper = config.createPagoPAObjectMapper();

        OffsetDateTime dateTime = OffsetDateTime.of(2025, 6, 15, 10, 30, 0, 0, ZoneOffset.UTC);
        String json = mapper.writeValueAsString(dateTime);

        assertNotNull(json);
        assertTrue(json.contains("2025"));
        assertTrue(json.contains("06"));
        assertTrue(json.contains("15"));
    }

    @Test
    void createPagoPAObjectMapper_shouldDeserializeOffsetDateTimeWithVariableMillis() throws JacksonException {
        ObjectMapper mapper = config.createPagoPAObjectMapper();

        // 3-digit millis
        OffsetDateTime dt3 = mapper.readValue("\"2025-06-15T10:30:00.123+02:00\"", OffsetDateTime.class);
        assertNotNull(dt3);
        assertEquals(15, dt3.getDayOfMonth());

        // 6-digit millis
        OffsetDateTime dt6 = mapper.readValue("\"2025-06-15T10:30:00.123456+02:00\"", OffsetDateTime.class);
        assertNotNull(dt6);
        assertEquals(15, dt6.getDayOfMonth());
    }

    @Test
    void createPagoPAObjectMapper_shouldDeserializeOffsetDateTimeWithoutMillis() throws JacksonException {
        ObjectMapper mapper = config.createPagoPAObjectMapper();

        OffsetDateTime dt = mapper.readValue("\"2025-06-15T10:30:00+02:00\"", OffsetDateTime.class);
        assertNotNull(dt);
        assertEquals(2025, dt.getYear());
    }
}
