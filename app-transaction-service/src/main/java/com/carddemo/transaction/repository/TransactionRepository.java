package com.carddemo.transaction.repository;

import com.carddemo.transaction.entity.Transaction;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TransactionRepository extends JpaRepository<Transaction, String> {

    /**
     * Highest TRAN-ID currently on file. Equivalent to STARTBR at HIGH-VALUES
     * followed by READPREV in COTRN02C (ADD-TRANSACTION).
     */
    @Query("select max(t.tranId) from Transaction t")
    Optional<String> findMaxTranId();
}
