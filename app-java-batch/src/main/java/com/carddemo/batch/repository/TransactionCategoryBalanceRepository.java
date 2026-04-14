package com.carddemo.batch.repository;

import com.carddemo.batch.model.TransactionCategoryBalance;
import com.carddemo.batch.model.TransactionCategoryBalanceId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for TransactionCategoryBalance entities.
 * Replaces VSAM KSDS TCATBALF random and sequential access.
 */
@Repository
public interface TransactionCategoryBalanceRepository
        extends JpaRepository<TransactionCategoryBalance, TransactionCategoryBalanceId> {

    Optional<TransactionCategoryBalance> findByAcctIdAndTranTypeCdAndTranCatCd(
            Long acctId, String tranTypeCd, Integer tranCatCd);
}
