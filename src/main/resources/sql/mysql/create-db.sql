-- MySQL DDL for PAGOPA_IBAN_CHECK table

CREATE TABLE PAGOPA_IBAN_CHECK (
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
  COMMENT='Esito del confronto tra gli IBAN pagoPA e IBAN_ACCREDITO censiti su GovPay, azzerata e ripopolata ad ogni run';

-- Indexes
CREATE INDEX idx_pic_ci_fiscal_code ON PAGOPA_IBAN_CHECK(ci_fiscal_code);
CREATE INDEX idx_pic_iban ON PAGOPA_IBAN_CHECK(iban);
CREATE INDEX idx_pic_cod_intermediario ON PAGOPA_IBAN_CHECK(cod_intermediario);
CREATE INDEX idx_pic_check_stato ON PAGOPA_IBAN_CHECK(check_stato);
CREATE INDEX idx_pic_fiscal_code_iban ON PAGOPA_IBAN_CHECK(ci_fiscal_code, iban);

-- MySQL DDL for PAGOPA_EC_CHECK table

CREATE TABLE PAGOPA_EC_CHECK (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    cod_intermediario   VARCHAR(35) NOT NULL,
    tax_code            VARCHAR(16) NOT NULL,
    company_name        VARCHAR(255),
    station_id          VARCHAR(35),
    aux_digit           VARCHAR(2),
    segregation_code    VARCHAR(4),
    cbill_code          VARCHAR(35),
    check_stato         VARCHAR(35),
    check_motivo        VARCHAR(1024)
) ENGINE=InnoDB CHARACTER SET latin1 COLLATE latin1_general_cs
  COMMENT='Esito del confronto tra l''anagrafica Enti Creditori pagoPA e i Domini censiti su GovPay, azzerata e ripopolata ad ogni run';

-- Indexes
CREATE INDEX idx_pec_tax_code ON PAGOPA_EC_CHECK(tax_code);
CREATE INDEX idx_pec_cod_intermediario ON PAGOPA_EC_CHECK(cod_intermediario);
CREATE INDEX idx_pec_check_stato ON PAGOPA_EC_CHECK(check_stato);
