package com.carddemo.billpayment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * PICTURE-accurate helpers for the CB00 screen and the payment record.
 *
 * <p>COBIL00C edits its balance through {@code WS-CURR-BAL PIC +9999999999.99}
 * (cbl:56) — two digits wider than the shared {@code CobolFormat.amountEdited}
 * helper, which renders the CT01 {@code PIC +99999999.99} field — and moves the
 * balance into {@code TRAN-AMT PIC S9(09)V99} (cbl:224), one digit narrower.
 */
public final class BillPaymentFormat {

    /** ACCT-CURR-BAL is S9(10)V99 (app/cpy/CVACT01Y.cpy). */
    private static final int CURR_BAL_DIGITS = 10;
    /** TRAN-AMT is S9(09)V99 (app/cpy/CVTRA05Y.cpy). */
    private static final BigDecimal TRAN_AMT_MODULUS = new BigDecimal("1000000000");
    /** TRAN-ID is a 16-char zero-padded numeric key (app/cpy/CVTRA05Y.cpy). */
    private static final int TRAN_ID_LEN = 16;

    /**
     * COBIL00C's timestamp layout: {@code WS-TIMESTAMP} (app/cpy/CSDAT01Y.cpy)
     * filled from FORMATTIME with DATESEP('-') and TIMESEP(':') (cbl:255-266).
     * Position 11 keeps the copybook's blank FILLER because INITIALIZE does not
     * touch it, and the microseconds are explicitly zeroed.
     */
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private BillPaymentFormat() {
    }

    /**
     * Renders the balance exactly like {@code MOVE ACCT-CURR-BAL TO WS-CURR-BAL}
     * (cbl:193-194): a leading sign, ten zero-padded integer digits, a literal
     * '.', two decimals. Example: {@code 194.00 -> "+0000000194.00"}.
     */
    public static String currBalEdited(BigDecimal balance) {
        BigDecimal abs = balance.abs().setScale(2, java.math.RoundingMode.DOWN);
        String[] parts = abs.toPlainString().split("\\.");
        String digits = parts[0];
        if (digits.length() > CURR_BAL_DIGITS) {
            // A numeric MOVE truncates on the left, exactly as the COBOL edit does.
            digits = digits.substring(digits.length() - CURR_BAL_DIGITS);
        }
        return (balance.signum() < 0 ? "-" : "+")
                + String.format("%0" + CURR_BAL_DIGITS + "d", Long.parseLong(digits))
                + "." + parts[1];
    }

    /**
     * {@code MOVE ACCT-CURR-BAL TO TRAN-AMT} (cbl:224) moves S9(10)V99 into
     * S9(09)V99: the sign and both decimals survive, the tenth integer digit is
     * truncated (quirk Q-1). {@code 1234567890.12 -> 234567890.12}.
     */
    public static BigDecimal tranAmount(BigDecimal balance) {
        return balance.setScale(2, java.math.RoundingMode.DOWN).remainder(TRAN_AMT_MODULUS);
    }

    /** {@code MOVE WS-TRAN-ID-NUM TO TRAN-ID}: 16 zero-padded digits (cbl:216-219). */
    public static String tranId(long value) {
        return String.format("%0" + TRAN_ID_LEN + "d", value);
    }

    /** {@code GET-CURRENT-TIMESTAMP} (cbl:249-267): {@code YYYY-MM-DD HH:MM:SS.000000}. */
    public static String timestamp(LocalDateTime now) {
        return TIMESTAMP.format(now) + ".000000";
    }
}
