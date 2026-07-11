package it.govpay.iban.batch.config;

import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.util.TimeZone;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import it.govpay.iban.batch.Costanti;
import it.govpay.common.utils.OffsetDateTimeDeserializer;
import it.govpay.common.utils.OffsetDateTimeSerializer;

/**
 * Web configuration class that provides a global ObjectMapper bean
 * with custom date serialization/deserialization for the entire application.
 * <p>
 * This ObjectMapper is used by:
 * - GDE client for sending events with customized date formats
 * - Spring MVC for JSON request/response handling
 * - Any component that requires JSON processing
 */
@Configuration
public class WebConfig {

	@Value("${spring.jackson.time-zone:Europe/Rome}")
	private String timezone;

	/**
	 * Creates a global ObjectMapper bean with custom configurations for date handling.
	 * <p>
	 * Configuration details:
	 * - Date format: yyyy-MM-dd'T'HH:mm:ss.SSSXXX (timestamp with timezone)
	 * - Custom OffsetDateTime serializer: ensures consistent date format in output
	 * - Custom OffsetDateTime deserializer: handles variable milliseconds from pagoPA API
	 * - Enums: serialized/deserialized using toString()
	 * - Dates: written as ISO-8601 strings (not timestamps) with zone ID
	 * - Timezone: configured from spring.jackson.time-zone property
	 *
	 * @return configured ObjectMapper instance
	 */
	@Bean
	public ObjectMapper objectMapper() {
		// Register custom serializers/deserializers for OffsetDateTime
		SimpleModule dateModule = new SimpleModule();
		dateModule.addSerializer(OffsetDateTime.class, new OffsetDateTimeSerializer());
		dateModule.addDeserializer(OffsetDateTime.class, new OffsetDateTimeDeserializer());

		// Jackson 3: ObjectMapper e' immutabile, la configurazione avviene tramite builder.
		return JsonMapper.builder()
			.defaultTimeZone(TimeZone.getTimeZone(timezone))
			// Set date format for java.util.Date (legacy support)
			.defaultDateFormat(new SimpleDateFormat(Costanti.PATTERN_TIMESTAMP_3_YYYY_MM_DD_T_HH_MM_SS_SSSXXX))
			.addModule(dateModule)
			// Enable enum serialization using toString()
			.enable(EnumFeature.READ_ENUMS_USING_TO_STRING)
			.enable(EnumFeature.WRITE_ENUMS_USING_TO_STRING)
			// Configure date serialization format
			.enable(DateTimeFeature.WRITE_DATES_WITH_ZONE_ID)
			.disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
			.build();
	}
}
