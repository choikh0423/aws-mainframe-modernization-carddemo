package com.carddemo.batch.repository;

import com.carddemo.batch.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for Account entities.
 * Replaces VSAM KSDS ACCTFILE random and sequential access.
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
}
