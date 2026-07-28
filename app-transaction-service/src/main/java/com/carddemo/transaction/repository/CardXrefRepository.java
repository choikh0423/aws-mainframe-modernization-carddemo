package com.carddemo.transaction.repository;

import com.carddemo.transaction.entity.CardXref;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardXrefRepository extends JpaRepository<CardXref, String> {

    /** READ-CCXREF-FILE: read CCXREF by card number (primary key). */
    Optional<CardXref> findByCardNum(String cardNum);

    /** READ-CXACAIX-FILE: read via the CXACAIX alternate index on account id. */
    Optional<CardXref> findByAcctId(Long acctId);
}
