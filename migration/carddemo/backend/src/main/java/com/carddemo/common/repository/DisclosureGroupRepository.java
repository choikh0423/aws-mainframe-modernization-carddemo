package com.carddemo.common.repository;

import com.carddemo.common.domain.DisclosureGroupRecord;
import com.carddemo.common.domain.DisclosureGroupId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Shared CRUD access to DISCGRP / disclosure_groups. */
@Repository
public interface DisclosureGroupRepository extends JpaRepository<DisclosureGroupRecord, DisclosureGroupId> {
}
