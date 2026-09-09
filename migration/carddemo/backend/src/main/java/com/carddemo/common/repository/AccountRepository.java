package com.carddemo.common.repository;

import com.carddemo.common.domain.AccountRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Shared CRUD access to ACCTDAT / accounts. */
@Repository
public interface AccountRepository extends JpaRepository<AccountRecord, Long> {
}
