-- CardDemo estate baseline schema.
--
-- Every column below is derived from the COBOL copybooks, the DB2 DDL and the
-- IMS DBDs under app/. PIC 9(n) -> NUMERIC(n); PIC S9(a)V9(b) -> NUMERIC(a+b,b);
-- PIC X(n) -> VARCHAR(n) (CHAR(n) where the DB2 DDL says CHAR). Trailing COBOL
-- FILLER is dropped: it carries no data.
--
-- This file is owned by the foundation. Stream sessions add their own numbered
-- migrations in their reserved band (see migration/carddemo/README.md) and never
-- edit this one.

-- ---------------------------------------------------------------------------
-- VSAM-derived master files
-- ---------------------------------------------------------------------------

-- ACCTDAT / CVACT01Y ACCOUNT-RECORD (RECLN 300)
CREATE TABLE accounts (
    acct_id             NUMERIC(11)   NOT NULL,
    active_status       VARCHAR(1)    NOT NULL,
    curr_bal            NUMERIC(12,2) NOT NULL,
    credit_limit        NUMERIC(12,2) NOT NULL,
    cash_credit_limit   NUMERIC(12,2) NOT NULL,
    open_date           VARCHAR(10)   NOT NULL,
    expiraion_date      VARCHAR(10)   NOT NULL,
    reissue_date        VARCHAR(10)   NOT NULL,
    curr_cyc_credit     NUMERIC(12,2) NOT NULL,
    curr_cyc_debit      NUMERIC(12,2) NOT NULL,
    addr_zip            VARCHAR(10)   NOT NULL,
    group_id            VARCHAR(10)   NOT NULL,
    CONSTRAINT pk_accounts PRIMARY KEY (acct_id)
);

-- CUSTDAT / CVCUS01Y CUSTOMER-RECORD (RECLN 500)
CREATE TABLE customers (
    cust_id             NUMERIC(9)   NOT NULL,
    first_name          VARCHAR(25)  NOT NULL,
    middle_name         VARCHAR(25)  NOT NULL,
    last_name           VARCHAR(25)  NOT NULL,
    addr_line_1         VARCHAR(50)  NOT NULL,
    addr_line_2         VARCHAR(50)  NOT NULL,
    addr_line_3         VARCHAR(50)  NOT NULL,
    addr_state_cd       VARCHAR(2)   NOT NULL,
    addr_country_cd     VARCHAR(3)   NOT NULL,
    addr_zip            VARCHAR(10)  NOT NULL,
    phone_num_1         VARCHAR(15)  NOT NULL,
    phone_num_2         VARCHAR(15)  NOT NULL,
    ssn                 NUMERIC(9)   NOT NULL,
    govt_issued_id      VARCHAR(20)  NOT NULL,
    dob_yyyy_mm_dd      VARCHAR(10)  NOT NULL,
    eft_account_id      VARCHAR(10)  NOT NULL,
    pri_card_holder_ind VARCHAR(1)   NOT NULL,
    fico_credit_score   NUMERIC(3)   NOT NULL,
    CONSTRAINT pk_customers PRIMARY KEY (cust_id)
);

-- CARDDAT / CVACT02Y CARD-RECORD (RECLN 150)
CREATE TABLE cards (
    card_num            VARCHAR(16) NOT NULL,
    acct_id             NUMERIC(11) NOT NULL,
    cvv_cd              NUMERIC(3)  NOT NULL,
    embossed_name       VARCHAR(50) NOT NULL,
    expiraion_date      VARCHAR(10) NOT NULL,
    active_status       VARCHAR(1)  NOT NULL,
    CONSTRAINT pk_cards PRIMARY KEY (card_num)
);
-- CARDAIX alternate index (card by account)
CREATE INDEX idx_cards_acct ON cards (acct_id);

