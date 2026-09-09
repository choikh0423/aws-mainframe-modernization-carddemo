package com.carddemo.common.repository;

import com.carddemo.common.domain.CardXrefRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Replaces both CCXREF (primary key) and CXACAIX (alternate index) VSAM files:
 *   - READ CCXREF  by XREF-CARD-NUM  -> findById() (by card_num)
 *   - READ CXACAIX by XREF-ACCT-ID   -> findByAcctId()
 */
@Repository
public interface CardXrefRepository extends JpaRepository<CardXrefRecord, String> {

    Optional<CardXrefRecord> findByAcctId(Long acctId);
}
