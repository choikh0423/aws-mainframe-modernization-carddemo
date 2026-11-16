package com.carddemo.statements;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * The statement archive. Listing is newest cycle first, which the
 * {@code (acct_id, cycle DESC)} index serves.
 */
public interface StatementArchiveRepository extends JpaRepository<StatementArchiveRecord, Long> {

    Page<StatementArchiveRecord> findByAcctIdOrderByCycleDesc(Long acctId, Pageable pageable);

    Optional<StatementArchiveRecord> findByCardNumAndCycle(String cardNum, String cycle);
}
