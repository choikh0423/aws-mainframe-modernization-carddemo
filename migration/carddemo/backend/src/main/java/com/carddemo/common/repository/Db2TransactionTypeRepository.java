package com.carddemo.common.repository;

import com.carddemo.common.domain.Db2TransactionTypeRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Shared CRUD access to DB2 CARDDEMO.TRANSACTION_TYPE / db2_transaction_type. */
@Repository
public interface Db2TransactionTypeRepository extends JpaRepository<Db2TransactionTypeRecord, String> {
}
