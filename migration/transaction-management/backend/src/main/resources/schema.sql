-- ============================================================================
-- Transaction Management (CT00/CT01/CT02) Java migration: H2 schema.
-- Replaces the VSAM KSDS files used by the COBOL online transaction programs.
-- ============================================================================

-- Replaces TRANSACT VSAM KSDS (CVTRA05Y TRAN-RECORD, 350 bytes).
-- Key: TRAN-ID X(16) -> id, a zero-padded 16-digit numeric string.
CREATE TABLE IF NOT EXISTS transactions (
    id            VARCHAR(16)    PRIMARY KEY,   -- TRAN-ID X(16)
    type_cd       VARCHAR(2)     NOT NULL,      -- TRAN-TYPE-CD X(02)
    cat_cd        INTEGER        NOT NULL,      -- TRAN-CAT-CD 9(04)
    source        VARCHAR(10)    NOT NULL,      -- TRAN-SOURCE X(10)
    description   VARCHAR(100)   NOT NULL,      -- TRAN-DESC X(100)
    amount        DECIMAL(11,2)  NOT NULL,      -- TRAN-AMT S9(09)V99
    merchant_id   BIGINT         NOT NULL,      -- TRAN-MERCHANT-ID 9(09)
    merchant_name VARCHAR(50)    NOT NULL,      -- TRAN-MERCHANT-NAME X(50)
    merchant_city VARCHAR(50)    NOT NULL,      -- TRAN-MERCHANT-CITY X(50)
    merchant_zip  VARCHAR(10)    NOT NULL,      -- TRAN-MERCHANT-ZIP X(10)
    card_num      VARCHAR(16)    NOT NULL,      -- TRAN-CARD-NUM X(16)
    orig_ts       VARCHAR(26),                  -- TRAN-ORIG-TS X(26)
    proc_ts       VARCHAR(26)                   -- TRAN-PROC-TS X(26)
    -- FILLER X(20) not stored
);

-- Replaces CCXREF VSAM KSDS + CXACAIX alternate index (CVACT03Y, 50 bytes).
-- Primary key: XREF-CARD-NUM (CCXREF base cluster).
-- Index on acct_id replaces the CXACAIX alternate index (resolve card <- account).
CREATE TABLE IF NOT EXISTS card_xref (
    card_num      VARCHAR(16)    PRIMARY KEY,   -- XREF-CARD-NUM X(16)
    cust_id       BIGINT         NOT NULL,      -- XREF-CUST-ID 9(09)
    acct_id       BIGINT         NOT NULL       -- XREF-ACCT-ID 9(11)
);
CREATE INDEX IF NOT EXISTS idx_card_xref_acct ON card_xref(acct_id);