-- CCXREF / CVACT03Y CARD-XREF-RECORD (RECLN 50)
CREATE TABLE card_xref (
    card_num            VARCHAR(16) NOT NULL,
    cust_id             NUMERIC(9)  NOT NULL,
    acct_id             NUMERIC(11) NOT NULL,
    CONSTRAINT pk_card_xref PRIMARY KEY (card_num)
);
-- CXACAIX alternate index (xref by account)
CREATE INDEX idx_card_xref_acct ON card_xref (acct_id);

-- TRANSACT / CVTRA05Y TRAN-RECORD (RECLN 350)
CREATE TABLE transactions (
    id                  VARCHAR(16)   NOT NULL,
    type_cd             VARCHAR(2)    NOT NULL,
    cat_cd              INTEGER       NOT NULL,
    source              VARCHAR(10)   NOT NULL,
    description         VARCHAR(100)  NOT NULL,
    amount              NUMERIC(11,2) NOT NULL,
    merchant_id         NUMERIC(9)    NOT NULL,
    merchant_name       VARCHAR(50)   NOT NULL,
    merchant_city       VARCHAR(50)   NOT NULL,
    merchant_zip        VARCHAR(10)   NOT NULL,
    card_num            VARCHAR(16)   NOT NULL,
    orig_ts             VARCHAR(26),
    proc_ts             VARCHAR(26),
    CONSTRAINT pk_transactions PRIMARY KEY (id)
);
CREATE INDEX idx_transactions_card ON transactions (card_num);

-- DALYTRAN / CVTRA06Y DALYTRAN-RECORD (RECLN 350)
CREATE TABLE daily_transactions (
    id                  VARCHAR(16)   NOT NULL,
    type_cd             VARCHAR(2)    NOT NULL,
    cat_cd              INTEGER       NOT NULL,
    source              VARCHAR(10)   NOT NULL,
    description         VARCHAR(100)  NOT NULL,
    amount              NUMERIC(11,2) NOT NULL,
    merchant_id         NUMERIC(9)    NOT NULL,
    merchant_name       VARCHAR(50)   NOT NULL,
    merchant_city       VARCHAR(50)   NOT NULL,
    merchant_zip        VARCHAR(10)   NOT NULL,
    card_num            VARCHAR(16)   NOT NULL,
    orig_ts             VARCHAR(26),
    proc_ts             VARCHAR(26),
    CONSTRAINT pk_daily_transactions PRIMARY KEY (id)
);
CREATE INDEX idx_daily_transactions_card ON daily_transactions (card_num);

-- TCATBALF / CVTRA01Y TRAN-CAT-BAL-RECORD (RECLN 50)
CREATE TABLE transaction_category_balances (
    acct_id             NUMERIC(11)   NOT NULL,
    type_cd             VARCHAR(2)    NOT NULL,
    cat_cd              NUMERIC(4)    NOT NULL,
    bal                 NUMERIC(11,2) NOT NULL,
    CONSTRAINT pk_transaction_category_balances PRIMARY KEY (acct_id, type_cd, cat_cd)
);

-- DISCGRP / CVTRA02Y DIS-GROUP-RECORD (RECLN 50)
CREATE TABLE disclosure_groups (
    acct_group_id       VARCHAR(10)  NOT NULL,
    tran_type_cd        VARCHAR(2)   NOT NULL,
    tran_cat_cd         NUMERIC(4)   NOT NULL,
    int_rate            NUMERIC(6,2) NOT NULL,
    CONSTRAINT pk_disclosure_groups PRIMARY KEY (acct_group_id, tran_type_cd, tran_cat_cd)
);

-- TRANTYPE / CVTRA03Y TRAN-TYPE-RECORD (RECLN 60)
CREATE TABLE transaction_types (
    tran_type           VARCHAR(2)  NOT NULL,
    tran_type_desc      VARCHAR(50) NOT NULL,
    CONSTRAINT pk_transaction_types PRIMARY KEY (tran_type)
);

