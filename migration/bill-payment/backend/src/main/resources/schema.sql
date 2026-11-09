-- ACCTDAT (CVACT01Y), CXACAIX over CCXREF (CVACT03Y), TRANSACT (CVTRA05Y)
CREATE TABLE IF NOT EXISTS account (
    acct_id              VARCHAR(11)    PRIMARY KEY,
    active_status        CHAR(1),
    curr_bal             DECIMAL(12, 2) NOT NULL,
    credit_limit         DECIMAL(12, 2),
    cash_credit_limit    DECIMAL(12, 2),
    open_date            VARCHAR(10),
    expiration_date      VARCHAR(10),
    reissue_date         VARCHAR(10),
    curr_cyc_credit      DECIMAL(12, 2),
    curr_cyc_debit       DECIMAL(12, 2),
    addr_zip             VARCHAR(10),
    group_id             VARCHAR(10)
);

CREATE TABLE IF NOT EXISTS card_xref (
    card_num             VARCHAR(16)    PRIMARY KEY,
    cust_id              BIGINT,
    acct_id              VARCHAR(11)    NOT NULL
);
CREATE INDEX IF NOT EXISTS card_xref_acct ON card_xref (acct_id);

CREATE TABLE IF NOT EXISTS transact (
    tran_id              VARCHAR(16)    PRIMARY KEY,
    type_cd              VARCHAR(2),
    cat_cd               INT,
    source               VARCHAR(10),
    description          VARCHAR(100),
    amount               DECIMAL(11, 2),
    merchant_id          BIGINT,
    merchant_name        VARCHAR(50),
    merchant_city        VARCHAR(50),
    merchant_zip         VARCHAR(10),
    card_num             VARCHAR(16),
    orig_ts              VARCHAR(26),
    proc_ts              VARCHAR(26)
);
