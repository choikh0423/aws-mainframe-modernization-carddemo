package com.carddemo.pendingauth.service;

import com.carddemo.common.domain.CardXrefRecord;
import com.carddemo.common.domain.PendingAuthDetailId;
import com.carddemo.common.domain.PendingAuthDetailRecord;
import com.carddemo.common.repository.CardXrefRepository;
import com.carddemo.common.repository.PendingAuthDetailRepository;
import com.carddemo.common.repository.PendingAuthSummaryRepository;
import com.carddemo.pendingauth.dto.PendingAuthDetailResponse;
import com.carddemo.pendingauth.repository.PendingAuthDetailBrowseRepository;
import com.carddemo.pendingauth.util.PendingAuthFormat;
import com.carddemo.pendingauth.util.PendingAuthKey;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * CPVD / COPAUS1C — the pending authorization detail screen, its PF8 walk to
 * the next authorization and its PF5 fraud toggle.
 */
@Service
public class PendingAuthDetailService {

    /** {@code WS-DECLINE-REASON-TABLE} (COPAUS1C.cbl:57-73), verbatim. */
    private static final Map<String, String> DECLINE_REASONS = declineReasons();

    private final PendingAuthSummaryRepository summaryRepository;
    private final PendingAuthDetailRepository detailRepository;
    private final PendingAuthDetailBrowseRepository browseRepository;
    private final CardXrefRepository cardXrefRepository;
    private final AuthFraudService authFraudService;

    public PendingAuthDetailService(PendingAuthSummaryRepository summaryRepository,
                                    PendingAuthDetailRepository detailRepository,
                                    PendingAuthDetailBrowseRepository browseRepository,
                                    CardXrefRepository cardXrefRepository,
                                    AuthFraudService authFraudService) {
        this.summaryRepository = summaryRepository;
        this.detailRepository = detailRepository;
        this.browseRepository = browseRepository;
        this.cardXrefRepository = cardXrefRepository;
        this.authFraudService = authFraudService;
    }

    /**
     * PROCESS-ENTER-KEY: with a numeric account id and a non-blank selected key
     * the segment is read and rendered; otherwise ERR-FLG goes on and the detail
     * area stays blank with no message at all (quirk Q-2, COPAUS1C.cbl:228-246).
     */
    @Transactional(readOnly = true)
    public PendingAuthDetailResponse view(String acctIdInput, String authKey) {
        return read(acctIdInput, authKey)
                .map(this::render)
                .orElseGet(() -> PendingAuthDetailResponse.empty(null));
    }

    /**
     * PROCESS-PF8-KEY: read the selected segment, then {@code GNP} for the next
     * one. At the end of the chain the screen keeps the authorization it was
     * showing and only the message changes (COPAUS1C.cbl:269-289).
     */
    @Transactional(readOnly = true)
    public PendingAuthDetailResponse next(String acctIdInput, String authKey) {
        Optional<PendingAuthDetailRecord> current = read(acctIdInput, authKey);
        if (current.isEmpty()) {
            return PendingAuthDetailResponse.empty(null);
        }
        PendingAuthDetailId id = current.get().getId();
        List<PendingAuthDetailRecord> following = browseRepository.browseAfter(
                id.getPaAcctId(), id.getPaAuthDate9c(), id.getPaAuthTime9c(), PageRequest.of(0, 1));
        if (following.isEmpty()) {
            return render(current.get()).withMessage(PendingAuthMessages.LAST_AUTHORIZATION);
        }
        return render(following.get(0));
    }

    /**
     * MARK-AUTH-FRAUD (COPAUS1C.cbl:238-266): flip {@code PA-AUTH-FRAUD} between
     * 'F' and 'R', hand the record to COPAUS2C for the DB2 write and only then
     * {@code REPL} the IMS segment. A failed DB2 update rolls the unit of work
     * back and shows the module's own message.
     */
    @Transactional
    public PendingAuthDetailResponse markFraud(String acctIdInput, String authKey) {
        Optional<PendingAuthDetailRecord> found = read(acctIdInput, authKey);
        if (found.isEmpty()) {
            return PendingAuthDetailResponse.empty(null);
        }
        PendingAuthDetailRecord detail = found.get();
        boolean confirmed = "F".equals(detail.getPaAuthFraud());
        AuthFraudService.Action action = confirmed
                ? AuthFraudService.Action.REMOVE_FRAUD : AuthFraudService.Action.REPORT_FRAUD;

        Long acctId = detail.getId().getPaAcctId();
        Long custId = cardXrefRepository.findByAcctId(acctId)
                .map(CardXrefRecord::getCustId)
                .orElseGet(() -> summaryRepository.findById(acctId)
                        .map(s -> s.getPaCustId())
                        .orElse(null));

        AuthFraudService.FraudResult result =
                authFraudService.report(acctId, custId, detail, action);
        if (!result.success()) {
            return render(detail).withMessage(result.message());
        }

        detail.setPaAuthFraud(action.code());
        detail.setPaFraudRptDate(result.fraudRptDate());
        PendingAuthDetailRecord saved = detailRepository.save(detail);

        String message = action == AuthFraudService.Action.REMOVE_FRAUD
                ? PendingAuthMessages.FRAUD_REMOVED : PendingAuthMessages.FRAUD_MARKED;
        return render(saved).withMessage(message);
    }

