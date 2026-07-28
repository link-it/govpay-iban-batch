-- Oracle DDL for PAGOPA_IBAN_CHECK table

-- Create sequence for primary key
CREATE SEQUENCE seq_pagopa_iban_check
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

-- Create table
CREATE TABLE PAGOPA_IBAN_CHECK (
    id                  NUMBER PRIMARY KEY,
    cod_intermediario   VARCHAR2(35 CHAR) NOT NULL,
    ci_fiscal_code      VARCHAR2(35 CHAR) NOT NULL,
    ci_name             VARCHAR2(255 CHAR),
    iban                VARCHAR2(35 CHAR) NOT NULL,
    status              VARCHAR2(255 CHAR),
    validity_date       TIMESTAMP WITH TIME ZONE,
    description         VARCHAR2(512 CHAR),
    label               VARCHAR2(1024 CHAR),
    check_stato         VARCHAR2(35 CHAR),
    check_motivo        VARCHAR2(1024 CHAR)
);

-- Create trigger for auto-increment
CREATE OR REPLACE TRIGGER PAGOPA_IBAN_CHECK_TRG
BEFORE INSERT ON PAGOPA_IBAN_CHECK
FOR EACH ROW
BEGIN
    IF :NEW.id IS NULL THEN
        SELECT seq_pagopa_iban_check.NEXTVAL INTO :NEW.id FROM DUAL;
    END IF;
END;
/

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

-- Oracle DDL for PAGOPA_EC_CHECK table

-- Create sequence for primary key
CREATE SEQUENCE seq_pagopa_ec_check
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

-- Create table
CREATE TABLE PAGOPA_EC_CHECK (
    id                  NUMBER PRIMARY KEY,
    cod_intermediario   VARCHAR2(35 CHAR) NOT NULL,
    tax_code            VARCHAR2(16 CHAR) NOT NULL,
    company_name        VARCHAR2(255 CHAR),
    station_id          VARCHAR2(35 CHAR),
    aux_digit           VARCHAR2(2 CHAR),
    segregation_code    VARCHAR2(4 CHAR),
    cbill_code          VARCHAR2(35 CHAR),
    check_stato         VARCHAR2(35 CHAR),
    check_motivo        VARCHAR2(1024 CHAR)
);

-- Create trigger for auto-increment
CREATE OR REPLACE TRIGGER PAGOPA_EC_CHECK_TRG
BEFORE INSERT ON PAGOPA_EC_CHECK
FOR EACH ROW
BEGIN
    IF :NEW.id IS NULL THEN
        SELECT seq_pagopa_ec_check.NEXTVAL INTO :NEW.id FROM DUAL;
    END IF;
END;
/

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
