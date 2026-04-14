package com.carddemo.batch.repository;

import com.carddemo.batch.model.DiscountGroup;
import com.carddemo.batch.model.DiscountGroupId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for DiscountGroup entities.
 * Replaces VSAM KSDS DISCGRP random access.
 */
@Repository
public interface DiscountGroupRepository extends JpaRepository<DiscountGroup, DiscountGroupId> {

    Optional<DiscountGroup> findByAcctGroupIdAndTranTypeCdAndTranCatCd(
            String acctGroupId, String tranTypeCd, Integer tranCatCd);
}
