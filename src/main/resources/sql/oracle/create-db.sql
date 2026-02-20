-- Oracle DDL for IBAN_PAGOPA_TEMP table

-- Create sequence for primary key
CREATE SEQUENCE seq_iban_pagopa_temp
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

-- Create table
CREATE TABLE IBAN_PAGOPA_TEMP (
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
CREATE OR REPLACE TRIGGER IBAN_PAGOPA_TEMP_TRG
BEFORE INSERT ON IBAN_PAGOPA_TEMP
FOR EACH ROW
BEGIN
    IF :NEW.id IS NULL THEN
        SELECT seq_iban_pagopa_temp.NEXTVAL INTO :NEW.id FROM DUAL;
    END IF;
END;
/

-- Indexes
CREATE INDEX idx_ipt_ci_fiscal_code ON IBAN_PAGOPA_TEMP(ci_fiscal_code);
CREATE INDEX idx_ipt_iban ON IBAN_PAGOPA_TEMP(iban);
CREATE INDEX idx_ipt_cod_intermediario ON IBAN_PAGOPA_TEMP(cod_intermediario);
CREATE INDEX idx_ipt_check_stato ON IBAN_PAGOPA_TEMP(check_stato);
CREATE INDEX idx_ipt_fiscal_code_iban ON IBAN_PAGOPA_TEMP(ci_fiscal_code, iban);

-- Comments
COMMENT ON TABLE IBAN_PAGOPA_TEMP IS 'Tabella temporanea per IBAN scaricati da pagoPA durante il batch di verifica';
COMMENT ON COLUMN IBAN_PAGOPA_TEMP.cod_intermediario IS 'Codice intermediario utilizzato nella richiesta (brokerCode)';
COMMENT ON COLUMN IBAN_PAGOPA_TEMP.ci_fiscal_code IS 'Codice fiscale del dominio (ciFiscalCode)';
COMMENT ON COLUMN IBAN_PAGOPA_TEMP.check_stato IS 'Esito verifica: OK, NON_CENSITO, INFO_DIVERSE, NON_ATTIVO';
COMMENT ON COLUMN IBAN_PAGOPA_TEMP.check_motivo IS 'Descrizione testuale della discrepanza';
