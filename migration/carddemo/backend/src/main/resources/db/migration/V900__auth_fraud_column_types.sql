-- S-09 PendingAuthorizations (band V900-V999).
--
-- Align auth_fraud with CARDDEMO.AUTHFRDS
-- (app/app-authorization-ims-db2-mq/ddl/AUTHFRDS.ddl) and its DCLGEN host
-- variables (app/app-authorization-ims-db2-mq/dcl/AUTHFRDS.dcl).
--
--   TRANSACTION_AMT / APPROVED_AMT  DECIMAL(12,2)  PIC S9(10)V9(2) COMP-3
--   POS_ENTRY_MODE                  SMALLINT       PIC S9(4) COMP
--   MERCHANT_NAME                   VARCHAR(22)    length-prefixed group
--   FRAUD_RPT_DATE                  DATE           set to CURRENT DATE by
--                                                  COPAUS2C.cbl:194 and :225
--
-- FRAUD_RPT_DATE is rebuilt rather than cast: the previous CHAR(8) held the
-- MMDDYY screen rendering, whose century cannot be recovered. The MMDDYY value
-- belongs to the separate IMS field PA-FRAUD-RPT-DATE (CIPAUDTY.cpy:53), which
-- is modelled by pending_auth_detail.pa_fraud_rpt_date and is left untouched.

ALTER TABLE auth_fraud ALTER COLUMN transaction_amt SET DATA TYPE NUMERIC(12,2);
ALTER TABLE auth_fraud ALTER COLUMN approved_amt SET DATA TYPE NUMERIC(12,2);
ALTER TABLE auth_fraud ALTER COLUMN merchant_name SET DATA TYPE VARCHAR(22);

ALTER TABLE auth_fraud DROP COLUMN pos_entry_mode;
ALTER TABLE auth_fraud ADD COLUMN pos_entry_mode SMALLINT;

ALTER TABLE auth_fraud DROP COLUMN fraud_rpt_date;
ALTER TABLE auth_fraud ADD COLUMN fraud_rpt_date DATE;
