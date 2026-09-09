package com.carddemo.common.repository;

import com.carddemo.common.domain.TransactionCategoryBalanceRecord;
import com.carddemo.common.domain.TransactionCategoryBalanceId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Shared CRUD access to TCATBALF / transaction_category_balances. */
@Repository
public interface TransactionCategoryBalanceRepository extends JpaRepository<TransactionCategoryBalanceRecord, TransactionCategoryBalanceId> {
}
