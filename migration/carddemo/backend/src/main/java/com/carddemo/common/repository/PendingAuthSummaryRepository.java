package com.carddemo.common.repository;

import com.carddemo.common.domain.PendingAuthSummaryRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Shared CRUD access to IMS PAUTSUM0 / pending_auth_summary. */
@Repository
public interface PendingAuthSummaryRepository extends JpaRepository<PendingAuthSummaryRecord, Long> {
}
