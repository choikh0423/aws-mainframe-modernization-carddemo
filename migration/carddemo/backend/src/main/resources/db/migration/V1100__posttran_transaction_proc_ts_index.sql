-- S-11 DailyTransactionPosting.
--
-- TRANIDX.jcl (lines 173-182) defines a non-unique alternate index over the
-- 26-byte processing timestamp at offset 304 of the TRANSACT record and a path
-- over it, so the transactions POSTTRAN writes can be retrieved in processing
-- timestamp order. The migrated equivalent of that AIX is a plain index; no
-- separate path object is needed because the base table is read directly.
CREATE INDEX idx_transactions_proc_ts ON transactions (proc_ts);
