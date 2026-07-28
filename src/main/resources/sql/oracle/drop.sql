-- Oracle - Drop PAGOPA_IBAN_CHECK table and related objects

-- Drop trigger first
DROP TRIGGER PAGOPA_IBAN_CHECK_TRG;

-- Drop table (cascade constraints to handle any foreign keys)
DROP TABLE PAGOPA_IBAN_CHECK CASCADE CONSTRAINTS;

-- Drop sequence
DROP SEQUENCE seq_pagopa_iban_check;

-- Oracle - Drop PAGOPA_EC_CHECK table and related objects

DROP TRIGGER PAGOPA_EC_CHECK_TRG;
DROP TABLE PAGOPA_EC_CHECK CASCADE CONSTRAINTS;
DROP SEQUENCE seq_pagopa_ec_check;
