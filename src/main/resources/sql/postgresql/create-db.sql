-- PostgreSQL DDL for PAGOPA_IBAN_CHECK table

CREATE SEQUENCE IF NOT EXISTS seq_pagopa_iban_check START WITH 1 INCREMENT BY 1 NO CYCLE;

CREATE TABLE PAGOPA_IBAN_CHECK (
    id                  BIGINT DEFAULT nextval('seq_pagopa_iban_check') PRIMARY KEY,
    cod_intermediario   VARCHAR(35) NOT NULL,
    ci_fiscal_code      VARCHAR(35) NOT NULL,
    ci_name             VARCHAR(255),
    iban                VARCHAR(35) NOT NULL,
    status              VARCHAR(255),
    validity_date       TIMESTAMP WITH TIME ZONE,
    description         VARCHAR(512),
    label               VARCHAR(1024),
    check_stato         VARCHAR(35),
    check_motivo        VARCHAR(1024)
);

-- Indexes
CREATE INDEX idx_pic_ci_fiscal_code ON PAGOPA_IBAN_CHECK(ci_fiscal_code);
CREATE INDEX idx_pic_iban ON PAGOPA_IBAN_CHECK(iban);
CREATE INDEX idx_pic_cod_intermediario ON PAGOPA_IBAN_CHECK(cod_intermediario);
CREATE INDEX idx_pic_check_stato ON PAGOPA_IBAN_CHECK(check_stato);
CREATE INDEX idx_pic_fiscal_code_iban ON PAGOPA_IBAN_CHECK(ci_fiscal_code, iban);

-- Comments
COMMENT ON TABLE PAGOPA_IBAN_CHECK IS 'Esito del confronto tra gli IBAN pagoPA e IBAN_ACCREDITO censiti su GovPay, azzerata e ripopolata ad ogni run';
COMMENT ON COLUMN PAGOPA_IBAN_CHECK.cod_intermediario IS 'Codice intermediario utilizzato nella richiesta (brokerCode)';
COMMENT ON COLUMN PAGOPA_IBAN_CHECK.ci_fiscal_code IS 'Codice fiscale del dominio (ciFiscalCode)';
COMMENT ON COLUMN PAGOPA_IBAN_CHECK.check_stato IS 'Esito verifica: OK, NON_CENSITO, INFO_DIVERSE, NON_ATTIVO';
COMMENT ON COLUMN PAGOPA_IBAN_CHECK.check_motivo IS 'Descrizione testuale della discrepanza';

-- PostgreSQL DDL for PAGOPA_EC_CHECK table

CREATE SEQUENCE IF NOT EXISTS seq_pagopa_ec_check START WITH 1 INCREMENT BY 1 NO CYCLE;

CREATE TABLE PAGOPA_EC_CHECK (
    id                  BIGINT DEFAULT nextval('seq_pagopa_ec_check') PRIMARY KEY,
    cod_intermediario   VARCHAR(35) NOT NULL,
    tax_code            VARCHAR(16) NOT NULL,
    company_name        VARCHAR(255),
    station_id          VARCHAR(35),
    aux_digit           VARCHAR(2),
    segregation_code    VARCHAR(4),
    cbill_code          VARCHAR(35),
    check_stato         VARCHAR(35),
    check_motivo        VARCHAR(1024)
);

-- Indexes
CREATE INDEX idx_pec_tax_code ON PAGOPA_EC_CHECK(tax_code);
CREATE INDEX idx_pec_cod_intermediario ON PAGOPA_EC_CHECK(cod_intermediario);
CREATE INDEX idx_pec_check_stato ON PAGOPA_EC_CHECK(check_stato);

-- Comments
COMMENT ON TABLE PAGOPA_EC_CHECK IS 'Esito del confronto tra l''anagrafica Enti Creditori pagoPA e i Domini censiti su GovPay, azzerata e ripopolata ad ogni run';
COMMENT ON COLUMN PAGOPA_EC_CHECK.cod_intermediario IS 'Codice intermediario utilizzato nella richiesta (brokerCode)';
COMMENT ON COLUMN PAGOPA_EC_CHECK.tax_code IS 'Codice fiscale dell''ente creditore (taxCode pagoPA)';
COMMENT ON COLUMN PAGOPA_EC_CHECK.check_stato IS 'Esito verifica: OK, NON_CENSITO, INFO_DIVERSE';
COMMENT ON COLUMN PAGOPA_EC_CHECK.check_motivo IS 'Descrizione testuale della discrepanza';
