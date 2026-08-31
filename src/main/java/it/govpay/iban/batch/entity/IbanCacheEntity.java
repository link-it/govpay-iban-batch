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
 * Cache locale degli IBAN abilitati su pagoPA per dominio, per il censimento
 * assistito dei conti di accredito. Tabella di proprieta' di
 * govpay-console-api (DDL in console-api-schema.sql); qui viene scritta dal
 * batch di verifica IBAN.
 */
@Entity
@Table(name = "pagopa_iban_cache")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IbanCacheEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pagopa_iban_cache_seq")
	@SequenceGenerator(name = "pagopa_iban_cache_seq", sequenceName = "seq_pagopa_iban_cache", allocationSize = 1)
	private Long id;

	@Column(name = "cod_dominio", length = 35, nullable = false)
	private String codDominio;

	@Column(name = "iban", length = 35, nullable = false)
	private String iban;

	@Column(name = "attivo", nullable = false)
	private Boolean attivo;

	@Column(name = "data_ultima_verifica", nullable = false)
	private OffsetDateTime dataUltimaVerifica;

	@Column(name = "cod_intermediario", length = 35)
	private String codIntermediario;

	@Column(name = "ci_name", length = 255)
	private String ciName;

	@Column(name = "status", length = 255)
	private String status;

	@Column(name = "validity_date")
	private OffsetDateTime validityDate;

	@Column(name = "description", length = 512)
	private String description;

	@Column(name = "label", length = 1024)
	private String label;

	@Column(name = "check_stato", length = 35)
	private String checkStato;

	@Column(name = "check_motivo", length = 1024)
	private String checkMotivo;
}
