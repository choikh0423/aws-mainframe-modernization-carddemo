package com.carddemo.common.repository;

import com.carddemo.common.domain.TransactionTypeRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Shared CRUD access to TRANTYPE / transaction_types. */
@Repository
public interface TransactionTypeRepository extends JpaRepository<TransactionTypeRecord, String> {
}
