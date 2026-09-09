package com.carddemo.common.repository;

import com.carddemo.common.domain.PendingAuthDetailRecord;
import com.carddemo.common.domain.PendingAuthDetailId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Shared CRUD access to IMS PAUTDTL1 / pending_auth_detail. */
@Repository
public interface PendingAuthDetailRepository extends JpaRepository<PendingAuthDetailRecord, PendingAuthDetailId> {
}
