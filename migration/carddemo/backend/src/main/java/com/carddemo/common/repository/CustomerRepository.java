package com.carddemo.common.repository;

import com.carddemo.common.domain.CustomerRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Shared CRUD access to CUSTDAT / customers. */
@Repository
public interface CustomerRepository extends JpaRepository<CustomerRecord, Long> {
}
