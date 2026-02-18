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

@Entity
@Table(name = "IBAN_PAGOPA_TEMP")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IbanPagopaTempEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "iban_pagopa_temp_seq")
	@SequenceGenerator(name = "iban_pagopa_temp_seq", sequenceName = "seq_iban_pagopa_temp", allocationSize = 1)
	private Long id;

	@Column(name = "cod_intermediario", length = 35, nullable = false)
	private String brokerCode;

	@Column(name = "ci_fiscal_code", length = 35, nullable = false)
	private String ciFiscalCode;

	@Column(name = "ci_name", length = 255)
	private String ciName;

	@Column(name = "iban", length = 35, nullable = false)
	private String iban;

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
