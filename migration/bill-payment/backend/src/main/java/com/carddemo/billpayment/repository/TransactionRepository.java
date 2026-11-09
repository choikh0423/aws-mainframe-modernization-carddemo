package com.carddemo.billpayment.repository;

import com.carddemo.billpayment.entity.Transaction;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TransactionRepository extends JpaRepository<Transaction, String> {

    /** STARTBR RIDFLD(HIGH-VALUES) + READPREV: the highest TRAN-ID on file. */
    Optional<Transaction> findTopByOrderByTranIdDesc();

    @Query("select t from Transaction t order by t.tranId desc")
    List<Transaction> findLatest(Pageable page);
}