-- TRANCATG / CVTRA04Y TRAN-CAT-RECORD (RECLN 60)
CREATE TABLE transaction_categories (
    tran_type_cd        VARCHAR(2)  NOT NULL,
    tran_cat_cd         NUMERIC(4)  NOT NULL,
    tran_cat_type_desc  VARCHAR(50) NOT NULL,
    CONSTRAINT pk_transaction_categories PRIMARY KEY (tran_type_cd, tran_cat_cd)
);

-- USRSEC / CSUSR01Y SEC-USER-DATA (RECLN 80)
CREATE TABLE sec_users (
    sec_usr_id          VARCHAR(8)  NOT NULL,
    sec_usr_fname       VARCHAR(20) NOT NULL,
    sec_usr_lname       VARCHAR(20) NOT NULL,
    sec_usr_pwd         VARCHAR(8)  NOT NULL,
    sec_usr_type        VARCHAR(1)  NOT NULL,
    CONSTRAINT pk_sec_users PRIMARY KEY (sec_usr_id)
);

-- ---------------------------------------------------------------------------
-- DB2-derived tables (app/app-transaction-type-db2, app/app-authorization-ims-db2-mq)
-- Column names are the DDL's, verbatim, including the CARDDEMO spelling of
-- MERCHANT_CATAGORY_CODE.
-- ---------------------------------------------------------------------------

-- CARDDEMO.TRANSACTION_TYPE (ddl/TRNTYPE.ddl)
CREATE TABLE db2_transaction_type (
    tr_type             CHAR(2)     NOT NULL,
    tr_description      VARCHAR(50) NOT NULL,
    CONSTRAINT pk_db2_transaction_type PRIMARY KEY (tr_type)
);

-- CARDDEMO.TRANSACTION_TYPE_CATEGORY (ddl/TRNTYCAT.ddl)
CREATE TABLE db2_transaction_type_category (
    trc_type_code       CHAR(2)     NOT NULL,
    trc_type_category   CHAR(4)     NOT NULL,
    trc_cat_data        VARCHAR(50) NOT NULL,
    CONSTRAINT pk_db2_transaction_type_category PRIMARY KEY (trc_type_code, trc_type_category),
    CONSTRAINT fk_db2_trc_type FOREIGN KEY (trc_type_code)
        REFERENCES db2_transaction_type (tr_type)
);

-- CARDDEMO.AUTHFRDS (ddl/AUTHFRDS.ddl)
CREATE TABLE auth_fraud (
    card_num              CHAR(16)      NOT NULL,
    auth_ts               TIMESTAMP     NOT NULL,
    auth_type             CHAR(4),
    card_expiry_date      CHAR(4),
    message_type          CHAR(6),
    message_source        CHAR(6),
    auth_id_code          CHAR(6),
    auth_resp_code        CHAR(2),
    auth_resp_reason      CHAR(4),
    processing_code       CHAR(6),
    transaction_amt       NUMERIC(11,2),
    approved_amt          NUMERIC(11,2),
    merchant_catagory_code CHAR(4),
    acqr_country_code     CHAR(3),
    pos_entry_mode        CHAR(2),
    merchant_id           CHAR(15),
    merchant_name         CHAR(22),
    merchant_city         CHAR(13),
    merchant_state        CHAR(2),
    merchant_zip          CHAR(9),
    transaction_id        CHAR(15),
    match_status          CHAR(1),
    auth_fraud            CHAR(1),
    fraud_rpt_date        CHAR(8),
    acct_id               NUMERIC(11),
    cust_id               NUMERIC(9),
    CONSTRAINT pk_auth_fraud PRIMARY KEY (card_num, auth_ts)
);
CREATE INDEX idx_auth_fraud_acct ON auth_fraud (acct_id);

