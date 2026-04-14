-- CardDemo Batch Application - Seed Data
-- Sample data matching the COBOL mainframe demonstration datasets

-- Transaction Types
INSERT INTO transaction_type (tran_type, description) VALUES ('01', 'Purchase');
INSERT INTO transaction_type (tran_type, description) VALUES ('02', 'Return');
INSERT INTO transaction_type (tran_type, description) VALUES ('03', 'Payment');
INSERT INTO transaction_type (tran_type, description) VALUES ('04', 'Balance Transfer');
INSERT INTO transaction_type (tran_type, description) VALUES ('05', 'Cash Advance');

-- Transaction Categories
INSERT INTO transaction_category (tran_type_cd, tran_cat_cd, description) VALUES ('01', 1, 'Retail Purchase');
INSERT INTO transaction_category (tran_type_cd, tran_cat_cd, description) VALUES ('01', 2, 'Online Purchase');
INSERT INTO transaction_category (tran_type_cd, tran_cat_cd, description) VALUES ('01', 3, 'Travel');
INSERT INTO transaction_category (tran_type_cd, tran_cat_cd, description) VALUES ('01', 4, 'Dining');
INSERT INTO transaction_category (tran_type_cd, tran_cat_cd, description) VALUES ('01', 5, 'Interest Charge');
INSERT INTO transaction_category (tran_type_cd, tran_cat_cd, description) VALUES ('02', 1, 'Retail Return');
INSERT INTO transaction_category (tran_type_cd, tran_cat_cd, description) VALUES ('03', 1, 'Account Payment');
INSERT INTO transaction_category (tran_type_cd, tran_cat_cd, description) VALUES ('05', 1, 'ATM Cash Advance');

-- Discount Groups (interest rates by account group, transaction type, and category)
INSERT INTO discount_group (acct_group_id, tran_type_cd, tran_cat_cd, int_rate) VALUES ('DEFAULT', '01', 1, 18.99);
INSERT INTO discount_group (acct_group_id, tran_type_cd, tran_cat_cd, int_rate) VALUES ('DEFAULT', '01', 2, 18.99);
INSERT INTO discount_group (acct_group_id, tran_type_cd, tran_cat_cd, int_rate) VALUES ('DEFAULT', '01', 3, 21.99);
INSERT INTO discount_group (acct_group_id, tran_type_cd, tran_cat_cd, int_rate) VALUES ('DEFAULT', '01', 4, 18.99);
INSERT INTO discount_group (acct_group_id, tran_type_cd, tran_cat_cd, int_rate) VALUES ('DEFAULT', '01', 5, 0.00);
INSERT INTO discount_group (acct_group_id, tran_type_cd, tran_cat_cd, int_rate) VALUES ('DEFAULT', '05', 1, 24.99);
INSERT INTO discount_group (acct_group_id, tran_type_cd, tran_cat_cd, int_rate) VALUES ('PREMIUM', '01', 1, 14.99);
INSERT INTO discount_group (acct_group_id, tran_type_cd, tran_cat_cd, int_rate) VALUES ('PREMIUM', '01', 2, 14.99);
INSERT INTO discount_group (acct_group_id, tran_type_cd, tran_cat_cd, int_rate) VALUES ('PREMIUM', '01', 3, 17.99);
INSERT INTO discount_group (acct_group_id, tran_type_cd, tran_cat_cd, int_rate) VALUES ('PREMIUM', '05', 1, 19.99);

-- Sample Customers
INSERT INTO customer (cust_id, first_name, middle_name, last_name, addr_line_1, addr_line_2, addr_line_3, state_cd, country_cd, zip, phone_1, phone_2, ssn, govt_issued_id, dob, eft_account_id, primary_card_holder_ind, fico_credit_score)
VALUES (1, 'JOHN', 'A', 'DOE', '123 MAIN STREET', 'APT 4B', '', 'NY', 'US', '10001', '2125551234', '2125555678', 123456789, 'DL12345678', '1985-03-15', '0000000001', 'Y', 750);

INSERT INTO customer (cust_id, first_name, middle_name, last_name, addr_line_1, addr_line_2, addr_line_3, state_cd, country_cd, zip, phone_1, phone_2, ssn, govt_issued_id, dob, eft_account_id, primary_card_holder_ind, fico_credit_score)
VALUES (2, 'JANE', 'B', 'SMITH', '456 OAK AVENUE', '', '', 'CA', 'US', '90210', '3105559012', '', 987654321, 'DL87654321', '1990-07-22', '0000000002', 'Y', 820);

-- Sample Accounts
INSERT INTO account (acct_id, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, current_cycle_credit, current_cycle_debit, address_zip, group_id)
VALUES (10000000001, 'Y', 1500.00, 10000.00, 2500.00, '2020-01-15', '2027-01-15', '2025-01-15', 1500.00, 0.00, '10001', 'DEFAULT');

INSERT INTO account (acct_id, active_status, current_balance, credit_limit, cash_credit_limit, open_date, expiration_date, reissue_date, current_cycle_credit, current_cycle_debit, address_zip, group_id)
VALUES (10000000002, 'Y', 3200.50, 15000.00, 5000.00, '2019-06-01', '2026-06-01', '2024-06-01', 3200.50, 0.00, '90210', 'PREMIUM');

-- Sample Card Cross-References
INSERT INTO card_xref (card_num, cust_id, acct_id) VALUES ('4111111111111111', 1, 10000000001);
INSERT INTO card_xref (card_num, cust_id, acct_id) VALUES ('4222222222222222', 2, 10000000002);

-- Sample Cards
INSERT INTO card (card_num, card_acct_id, card_cvv_cd, card_embossed_name, card_expiration_date, card_active_status)
VALUES ('4111111111111111', 10000000001, 123, 'JOHN A DOE', '2027-01-15', 'Y');

INSERT INTO card (card_num, card_acct_id, card_cvv_cd, card_embossed_name, card_expiration_date, card_active_status)
VALUES ('4222222222222222', 10000000002, 456, 'JANE B SMITH', '2026-06-01', 'Y');

-- Sample Transactions
INSERT INTO transaction (tran_id, tran_type_cd, tran_cat_cd, tran_source, tran_desc, tran_amt, merchant_id, merchant_name, merchant_city, merchant_zip, card_num, orig_timestamp, proc_timestamp)
VALUES ('0000000000000001', '01', 1, 'POS', 'GROCERY PURCHASE', 125.50, 100000001, 'WHOLE FOODS', 'NEW YORK', '10001', '4111111111111111', '2024-01-15-10.30.00.000000', '2024-01-15-22.00.00.000000');

INSERT INTO transaction (tran_id, tran_type_cd, tran_cat_cd, tran_source, tran_desc, tran_amt, merchant_id, merchant_name, merchant_city, merchant_zip, card_num, orig_timestamp, proc_timestamp)
VALUES ('0000000000000002', '01', 2, 'WEB', 'ONLINE PURCHASE', 89.99, 100000002, 'AMAZON', 'SEATTLE', '98101', '4222222222222222', '2024-01-16-14.15.00.000000', '2024-01-16-22.00.00.000000');

-- Sample Transaction Category Balances
INSERT INTO tran_cat_balance (acct_id, tran_type_cd, tran_cat_cd, balance) VALUES (10000000001, '01', 1, 1500.00);
INSERT INTO tran_cat_balance (acct_id, tran_type_cd, tran_cat_cd, balance) VALUES (10000000002, '01', 2, 3200.50);
