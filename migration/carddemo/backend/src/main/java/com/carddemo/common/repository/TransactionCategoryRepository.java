package com.carddemo.common.repository;

import com.carddemo.common.domain.TransactionCategoryRecord;
import com.carddemo.common.domain.TransactionCategoryId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Shared CRUD access to TRANCATG / transaction_categories. */
@Repository
public interface TransactionCategoryRepository extends JpaRepository<TransactionCategoryRecord, TransactionCategoryId> {
}