-- ---------------------------------------------------------------------------
-- IMS-derived tables (app/app-authorization-ims-db2-mq/ims)
-- DBPAUTP0: root segment PAUTSUM0 (key ACCNTID) -> pending_auth_summary,
--           child segment PAUTDTL1 (key PAUT9CTS) -> pending_auth_detail.
-- DBPAUTX0: secondary index PAUTINDX on ACCNTID -> the summary primary key,
--           so no extra table is needed.
-- ---------------------------------------------------------------------------

-- PAUTSUM0 / CIPAUSMY PENDING-AUTH-SUMMARY (100 bytes)
CREATE TABLE pending_auth_summary (
    pa_acct_id            NUMERIC(11)   NOT NULL,
    pa_cust_id            NUMERIC(9)    NOT NULL,
    pa_auth_status        VARCHAR(1)    NOT NULL,
    pa_account_status_1   VARCHAR(2),
    pa_account_status_2   VARCHAR(2),
    pa_account_status_3   VARCHAR(2),
    pa_account_status_4   VARCHAR(2),
    pa_account_status_5   VARCHAR(2),
    pa_credit_limit       NUMERIC(11,2) NOT NULL,
    pa_cash_limit         NUMERIC(11,2) NOT NULL,
    pa_credit_balance     NUMERIC(11,2) NOT NULL,
    pa_cash_balance       NUMERIC(11,2) NOT NULL,
    pa_approved_auth_cnt  INTEGER       NOT NULL,
    pa_declined_auth_cnt  INTEGER       NOT NULL,
    pa_approved_auth_amt  NUMERIC(11,2) NOT NULL,
    pa_declined_auth_amt  NUMERIC(11,2) NOT NULL,
    CONSTRAINT pk_pending_auth_summary PRIMARY KEY (pa_acct_id)
);

-- PAUTDTL1 / CIPAUDTY PENDING-AUTH-DETAILS (200 bytes)
-- The IMS sequence field PAUT9CTS is the 9s-complement date+time pair; it is kept
-- as the two source fields so the segment key stays recognisable.
CREATE TABLE pending_auth_detail (
    pa_acct_id            NUMERIC(11)   NOT NULL,
    pa_auth_date_9c       NUMERIC(5)    NOT NULL,
    pa_auth_time_9c       NUMERIC(9)    NOT NULL,
    pa_auth_orig_date     VARCHAR(6),
    pa_auth_orig_time     VARCHAR(6),
    pa_card_num           VARCHAR(16)   NOT NULL,
    pa_auth_type          VARCHAR(4),
    pa_card_expiry_date   VARCHAR(4),
    pa_message_type       VARCHAR(6),
    pa_message_source     VARCHAR(6),
    pa_auth_id_code       VARCHAR(6),
    pa_auth_resp_code     VARCHAR(2),
    pa_auth_resp_reason   VARCHAR(4),
    pa_processing_code    NUMERIC(6),
    pa_transaction_amt    NUMERIC(12,2),
    pa_approved_amt       NUMERIC(12,2),
    pa_merchant_catagory_code VARCHAR(4),
    pa_acqr_country_code  VARCHAR(3),
    pa_pos_entry_mode     NUMERIC(2),
    pa_merchant_id        VARCHAR(15),
    pa_merchant_name      VARCHAR(22),
    pa_merchant_city      VARCHAR(13),
    pa_merchant_state     VARCHAR(2),
    pa_merchant_zip       VARCHAR(9),
    pa_transaction_id     VARCHAR(15),
    pa_match_status       VARCHAR(1),
    pa_auth_fraud         VARCHAR(1),
    pa_fraud_rpt_date     VARCHAR(8),
    CONSTRAINT pk_pending_auth_detail PRIMARY KEY (pa_acct_id, pa_auth_date_9c, pa_auth_time_9c),
    CONSTRAINT fk_pending_auth_detail_parent FOREIGN KEY (pa_acct_id)
        REFERENCES pending_auth_summary (pa_acct_id)
);
CREATE INDEX idx_pending_auth_detail_card ON pending_auth_detail (pa_card_num);
