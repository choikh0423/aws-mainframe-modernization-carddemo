package com.carddemo.batch.repository;

import com.carddemo.batch.model.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for Card entities.
 * Replaces VSAM KSDS CARDFILE sequential access.
 */
@Repository
public interface CardRepository extends JpaRepository<Card, String> {
}
