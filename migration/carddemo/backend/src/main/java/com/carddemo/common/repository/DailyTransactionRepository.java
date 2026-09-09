package com.carddemo.common.repository;

import com.carddemo.common.domain.DailyTransactionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Shared CRUD access to DALYTRAN / daily_transactions. */
@Repository
public interface DailyTransactionRepository extends JpaRepository<DailyTransactionRecord, String> {
}
