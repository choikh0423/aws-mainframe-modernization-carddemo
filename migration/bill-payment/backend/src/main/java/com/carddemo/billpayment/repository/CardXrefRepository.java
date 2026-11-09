package com.carddemo.billpayment.repository;

import com.carddemo.billpayment.entity.CardXref;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardXrefRepository extends JpaRepository<CardXref, String> {

    /** EXEC CICS READ DATASET(CXACAIX): first card on the account alternate index. */
    Optional<CardXref> findFirstByAcctIdOrderByCardNumAsc(String acctId);
}
