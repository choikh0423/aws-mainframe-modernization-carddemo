-- S-09 PendingAuthorizations (band V900-V999), PostgreSQL dialect.
--
-- AUTHFRDS.POS_ENTRY_MODE is SMALLINT (PIC S9(4) COMP). PostgreSQL refuses a
-- CHAR -> SMALLINT change without an explicit USING expression; blank-padded
-- and empty values become NULL rather than failing the migration.
-- The H2 form of this migration lives beside it under db/vendor/h2.

ALTER TABLE auth_fraud
    ALTER COLUMN pos_entry_mode SET DATA TYPE SMALLINT
        USING NULLIF(TRIM(pos_entry_mode), '')::smallint;
