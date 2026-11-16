package com.carddemo.statements;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * The statement archive. Listing is newest cycle first, which the
 * {@code (acct_id, cycle DESC)} index serves.
 */
public interface StatementArchiveRepository extends JpaRepository<StatementArchiveRecord, Long> {

    Page<StatementArchiveRecord> findByAcctIdOrderByCycleDesc(Long acctId, Pageable pageable);

    Optional<StatementArchiveRecord> findByCardNumAndCycle(String cardNum, String cycle);

    long countByCycle(String cycle);

    /** Drops the cycle's rows that the run stamped {@code generatedTs} did not write. */
    @Transactional
    @Modifying
    @Query("delete from StatementArchiveRecord s where s.cycle = :cycle and s.generatedTs <> :generatedTs")
    int deleteOtherRuns(@Param("cycle") String cycle, @Param("generatedTs") LocalDateTime generatedTs);
}
