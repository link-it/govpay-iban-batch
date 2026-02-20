-- MySQL DDL for IBAN_PAGOPA_TEMP table

CREATE TABLE IBAN_PAGOPA_TEMP (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    cod_intermediario   VARCHAR(35) NOT NULL,
    ci_fiscal_code      VARCHAR(35) NOT NULL,
    ci_name             VARCHAR(255),
    iban                VARCHAR(35) NOT NULL,
    status              VARCHAR(255),
    validity_date       DATETIME(3),
    description         VARCHAR(512),
    label               VARCHAR(1024),
    check_stato         VARCHAR(35),
    check_motivo        VARCHAR(1024)
) ENGINE=InnoDB CHARACTER SET latin1 COLLATE latin1_general_cs
  COMMENT='Tabella temporanea per IBAN scaricati da pagoPA durante il batch di verifica';

-- Indexes
CREATE INDEX idx_ipt_ci_fiscal_code ON IBAN_PAGOPA_TEMP(ci_fiscal_code);
CREATE INDEX idx_ipt_iban ON IBAN_PAGOPA_TEMP(iban);
CREATE INDEX idx_ipt_cod_intermediario ON IBAN_PAGOPA_TEMP(cod_intermediario);
CREATE INDEX idx_ipt_check_stato ON IBAN_PAGOPA_TEMP(check_stato);
CREATE INDEX idx_ipt_fiscal_code_iban ON IBAN_PAGOPA_TEMP(ci_fiscal_code, iban);
