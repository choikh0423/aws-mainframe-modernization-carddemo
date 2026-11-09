package com.carddemo.billpayment.repository;

import com.carddemo.billpayment.entity.Account;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AccountRepository extends JpaRepository<Account, String> {

    /** EXEC CICS READ DATASET(ACCTDAT) UPDATE: read and hold the record for REWRITE. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.acctId = :acctId")
    Optional<Account> findForUpdate(String acctId);
}
