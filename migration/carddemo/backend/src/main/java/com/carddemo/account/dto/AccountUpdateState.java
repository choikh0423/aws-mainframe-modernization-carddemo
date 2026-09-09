package com.carddemo.account.dto;

/**
 * The CAUP state machine (COACTUPC:544-563, decided in 2000-DECIDE-ACTION at COACTUPC:2562-2648).
 * The legacy program keeps it in its private commarea; here it travels in every response so the
 * React screen can drive ENTER / F5 / F12 exactly as the 3270 screen does.
 */
public enum AccountUpdateState {

    /** ACUP-DETAILS-NOT-FETCHED — only the account id is enterable. */
    DETAILS_NOT_FETCHED,
    /** ACUP-SHOW-DETAILS — details fetched, fields enterable. */
    SHOW_DETAILS,
    /** ACUP-CHANGES-NOT-OK — an edit failed; the typed values stay on screen. */
    CHANGES_NOT_OK,
    /** ACUP-CHANGES-OK-NOT-CONFIRMED — every edit passed, waiting for F5. */
    CHANGES_OK_NOT_CONFIRMED,
    /** ACUP-CHANGES-OKAYED-AND-DONE — both rows rewritten. */
    CHANGES_OKAYED_AND_DONE
}
