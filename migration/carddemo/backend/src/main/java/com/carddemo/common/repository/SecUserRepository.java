package com.carddemo.common.repository;

import com.carddemo.common.domain.SecUserRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Shared CRUD access to USRSEC / sec_users. */
@Repository
public interface SecUserRepository extends JpaRepository<SecUserRecord, String> {
}
