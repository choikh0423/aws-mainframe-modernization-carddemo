package com.carddemo.batch.repository;

import com.carddemo.batch.model.TransactionCategory;
import com.carddemo.batch.model.TransactionCategoryId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for TransactionCategory entities.
 * Replaces VSAM KSDS TRANCATG random access.
 */
@Repository
public interface TransactionCategoryRepository
        extends JpaRepository<TransactionCategory, TransactionCategoryId> {

    Optional<TransactionCategory> findByTranTypeCdAndTranCatCd(String tranTypeCd, Integer tranCatCd);
}
