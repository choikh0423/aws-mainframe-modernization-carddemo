-- ============================================================================
-- COTRN02C Java Migration: Seed Data
-- Provides test data for card cross-reference lookups and one existing
-- transaction for the PF5 "Copy Last Transaction" function.
-- ============================================================================

-- Card cross-reference records (replaces CCXREF / CXACAIX VSAM files)
INSERT INTO card_xref (xref_card_num, xref_cust_id, xref_acct_id) VALUES
('4111111111111111', 100000001, 00000000001);
INSERT INTO card_xref (xref_card_num, xref_cust_id, xref_acct_id) VALUES
('4222222222222222', 100000002, 00000000002);
INSERT INTO card_xref (xref_card_num, xref_cust_id, xref_acct_id) VALUES
('4333333333333333', 100000003, 00000000003);

-- Seed transaction for COPY-LAST-TRAN-DATA (PF5) testing
INSERT INTO transaction_record (
    tran_id, tran_type_cd, tran_cat_cd, tran_source, tran_desc, tran_amt,
    tran_merchant_id, tran_merchant_name, tran_merchant_city, tran_merchant_zip,
    tran_card_num, tran_orig_ts, tran_proc_ts
) VALUES (
    '0000000000000001', '01', 5001, 'MANUAL',
    'Seed transaction for testing', 250.00,
    123456789, 'Seed Merchant', 'Chicago', '60601',
    '4111111111111111', '2024-01-01', '2024-01-02'
);
