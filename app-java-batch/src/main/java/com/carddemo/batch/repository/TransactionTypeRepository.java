package com.carddemo.batch.repository;

import com.carddemo.batch.model.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for TransactionType entities.
 * Replaces VSAM KSDS TRANTYPE random access.
 */
@Repository
public interface TransactionTypeRepository extends JpaRepository<TransactionType, String> {
}
