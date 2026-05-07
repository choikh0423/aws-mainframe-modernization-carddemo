package com.carddemo.transaction.service;

import com.carddemo.transaction.dto.AddTransactionRequest;
import com.carddemo.transaction.dto.AddTransactionResponse;
import com.carddemo.transaction.dto.CopyLastTransactionResponse;
import com.carddemo.transaction.entity.TransactionRecord;
import com.carddemo.transaction.exception.ValidationException;
import com.carddemo.transaction.repository.TransactionRepository;
import com.carddemo.transaction.validation.TransactionValidator;
import com.carddemo.transaction.validation.TransactionValidator.KeyValidationResult;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Business logic for the Add Transaction flow.
 *
 * Replaces COBOL paragraphs:
 *   - PROCESS-ENTER-KEY      (lines 164-188)
 *   - ADD-TRANSACTION         (lines 442-466)
 *   - COPY-LAST-TRAN-DATA    (lines 471-495)
 *   - WRITE-TRANSACT-FILE    (lines 711-749)
 *   - STARTBR/READPREV/ENDBR (lines 642-706)
 */
@Service
public class TransactionAddService {

    private final TransactionValidator validator;
    private final TransactionRepository transactionRepository;

    public TransactionAddService(TransactionValidator validator,
                                  TransactionRepository transactionRepository) {
        this.validator = validator;
        this.transactionRepository = transactionRepository;
    }

    /**
     * PROCESS-ENTER-KEY (lines 164-188).
     *
     * 1. Validate key fields (acct ID or card num)
     * 2. Validate data fields (11 empty checks + type/format + date validity)
     * 3. Validate confirmation
     * 4. If confirm=Y, add transaction
     *
     * Short-circuits on first error (throws ValidationException).
     */
    public AddTransactionResponse processSubmission(AddTransactionRequest req) {
        // Phase 1: Key field validation (VALIDATE-INPUT-KEY-FIELDS)
        KeyValidationResult keyResult = validator.validateKeyFields(req);

        // Phase 2-5: Data field validation (VALIDATE-INPUT-DATA-FIELDS)
        String normalizedAmount = validator.validateDataFields(req);

        // Phase 6: Confirmation (PROCESS-ENTER-KEY lines 169-188)
        validator.validateConfirmation(req.getConfirm());

        // ADD-TRANSACTION (lines 442-466)
        return addTransaction(req, keyResult, normalizedAmount);
    }

    /**
     * Validate-only endpoint (ENTER without confirm=Y).
     * Runs all validation, resolves XREF lookups, normalizes amount.
     * Does NOT write a transaction record.
     */
    public AddTransactionResponse validateOnly(AddTransactionRequest req) {
        KeyValidationResult keyResult = validator.validateKeyFields(req);
        String normalizedAmount = validator.validateDataFields(req);
        return AddTransactionResponse.validated(
                keyResult.getResolvedCardNum(),
                keyResult.getResolvedAcctId(),
                normalizedAmount);
    }

