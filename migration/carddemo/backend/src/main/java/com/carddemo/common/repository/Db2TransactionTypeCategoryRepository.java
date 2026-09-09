package com.carddemo.common.repository;

import com.carddemo.common.domain.Db2TransactionTypeCategoryRecord;
import com.carddemo.common.domain.Db2TransactionTypeCategoryId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Shared CRUD access to DB2 CARDDEMO.TRANSACTION_TYPE_CATEGORY / db2_transaction_type_category. */
@Repository
public interface Db2TransactionTypeCategoryRepository extends JpaRepository<Db2TransactionTypeCategoryRecord, Db2TransactionTypeCategoryId> {
}
