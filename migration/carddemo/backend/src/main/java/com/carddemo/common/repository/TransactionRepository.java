package com.carddemo.common.repository;

import com.carddemo.common.domain.TransactionRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Replaces TRANSACT VSAM KSDS operations used across CT00/CT01/CT02:
 *   - READ TRANSACT by key            -> findById()
 *   - WRITE TRANSACT                  -> save()
 *   - STARTBR + READNEXT (page fwd)   -> findAllByOrderByIdAsc / findByIdGreaterThanOrderByIdAsc
 *   - STARTBR GTEQ + READNEXT (filter)-> findByIdGreaterThanEqualOrderByIdAsc (CT00 start-at)
 *   - STARTBR + READPREV (page back)  -> findByIdLessThanOrderByIdDesc
 *   - STARTBR HIGH-VALUES + READPREV  -> findTopByOrderByIdDesc (max key for CT02 key-gen)
 *
 * TRAN-ID is a zero-padded 16-digit numeric string, so lexicographic ordering
 * on {@code id} matches the numeric VSAM key ordering.
 */
@Repository
public interface TransactionRepository extends JpaRepository<TransactionRecord, String> {

    List<TransactionRecord> findAllByOrderByIdAsc(Pageable pageable);

    List<TransactionRecord> findByIdGreaterThanOrderByIdAsc(String id, Pageable pageable);

    List<TransactionRecord> findByIdGreaterThanEqualOrderByIdAsc(String id, Pageable pageable);

    List<TransactionRecord> findByIdLessThanOrderByIdDesc(String id, Pageable pageable);

    Optional<TransactionRecord> findTopByOrderByIdDesc();
}
