package com.carddemo.transaction.repository;

import com.carddemo.transaction.domain.CardXref;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardXrefRepository extends JpaRepository<CardXref, String> {

    /** Equivalent of a READ against the CXACAIX alternate index. */
    Optional<CardXref> findFirstByXrefAcctId(String xrefAcctId);
}
