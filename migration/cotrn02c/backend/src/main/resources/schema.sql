-- ============================================================================
-- COTRN02C Java Migration: Database Schema
-- Replaces VSAM KSDS files used by the COBOL Add Transaction program.
-- ============================================================================

-- Replaces TRANSACT VSAM KSDS (CVTRA05Y TRAN-RECORD, 350 bytes)
-- Key: TRAN-ID X(16) — zero-padded numeric string
CREATE TABLE IF NOT EXISTS transaction_record (
    tran_id            VARCHAR(16)    PRIMARY KEY,    -- TRAN-ID X(16)
    tran_type_cd       VARCHAR(2)     NOT NULL,       -- TRAN-TYPE-CD X(02)
    tran_cat_cd        INTEGER        NOT NULL,       -- TRAN-CAT-CD 9(04)
    tran_source        VARCHAR(10)    NOT NULL,       -- TRAN-SOURCE X(10)
    tran_desc          VARCHAR(100)   NOT NULL,       -- TRAN-DESC X(100)
    tran_amt           DECIMAL(11,2)  NOT NULL,       -- TRAN-AMT S9(09)V99
    tran_merchant_id   BIGINT         NOT NULL,       -- TRAN-MERCHANT-ID 9(09)
    tran_merchant_name VARCHAR(50)    NOT NULL,       -- TRAN-MERCHANT-NAME X(50)
    tran_merchant_city VARCHAR(50)    NOT NULL,       -- TRAN-MERCHANT-CITY X(50)
    tran_merchant_zip  VARCHAR(10)    NOT NULL,       -- TRAN-MERCHANT-ZIP X(10)
    tran_card_num      VARCHAR(16)    NOT NULL,       -- TRAN-CARD-NUM X(16)
    tran_orig_ts       VARCHAR(26),                   -- TRAN-ORIG-TS X(26)
    tran_proc_ts       VARCHAR(26)                    -- TRAN-PROC-TS X(26)
    -- FILLER X(20) not stored in DB
);

-- Replaces CCXREF VSAM KSDS + CXACAIX alternate index (CVACT03Y, 50 bytes)
-- Primary key: XREF-CARD-NUM (CCXREF base cluster)
-- Index on XREF-ACCT-ID (replaces CXACAIX alternate index)
CREATE TABLE IF NOT EXISTS card_xref (
    xref_card_num      VARCHAR(16)    PRIMARY KEY,    -- XREF-CARD-NUM X(16)
    xref_cust_id       BIGINT         NOT NULL,       -- XREF-CUST-ID 9(09)
    xref_acct_id       BIGINT         NOT NULL        -- XREF-ACCT-ID 9(11)
);
CREATE INDEX IF NOT EXISTS idx_card_xref_acct ON card_xref(xref_acct_id);

-- Replaces ACCTDAT VSAM (CVACT01Y ACCOUNT-RECORD, 300 bytes)
-- Included for schema completeness; COTRN02C does NOT read this file.
CREATE TABLE IF NOT EXISTS account_record (
    acct_id              BIGINT         PRIMARY KEY,  -- ACCT-ID 9(11)
    acct_active_status   VARCHAR(1),                  -- ACCT-ACTIVE-STATUS X(01)
    acct_curr_bal        DECIMAL(12,2),               -- ACCT-CURR-BAL S9(10)V99
    acct_credit_limit    DECIMAL(12,2),               -- ACCT-CREDIT-LIMIT S9(10)V99
    acct_cash_credit_limit DECIMAL(12,2),             -- ACCT-CASH-CREDIT-LIMIT S9(10)V99
    acct_open_date       VARCHAR(10),                 -- ACCT-OPEN-DATE X(10)
    acct_expiration_date VARCHAR(10),                 -- ACCT-EXPIRAION-DATE X(10)
    acct_reissue_date    VARCHAR(10),                 -- ACCT-REISSUE-DATE X(10)
    acct_curr_cyc_credit DECIMAL(12,2),               -- ACCT-CURR-CYC-CREDIT S9(10)V99
    acct_curr_cyc_debit  DECIMAL(12,2),               -- ACCT-CURR-CYC-DEBIT S9(10)V99
    acct_addr_zip        VARCHAR(10),                 -- ACCT-ADDR-ZIP X(10)
    acct_group_id        VARCHAR(10)                  -- ACCT-GROUP-ID X(10)
    -- FILLER X(178) not stored in DB
);
