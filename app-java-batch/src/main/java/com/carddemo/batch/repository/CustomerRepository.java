package com.carddemo.batch.repository;

import com.carddemo.batch.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for Customer entities.
 * Replaces VSAM KSDS CUSTFILE sequential access.
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
