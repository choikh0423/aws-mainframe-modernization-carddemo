package com.carddemo.common.repository;

import com.carddemo.common.domain.AuthFraudRecord;
import com.carddemo.common.domain.AuthFraudId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Shared CRUD access to DB2 CARDDEMO.AUTHFRDS / auth_fraud. */
@Repository
public interface AuthFraudRepository extends JpaRepository<AuthFraudRecord, AuthFraudId> {
}
