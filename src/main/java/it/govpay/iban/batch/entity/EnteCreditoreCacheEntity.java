package it.govpay.iban.batch.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cache locale dell'anagrafica di un Ente Creditore sincronizzata da pagoPA.
 * Tabella di proprieta' di govpay-console-api (DDL in console-api-schema.sql);
 * qui viene scritta dal batch di sincronizzazione.
 */
@Entity
@Table(name = "pagopa_ec_cache")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnteCreditoreCacheEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pagopa_ec_cache_seq")
	@SequenceGenerator(name = "pagopa_ec_cache_seq", sequenceName = "seq_pagopa_ec_cache", allocationSize = 1)
	private Long id;

	@Column(name = "cod_fiscale", length = 16, nullable = false)
	private String codFiscale;

	@Column(name = "denominazione", length = 255, nullable = false)
	private String denominazione;

	@Column(name = "station_id", length = 35)
	private String stationId;

	@Column(name = "aux_digit", length = 2, nullable = false)
	private String auxDigit;

	@Column(name = "segregation_code", length = 4)
	private String segregationCode;

	@Column(name = "cbill_code", length = 35)
	private String cbillCode;

	@Column(name = "data_ultimo_aggiornamento", nullable = false)
	private OffsetDateTime dataUltimoAggiornamento;
}
