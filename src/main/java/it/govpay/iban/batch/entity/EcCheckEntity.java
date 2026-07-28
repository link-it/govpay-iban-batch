package it.govpay.iban.batch.entity;

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

@Entity
@Table(name = "PAGOPA_EC_CHECK")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EcCheckEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pagopa_ec_check_seq")
	@SequenceGenerator(name = "pagopa_ec_check_seq", sequenceName = "seq_pagopa_ec_check", allocationSize = 1)
	private Long id;

	@Column(name = "cod_intermediario", length = 35, nullable = false)
	private String codIntermediario;

	@Column(name = "tax_code", length = 16, nullable = false)
	private String taxCode;

	@Column(name = "company_name", length = 255)
	private String companyName;

	@Column(name = "station_id", length = 35)
	private String stationId;

	@Column(name = "aux_digit", length = 2)
	private String auxDigit;

	@Column(name = "segregation_code", length = 4)
	private String segregationCode;

	@Column(name = "cbill_code", length = 35)
	private String cbillCode;

	@Column(name = "check_stato", length = 35)
	private String checkStato;

	@Column(name = "check_motivo", length = 1024)
	private String checkMotivo;
}
