-- S-13 StatementGeneration (band V1300-V1399).
--
-- AWS.M2.CARDDEMO.TRXFL.VSAM.KSDS, the card-keyed copy of TRANSACT that
-- CREASTMT.JCL DELDEF01 defines (KEYS(32 0), RECORDSIZE(350 350), INDEXED) and
-- STEP020 REPROs into. Columns follow app/cpy/COSTM01.CPY, the "transaction
-- altered layout" the SORT step produces; the key is the 32-byte card number +
-- transaction id pair.
--
-- Stream-private work store: only com.carddemo.batch.statement reads or writes it.

CREATE TABLE statement_work_transactions (
    card_num            VARCHAR(16)   NOT NULL,
    tran_id             VARCHAR(16)   NOT NULL,
    type_cd             VARCHAR(2)    NOT NULL,
    cat_cd              INTEGER       NOT NULL,
    source              VARCHAR(10)   NOT NULL,
    description         VARCHAR(100)  NOT NULL,
    amount              NUMERIC(11,2) NOT NULL,
    merchant_id         NUMERIC(9)    NOT NULL,
    merchant_name       VARCHAR(50)   NOT NULL,
    merchant_city       VARCHAR(50)   NOT NULL,
    merchant_zip        VARCHAR(10)   NOT NULL,
    orig_ts             VARCHAR(26),
    proc_ts             VARCHAR(26),
    CONSTRAINT pk_statement_work_transactions PRIMARY KEY (card_num, tran_id)
);