    /** READ-AUTH-RECORD: {@code GU PAUTSUM0} then {@code GNP PAUTDTL1 WHERE PAUT9CTS}. */
    private Optional<PendingAuthDetailRecord> read(String acctIdInput, String authKey) {
        if (acctIdInput == null || !acctIdInput.trim().chars().allMatch(Character::isDigit)
                || acctIdInput.trim().isEmpty()) {
            return Optional.empty();
        }
        if (!PendingAuthKey.isValid(authKey)) {
            return Optional.empty();
        }
        long acctId = Long.parseLong(acctIdInput.trim());
        if (summaryRepository.findById(acctId).isEmpty()) {
            return Optional.empty();
        }
        return detailRepository.findById(new PendingAuthDetailId(acctId,
                PendingAuthKey.date9c(authKey), PendingAuthKey.time9c(authKey)));
    }

    private PendingAuthDetailResponse render(PendingAuthDetailRecord d) {
        return new PendingAuthDetailResponse(
                PendingAuthKey.of(d.getId()),
                d.getPaCardNum(),
                PendingAuthFormat.displayDate(d.getPaAuthOrigDate()),
                PendingAuthFormat.displayTime(d.getPaAuthOrigTime()),
                PendingAuthFormat.amount12(d.getPaApprovedAmt()),
                "00".equals(d.getPaAuthRespCode()) ? "A" : "D",
                declineReason(d.getPaAuthRespReason()),
                d.getPaProcessingCode() == null ? null : String.format("%06d", d.getPaProcessingCode()),
                d.getPaPosEntryMode() == null ? null : String.format("%02d", d.getPaPosEntryMode()),
                d.getPaMessageSource(),
                d.getPaMerchantCatagoryCode(),
                PendingAuthFormat.displayExpiry(d.getPaCardExpiryDate()),
                d.getPaAuthType(),
                d.getPaTransactionId(),
                d.getPaMatchStatus(),
                d.getPaMerchantName(),
                d.getPaMerchantId(),
                d.getPaMerchantCity(),
                d.getPaMerchantState(),
                d.getPaMerchantZip(),
                fraud(d),
                true,
                null);
    }

    /**
     * {@code SEARCH ALL WS-DECLINE-REASON-TAB}: {@code cccc-DESCRIPTION}, and
     * {@code 9999-ERROR} when the reason code is not in the table
     * (COPAUS1C.cbl:317-327).
     */
    static String declineReason(String reasonCode) {
        String code = reasonCode == null ? "" : reasonCode.trim();
        String description = DECLINE_REASONS.get(code);
        if (description == null) {
            return "9999-ERROR";
        }
        return code + "-" + description;
    }

    /** {@code AUTHFRDO}: 'F'/'R' with the report date, otherwise a bare '-'. */
    private String fraud(PendingAuthDetailRecord d) {
        String flag = d.getPaAuthFraud();
        if ("F".equals(flag) || "R".equals(flag)) {
            String date = d.getPaFraudRptDate() == null ? "" : d.getPaFraudRptDate();
            return flag + "-" + date;
        }
        return "-";
    }

    private static Map<String, String> declineReasons() {
        Map<String, String> table = new LinkedHashMap<>();
        table.put("0000", "APPROVED");
        table.put("3100", "INVALID CARD");
        table.put("4100", "INSUFFICNT FUND");
        table.put("4200", "CARD NOT ACTIVE");
        table.put("4300", "ACCOUNT CLOSED");
        table.put("4400", "EXCED DAILY LMT");
        table.put("5100", "CARD FRAUD");
        table.put("5200", "MERCHANT FRAUD");
        table.put("5300", "LOST CARD");
        table.put("9000", "UNKNOWN");
        return Map.copyOf(table);
    }
}
