package com.carddemo.transaction.repository;

import com.carddemo.transaction.entity.TransactionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Replaces TRANSACT VSAM KSDS file operations:
 *   - STARTBR/READPREV at HIGH-VALUES -> findTopByOrderByTranIdDesc()
 *   - WRITE TRANSACT                  -> save()
 *   - READ TRANSACT                   -> findById()
 */
@Repository
public interface TransactionRepository extends JpaRepository<TransactionRecord, String> {

    Optional<TransactionRecord> findTopByOrderByTranIdDesc();
}
