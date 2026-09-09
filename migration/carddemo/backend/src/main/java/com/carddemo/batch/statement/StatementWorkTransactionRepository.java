package com.carddemo.batch.statement;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The TRXFL work KSDS. Reads are issued in key order (card number, transaction
 * id), which is what ORGANIZATION INDEXED ACCESS SEQUENTIAL gives CBSTM03B
 * (CBSTM03B.CBL:31-35); {@code deleteAllInBatch} is the IDCAMS DELETE of the
 * cluster in CREASTMT.JCL DELDEF01.
 */
public interface StatementWorkTransactionRepository
        extends JpaRepository<StatementWorkTransaction, StatementWorkTransactionKey> {
}
