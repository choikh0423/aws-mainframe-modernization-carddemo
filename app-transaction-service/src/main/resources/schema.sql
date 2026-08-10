-- PostgreSQL replacements for the VSAM files used by COTRN02C.
-- TRANSACT (copybook CVTRA05Y) and CCXREF/CXACAIX (copybook CVACT03Y).

CREATE TABLE IF NOT EXISTS transact (
    tran_id            CHAR(16)      PRIMARY KEY,
    tran_type_cd       CHAR(2),
    tran_cat_cd        INTEGER,
    tran_source        VARCHAR(10),
    tran_desc          VARCHAR(100),
    tran_amt           NUMERIC(11,2),
    tran_merchant_id   BIGINT,
    tran_merchant_name VARCHAR(50),
    tran_merchant_city VARCHAR(50),
    tran_merchant_zip  VARCHAR(10),
    tran_card_num      CHAR(16),
    tran_orig_ts       VARCHAR(26),
    tran_proc_ts       VARCHAR(26)
);

CREATE TABLE IF NOT EXISTS ccxref (
    xref_card_num CHAR(16) PRIMARY KEY,
    xref_cust_id  BIGINT,
    xref_acct_id  BIGINT
);

-- Stands in for the CXACAIX alternate index over CCXREF.
CREATE INDEX IF NOT EXISTS cxacaix ON ccxref (xref_acct_id);
