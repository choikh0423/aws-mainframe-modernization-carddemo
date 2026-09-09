package com.carddemo.common.repository;

import com.carddemo.common.domain.CardRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Shared CRUD access to CARDDAT / cards. */
@Repository
public interface CardRepository extends JpaRepository<CardRecord, String> {
}