    /**
     * ADD-TRANSACTION (lines 442-466).
     *
     * COBOL sequence:
     *   1. STARTBR TRANSACT at HIGH-VALUES
     *   2. READPREV -> get last (highest) TRAN-ID
     *   3. ENDBR
     *   4. ADD 1 to last TRAN-ID -> new ID
     *   5. INITIALIZE TRAN-RECORD
     *   6. Populate fields from screen
     *   7. WRITE TRANSACT
     *
     * Java: findTopByOrderByTranIdDesc() replaces STARTBR/READPREV/ENDBR.
     * New ID = last + 1, zero-padded to 16 chars (preserves VSAM key format).
     */
    private AddTransactionResponse addTransaction(AddTransactionRequest req,
                                                   KeyValidationResult keyResult,
                                                   String normalizedAmount) {
        // Get last tran ID (STARTBR HIGH-VALUES + READPREV)
        Optional<TransactionRecord> lastTran = transactionRepository.findTopByOrderByTranIdDesc();
        long lastId = 0;
        if (lastTran.isPresent()) {
            try {
                lastId = Long.parseLong(lastTran.get().getTranId().trim());
            } catch (NumberFormatException e) {
                lastId = 0;
            }
        }
        long newId = lastId + 1;
        String newTranId = String.format("%016d", newId);

        // INITIALIZE TRAN-RECORD + populate from screen fields
        TransactionRecord record = new TransactionRecord();
        record.setTranId(newTranId);
        record.setTranTypeCd(req.getTypeCd());
        record.setTranCatCd(Integer.parseInt(req.getCatCd()));
        record.setTranSource(req.getSource());
        record.setTranDesc(req.getDescription());

        // NUMVAL-C equivalent: parse the normalized amount
        BigDecimal amtValue = new BigDecimal(normalizedAmount.trim());
        record.setTranAmt(amtValue);

        record.setTranCardNum(keyResult.getResolvedCardNum());
        record.setTranMerchantId(Long.parseLong(req.getMerchantId()));
        record.setTranMerchantName(req.getMerchantName());
        record.setTranMerchantCity(req.getMerchantCity());
        record.setTranMerchantZip(req.getMerchantZip());
        record.setTranOrigTs(req.getOrigDate());
        record.setTranProcTs(req.getProcDate());

        // WRITE TRANSACT (lines 711-749)
        try {
            transactionRepository.save(record);
        } catch (DataIntegrityViolationException e) {
            // DFHRESP(DUPKEY) / DFHRESP(DUPREC) -> "Tran ID already exist..."
            throw new ValidationException("Tran ID already exist...", "acctId");
        } catch (Exception e) {
            // DFHRESP(OTHER) -> "Unable to Add Transaction..."
            throw new ValidationException("Unable to Add Transaction...", "acctId");
        }

        // Success: clear fields, green message (COBOL lines 724-734)
        String trimmedId = newTranId.replaceFirst("^0+", "");
        if (trimmedId.isEmpty()) {
            trimmedId = "0";
        }
        String message = "Transaction added successfully. Your Tran ID is " + trimmedId + ".";
        return AddTransactionResponse.success(newTranId, message);
    }

    /**
     * COPY-LAST-TRAN-DATA (lines 471-495).
     *
     * 1. Validate key fields first
     * 2. STARTBR/READPREV/ENDBR -> get last transaction
     * 3. Copy all 11 data fields to response
     *
     * After copy, the COBOL program calls PROCESS-ENTER-KEY to validate
     * the copied data. The frontend replicates this by auto-submitting
     * to the validate endpoint after receiving the copy response.
     */
    public CopyLastTransactionResponse copyLastTransaction(AddTransactionRequest req) {
        // Validate key fields first (COBOL line 473)
        validator.validateKeyFields(req);

        // STARTBR/READPREV/ENDBR (lines 475-478)
        Optional<TransactionRecord> lastTran = transactionRepository.findTopByOrderByTranIdDesc();

        CopyLastTransactionResponse resp = new CopyLastTransactionResponse();
        if (lastTran.isEmpty()) {
            resp.setSuccess(true);
            resp.setMessage("No transactions found to copy.");
            return resp;
        }

        TransactionRecord tran = lastTran.get();
        resp.setSuccess(true);

        // Copy all 11 data fields (COBOL lines 481-492)
        resp.setTypeCd(tran.getTranTypeCd());
        resp.setCatCd(String.format("%04d", tran.getTranCatCd()));
        resp.setSource(tran.getTranSource());
        resp.setDescription(tran.getTranDesc());

        // TRAN-AMT -> WS-TRAN-AMT-E (PIC +99999999.99)
        String amtFormatted = String.format("%+012.2f", tran.getTranAmt().doubleValue());
        resp.setAmount(amtFormatted);

        resp.setOrigDate(tran.getTranOrigTs() != null ? tran.getTranOrigTs().trim() : "");
        resp.setProcDate(tran.getTranProcTs() != null ? tran.getTranProcTs().trim() : "");
        resp.setMerchantId(String.format("%09d", tran.getTranMerchantId()));
        resp.setMerchantName(tran.getTranMerchantName());
        resp.setMerchantCity(tran.getTranMerchantCity());
        resp.setMerchantZip(tran.getTranMerchantZip());

        return resp;
    }
}
