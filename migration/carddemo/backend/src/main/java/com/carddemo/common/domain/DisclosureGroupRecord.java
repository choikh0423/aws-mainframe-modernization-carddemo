package com.carddemo.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * DISCGRP VSAM KSDS record CVTRA02Y DIS-GROUP-RECORD (RECLN 50).
 */
@Entity
@Table(name = "disclosure_groups")
public class DisclosureGroupRecord {

    @EmbeddedId
    private DisclosureGroupId id;

    @Column(name = "int_rate", precision = 6, scale = 2, nullable = false)
    private BigDecimal intRate;

    public DisclosureGroupRecord() {
    }

    public DisclosureGroupId getId() { return id; }
    public void setId(DisclosureGroupId id) { this.id = id; }

    public BigDecimal getIntRate() { return intRate; }
    public void setIntRate(BigDecimal intRate) { this.intRate = intRate; }
}
