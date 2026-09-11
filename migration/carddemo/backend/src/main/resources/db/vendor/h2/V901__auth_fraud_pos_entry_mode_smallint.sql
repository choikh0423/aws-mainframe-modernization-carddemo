-- S-09 PendingAuthorizations (band V900-V999), H2 dialect.
--
-- AUTHFRDS.POS_ENTRY_MODE is SMALLINT (PIC S9(4) COMP). H2 converts the
-- existing CHAR(2) values in place, so no USING clause is needed or accepted.
-- The PostgreSQL form of this migration lives beside it under db/vendor/postgresql.

ALTER TABLE auth_fraud ALTER COLUMN pos_entry_mode SET DATA TYPE SMALLINT;
