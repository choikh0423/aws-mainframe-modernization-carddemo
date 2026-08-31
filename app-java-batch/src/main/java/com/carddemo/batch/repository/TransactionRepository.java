package com.carddemo.batch.repository;

import com.carddemo.batch.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for Transaction entities.
 * Replaces VSAM KSDS TRANFILE random and sequential access.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {

    /**
     * Find the transaction with the highest transaction ID (for ID generation).
     */
    Optional<Transaction> findTopByOrderByTranIdDesc();

    /**
     * Find transactions within a date range sorted by card number.
     * Replaces the JCL SORT + INCLUDE COND in TRANREPT.
     */
    @Query("SELECT t FROM Transaction t WHERE SUBSTRING(t.procTimestamp, 1, 10) >= :startDate " +
           "AND SUBSTRING(t.procTimestamp, 1, 10) <= :endDate ORDER BY t.cardNum, t.tranId")
    List<Transaction> findByProcTimestampDateRange(
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);
}
