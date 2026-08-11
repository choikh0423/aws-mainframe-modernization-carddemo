package com.carddemo.transaction.repository;

import com.carddemo.transaction.domain.Transaction;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, String> {

    /** Equivalent of STARTBR(HIGH-VALUES) + READPREV on the TRANSACT file. */
    Optional<Transaction> findFirstByOrderByTranIdDesc();
}
