package com.carddemo.batch.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Service for date formatting and validation.
 * Replaces the COBOL Z-GET-DB2-FORMAT-TIMESTAMP paragraph.
 */
@Service
public class DateValidationService {

    private static final DateTimeFormatter DB2_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SS'0000'");

    /**
     * Generates a DB2-format timestamp string: YYYY-MM-DD-HH.MM.SS.HH0000
     * Ported from COBOL Z-GET-DB2-FORMAT-TIMESTAMP.
     */
    public String generateDb2Timestamp() {
        return LocalDateTime.now().format(DB2_TIMESTAMP_FORMAT);
    }

    /**
     * Checks if the account expiration date is on or after the transaction date.
     * Ported from CBTRN02C line 414: IF ACCT-EXPIRAION-DATE >= DALYTRAN-ORIG-TS(1:10)
     *
     * @param expirationDate account expiration date (YYYY-MM-DD)
     * @param transactionOrigTimestamp full transaction timestamp (first 10 chars used)
     * @return true if the account has not expired
     */
    public boolean isAccountNotExpired(String expirationDate, String transactionOrigTimestamp) {
        if (expirationDate == null || transactionOrigTimestamp == null) {
            return false;
        }
        String tranDate = transactionOrigTimestamp.length() >= 10
                ? transactionOrigTimestamp.substring(0, 10) : transactionOrigTimestamp;
        return expirationDate.compareTo(tranDate) >= 0;
    }
}
