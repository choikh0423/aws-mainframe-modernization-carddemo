package com.carddemo.pendingauth.service;

import com.carddemo.common.domain.AuthFraudId;
import com.carddemo.common.domain.AuthFraudRecord;
import com.carddemo.common.domain.PendingAuthDetailRecord;
import com.carddemo.common.repository.AuthFraudRepository;
import com.carddemo.pendingauth.util.PendingAuthKey;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * COPAUS2C — the fraud reporting module CPVD reaches through
 * {@code EXEC CICS LINK PROGRAM(WS-PGM-AUTH-FRAUD)} (COPAUS1C.cbl:35, 248-251).
 *
 * <p>The program inserts the authorization into {@code CARDDEMO.AUTHFRDS} and,
 * when the row already exists ({@code SQLCODE -803}), updates the fraud flag and
 * report date instead (COPAUS2C.cbl:144-235). The DB2 table is the JPA entity
 * {@code AuthFraudRecord} (B-09); "insert, fall back to update on duplicate key"
 * becomes an existence check on the same primary key, which is the same end
 * state and the same two messages.
 */
@Service
public class AuthFraudService {

    /** {@code MMDDYY} with {@code DATESEP}: the CICS FORMATTIME report date. */
    private static final DateTimeFormatter RPT_DATE = DateTimeFormatter.ofPattern("MM/dd/yy");

    private final AuthFraudRepository authFraudRepository;
    private final Clock clock;

    public AuthFraudService(AuthFraudRepository authFraudRepository, Clock clock) {
        this.authFraudRepository = authFraudRepository;
        this.clock = clock;
    }

    /** {@code WS-FRD-ACTION}: 'F' reports fraud, 'R' removes it. */
    public enum Action {
        REPORT_FRAUD("F"),
        REMOVE_FRAUD("R");

        private final String code;

        Action(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    /**
     * {@code WS-FRAUD-STATUS-RECORD} as returned through the COMMAREA.
     *
     * @param success      WS-FRD-UPDATE-STATUS = 'S'
     * @param message      WS-FRD-ACT-MSG
     * @param fraudRptDate the report date COPAUS2C stamps onto the record
     */
    public record FraudResult(boolean success, String message, String fraudRptDate) {
    }

    /**
     * MAIN-PARA: stamp the report date, rebuild the authorization timestamp from
     * the segment key, then insert or update AUTHFRDS.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public FraudResult report(Long acctId, Long custId, PendingAuthDetailRecord detail,
                              Action action) {
        String fraudRptDate = LocalDate.now(clock).format(RPT_DATE);
        AuthFraudId id = new AuthFraudId(detail.getPaCardNum(), authTimestamp(detail));

        Optional<AuthFraudRecord> existing = authFraudRepository.findById(id);
        if (existing.isPresent()) {
            AuthFraudRecord row = existing.get();
            row.setAuthFraud(action.code());
            row.setFraudRptDate(fraudRptDate);
            authFraudRepository.save(row);
            return new FraudResult(true, PendingAuthMessages.DB2_UPDT_SUCCESS, fraudRptDate);
        }

        AuthFraudRecord row = new AuthFraudRecord();
        row.setId(id);
        row.setAuthType(detail.getPaAuthType());
        row.setCardExpiryDate(detail.getPaCardExpiryDate());
        row.setMessageType(detail.getPaMessageType());
        row.setMessageSource(detail.getPaMessageSource());
        row.setAuthIdCode(detail.getPaAuthIdCode());
        row.setAuthRespCode(detail.getPaAuthRespCode());
        row.setAuthRespReason(detail.getPaAuthRespReason());
        row.setProcessingCode(processingCode(detail));
        row.setTransactionAmt(detail.getPaTransactionAmt());
        row.setApprovedAmt(detail.getPaApprovedAmt());
        row.setMerchantCatagoryCode(detail.getPaMerchantCatagoryCode());
        row.setAcqrCountryCode(detail.getPaAcqrCountryCode());
        row.setPosEntryMode(posEntryMode(detail));
        row.setMerchantId(detail.getPaMerchantId());
        row.setMerchantName(detail.getPaMerchantName());
        row.setMerchantCity(detail.getPaMerchantCity());
        row.setMerchantState(detail.getPaMerchantState());
        row.setMerchantZip(detail.getPaMerchantZip());
        row.setTransactionId(detail.getPaTransactionId());
        row.setMatchStatus(detail.getPaMatchStatus());
        row.setAuthFraud(action.code());
        row.setFraudRptDate(fraudRptDate);
        row.setAcctId(acctId);
        row.setCustId(custId);
        authFraudRepository.save(row);
        return new FraudResult(true, PendingAuthMessages.DB2_ADD_SUCCESS, fraudRptDate);
    }

    /**
     * {@code AUTH_TS}: the original authorization date {@code YY-MM-DD} plus the
     * de-inverted time {@code HH.MI.SSsss} (COPAUS2C.cbl:100-113). Two digit
     * years are 20xx, the only century the estate's data uses (BD-3).
     */
    static LocalDateTime authTimestamp(PendingAuthDetailRecord detail) {
        String origDate = detail.getPaAuthOrigDate() == null ? "" : detail.getPaAuthOrigDate().trim();
        int year = 2000 + Integer.parseInt(origDate.substring(0, 2));
        int month = Integer.parseInt(origDate.substring(2, 4));
        int day = Integer.parseInt(origDate.substring(4, 6));

        long time = PendingAuthKey.TIME_COMPLEMENT - detail.getId().getPaAuthTime9c();
        String t = String.format("%09d", time);
        int hour = Integer.parseInt(t.substring(0, 2));
        int minute = Integer.parseInt(t.substring(2, 4));
        int second = Integer.parseInt(t.substring(4, 6));
        int millis = Integer.parseInt(t.substring(6, 9));
        return LocalDateTime.of(year, month, day, hour, minute, second, millis * 1_000_000);
    }

    private String processingCode(PendingAuthDetailRecord detail) {
        return detail.getPaProcessingCode() == null
                ? null : String.format("%06d", detail.getPaProcessingCode());
    }

    private String posEntryMode(PendingAuthDetailRecord detail) {
        return detail.getPaPosEntryMode() == null
                ? null : String.format("%02d", detail.getPaPosEntryMode());
    }
}
