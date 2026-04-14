-- CardDemo Batch Application - Database Schema
-- Migrated from COBOL VSAM KSDS file definitions

CREATE TABLE IF NOT EXISTS account (
    acct_id BIGINT PRIMARY KEY,
    active_status CHAR(1),
    current_balance DECIMAL(12,2),
    credit_limit DECIMAL(12,2),
    cash_credit_limit DECIMAL(12,2),
    open_date VARCHAR(10),
    expiration_date VARCHAR(10),
    reissue_date VARCHAR(10),
    current_cycle_credit DECIMAL(12,2),
    current_cycle_debit DECIMAL(12,2),
    address_zip VARCHAR(10),
    group_id VARCHAR(10)
);

CREATE TABLE IF NOT EXISTS transaction (
    tran_id VARCHAR(16) PRIMARY KEY,
    tran_type_cd VARCHAR(2),
    tran_cat_cd INTEGER,
    tran_source VARCHAR(10),
    tran_desc VARCHAR(100),
    tran_amt DECIMAL(11,2),
    merchant_id BIGINT,
    merchant_name VARCHAR(50),
    merchant_city VARCHAR(50),
    merchant_zip VARCHAR(10),
    card_num VARCHAR(16),
    orig_timestamp VARCHAR(26),
    proc_timestamp VARCHAR(26)
);

CREATE TABLE IF NOT EXISTS card_xref (
    card_num VARCHAR(16) PRIMARY KEY,
    cust_id BIGINT,
    acct_id BIGINT
);

CREATE TABLE IF NOT EXISTS customer (
    cust_id BIGINT PRIMARY KEY,
    first_name VARCHAR(25),
    middle_name VARCHAR(25),
    last_name VARCHAR(25),
    addr_line_1 VARCHAR(50),
    addr_line_2 VARCHAR(50),
    addr_line_3 VARCHAR(50),
    state_cd VARCHAR(2),
    country_cd VARCHAR(3),
    zip VARCHAR(10),
    phone_1 VARCHAR(15),
    phone_2 VARCHAR(15),
    ssn BIGINT,
    govt_issued_id VARCHAR(20),
    dob VARCHAR(10),
    eft_account_id VARCHAR(10),
    primary_card_holder_ind CHAR(1),
    fico_credit_score INTEGER
);

CREATE TABLE IF NOT EXISTS tran_cat_balance (
    acct_id BIGINT,
    tran_type_cd VARCHAR(2),
    tran_cat_cd INTEGER,
    balance DECIMAL(11,2),
    PRIMARY KEY (acct_id, tran_type_cd, tran_cat_cd)
);

CREATE TABLE IF NOT EXISTS transaction_type (
    tran_type VARCHAR(2) PRIMARY KEY,
    description VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS transaction_category (
    tran_type_cd VARCHAR(2),
    tran_cat_cd INTEGER,
    description VARCHAR(54),
    PRIMARY KEY (tran_type_cd, tran_cat_cd)
);

CREATE TABLE IF NOT EXISTS discount_group (
    acct_group_id VARCHAR(10),
    tran_type_cd VARCHAR(2),
    tran_cat_cd INTEGER,
    int_rate DECIMAL(6,2),
    PRIMARY KEY (acct_group_id, tran_type_cd, tran_cat_cd)
);

CREATE TABLE IF NOT EXISTS card (
    card_num VARCHAR(16) PRIMARY KEY,
    card_acct_id BIGINT,
    card_cvv_cd INTEGER,
    card_embossed_name VARCHAR(50),
    card_expiration_date VARCHAR(10),
    card_active_status CHAR(1)
);
