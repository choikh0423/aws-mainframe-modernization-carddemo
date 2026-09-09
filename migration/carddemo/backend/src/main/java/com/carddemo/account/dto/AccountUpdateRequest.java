package com.carddemo.account.dto;

/**
 * ENTER or F5 on the CAUP screen. {@code original} is the snapshot the fetch returned
 * (ACUP-OLD-DETAILS) and {@code updated} is what the operator typed (ACUP-NEW-DETAILS);
 * COACTUPC carries both in its private commarea (COACTUPC:380-870).
 */
public class AccountUpdateRequest {

    public AccountFields original;
    public AccountFields updated;
}
