package com.carddemo.transaction.service;

import com.carddemo.transaction.dto.CardXrefResolveResponse;
import com.carddemo.transaction.dto.TransactionAddRequest;
import com.carddemo.transaction.dto.TransactionAddResponse;
import com.carddemo.transaction.dto.TransactionViewResponse;
import com.carddemo.transaction.entity.TransactionRecord;
import com.carddemo.transaction.exception.DuplicateTranIdException;
import com.carddemo.transaction.repository.TransactionRepository;
import com.carddemo.transaction.validator.TransactionValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * CT02 — Add a Transaction (COTRN02C). Orchestrates PROCESS-ENTER-KEY 1:1
 * (COTRN02C.cbl:164-188): resolve the account/card key fields, run the data
 * edits, apply the confirm gate, then generate the next Tran ID and WRITE.
 *
 * <p>Key generation mirrors ADD-TRANSACTION (COTRN02C.cbl:442-466): browse
 * TRANSACT to its highest key (STARTBR HIGH-VALUES + READPREV ==
 * {@code findTopByOrderByIdDesc}), add 1, and write the new 16-digit record. An
 * empty file yields id 1 (READPREV ENDFILE -> ZEROS + 1, cbl:688-689). A
 * pre-existing key raises "Tran ID already exist..." (cbl:735-741).
 */
@Service
public class TransactionAddService {

    /** TRAN-ID is a 16-char zero-padded numeric key (CVTRA05Y / schema.sql). */
    private static final int TRAN_ID_LEN = 16;

    /**
     * Verbatim success text COTRN02C STRINGs after a good WRITE
     * (COTRN02C.cbl:728-733). The two literals contribute a trailing and a
     * leading blank, so there are two spaces after the period — reproduced here
     * for parity with the legacy 3270 message.
     */
    static final String SUCCESS_PREFIX = "Transaction added successfully.  Your Tran ID is ";

    private final TransactionRepository transactionRepository;
    private final CardXrefResolveService cardXrefResolveService;
    private final TransactionValidator validator;

    public TransactionAddService(TransactionRepository transactionRepository,
                                 CardXrefResolveService cardXrefResolveService,
                                 TransactionValidator validator) {
        this.transactionRepository = transactionRepository;
        this.cardXrefResolveService = cardXrefResolveService;
        this.validator = validator;
    }

    @Transactional
    public TransactionAddResponse add(TransactionAddRequest req) {
        // VALIDATE-INPUT-KEY-FIELDS: resolve account<->card (FR-A1..A5).
        CardXrefResolveResponse xref =
                cardXrefResolveService.resolve(req.getAccountId(), req.getCardNumber());

        // VALIDATE-INPUT-DATA-FIELDS: field edits in COBOL source order (FR-A7..A10).
        validator.validateDataFields(req);

        // Confirm gate (FR-A11): only Y/y falls through to the WRITE.
        validator.validateConfirm(req.getConfirm());

        // ADD-TRANSACTION key generation: max existing id + 1 (FR-A6).
        String newId = nextTranId();
        if (transactionRepository.existsById(newId)) {
            throw new DuplicateTranIdException();
        }

        TransactionRecord record = buildRecord(newId, req, xref.getCardNumber());
        transactionRepository.save(record);

        return new TransactionAddResponse(newId, SUCCESS_PREFIX + newId + ".");
    }

    /**
     * Most recent transaction (highest key), the source COTRN02C's
     * COPY-LAST-TRAN-DATA reads to pre-fill the form on PF5 (FR-A12,
     * COTRN02C.cbl:471-495): STARTBR HIGH-VALUES + READPREV ==
     * {@code findTopByOrderByIdDesc}. Empty when no transactions exist.
     */
    @Transactional(readOnly = true)
    public Optional<TransactionViewResponse> getLatest() {
        return transactionRepository.findTopByOrderByIdDesc().map(TransactionViewResponse::from);
    }

    private String nextTranId() {
        long max = transactionRepository.findTopByOrderByIdDesc()
                .map(r -> Long.parseLong(r.getId().trim()))
                .orElse(0L);
        return String.format("%0" + TRAN_ID_LEN + "d", max + 1);
    }

    private TransactionRecord buildRecord(String id, TransactionAddRequest req, String cardNum) {
        TransactionRecord r = new TransactionRecord();
        r.setId(id);
        r.setTypeCd(req.getTypeCd().trim());
        r.setCatCd(Integer.parseInt(req.getCategoryCd().trim()));
        r.setSource(req.getSource().trim());
        r.setDescription(req.getDescription().trim());
        r.setAmount(parseAmount(req.getAmount()));
        r.setMerchantId(Long.parseLong(req.getMerchantId().trim()));
        r.setMerchantName(req.getMerchantName().trim());
        r.setMerchantCity(req.getMerchantCity().trim());
        r.setMerchantZip(req.getMerchantZip().trim());
        r.setCardNum(cardNum);
        r.setOrigTs(req.getOrigDate().trim());
        r.setProcTs(req.getProcDate().trim());
        return r;
    }

    /**
     * Parse the {@code -99999999.99} edit string into TRAN-AMT S9(09)V99,
     * mirroring COMPUTE WS-TRAN-AMT-N = FUNCTION NUMVAL-C(TRNAMTI)
     * (COTRN02C.cbl:456-458). The format is guaranteed by the validator.
     */
    private BigDecimal parseAmount(String amount) {
        return new BigDecimal(amount.trim()).setScale(2, RoundingMode.HALF_UP);
    }
}
