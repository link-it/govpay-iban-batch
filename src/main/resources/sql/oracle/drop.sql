-- Oracle - Drop IBAN_PAGOPA_TEMP table and related objects

-- Drop trigger first
DROP TRIGGER IBAN_PAGOPA_TEMP_TRG;

-- Drop table (cascade constraints to handle any foreign keys)
DROP TABLE IBAN_PAGOPA_TEMP CASCADE CONSTRAINTS;

-- Drop sequence
DROP SEQUENCE seq_iban_pagopa_temp;
