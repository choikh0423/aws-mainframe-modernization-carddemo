package com.carddemo.batch.repository;

import com.carddemo.batch.model.CardXref;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for CardXref entities.
 * Replaces VSAM KSDS XREFFILE random access by card number
 * and alternate key access by account ID.
 */
@Repository
public interface CardXrefRepository extends JpaRepository<CardXref, String> {

    Optional<CardXref> findByCardNum(String cardNum);

    List<CardXref> findByAcctId(Long acctId);
}
