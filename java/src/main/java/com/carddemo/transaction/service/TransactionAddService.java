package com.carddemo.transaction.service;

import com.carddemo.transaction.api.TransactionAddRequest;
import com.carddemo.transaction.api.TransactionAddResponse;
import com.carddemo.transaction.domain.CardXref;
import com.carddemo.transaction.domain.Transaction;
import com.carddemo.transaction.repository.CardXrefRepository;
import com.carddemo.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Java migration of CICS program COTRN02C (transaction CT02, mapset COTRN02):
 * add a transaction to the TRANSACT file.
 *
 * <p>COBOL sends the map and returns to CICS as soon as a rule fails, so
 * validation here stops at the first error and reports the same message.
 */
@Service
public class TransactionAddService {

    static final int ACCT_ID_LEN = 11;
    static final int CARD_NUM_LEN = 16;
    static final int TYPE_CD_LEN = 2;
    static final int CAT_CD_LEN = 4;
    static final int SOURCE_LEN = 10;
    static final int DESC_LEN = 60;
    static final int AMOUNT_LEN = 12;
    static final int DATE_LEN = 10;
    static final int MERCHANT_ID_LEN = 9;
    static final int MERCHANT_NAME_LEN = 30;
    static final int MERCHANT_CITY_LEN = 25;
    static final int MERCHANT_ZIP_LEN = 10;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    private final TransactionRepository transactionRepository;
    private final CardXrefRepository cardXrefRepository;

    public TransactionAddService(TransactionRepository transactionRepository,
                                 CardXrefRepository cardXrefRepository) {
        this.transactionRepository = transactionRepository;
        this.cardXrefRepository = cardXrefRepository;
    }

    /** PROCESS-ENTER-KEY. */
    @Transactional
    public TransactionAddResponse processEnterKey(TransactionAddRequest request) {
        TransactionAddRequest fields = copyOf(request);

        String keyError = validateInputKeyFields(fields);
        if (keyError != null) {
            return TransactionAddResponse.error(keyError, fields);
        }

        String dataError = validateInputDataFields(fields);
        if (dataError != null) {
            return TransactionAddResponse.error(dataError, fields);
        }

        String confirm = fields.getConfirm() == null ? "" : fields.getConfirm().trim();
        if (confirm.equalsIgnoreCase("Y")) {
            return addTransaction(fields);
        }
        if (confirm.isEmpty() || confirm.equalsIgnoreCase("N")) {
            return TransactionAddResponse.error(Messages.CONFIRM_TO_ADD, fields);
        }
        return TransactionAddResponse.error(Messages.CONFIRM_INVALID, fields);
    }

    /**
     * COPY-LAST-TRAN-DATA (PF5): pre-fill the screen from the most recent
     * transaction and then re-run PROCESS-ENTER-KEY.
     */
    @Transactional
    public TransactionAddResponse copyLastTransaction(TransactionAddRequest request) {
        TransactionAddRequest fields = copyOf(request);

        String keyError = validateInputKeyFields(fields);
        if (keyError != null) {
            return TransactionAddResponse.error(keyError, fields);
        }

        transactionRepository.findFirstByOrderByTranIdDesc().ifPresent(last -> {
            fields.setTypeCd(last.getTranTypeCd());
            fields.setCatCd(last.getTranCatCd() == null ? null
                    : CobolField.zeroFill(String.valueOf(last.getTranCatCd()), CAT_CD_LEN));
            fields.setSource(last.getTranSource());
            fields.setAmount(formatAmount(last.getTranAmt()));
            fields.setDescription(last.getTranDesc() == null ? null
                    : CobolField.pad(last.getTranDesc(), DESC_LEN));
            fields.setOrigDate(trimToDate(last.getTranOrigTs()));
            fields.setProcDate(trimToDate(last.getTranProcTs()));
            fields.setMerchantId(last.getTranMerchantId() == null ? null
                    : CobolField.zeroFill(String.valueOf(last.getTranMerchantId()), MERCHANT_ID_LEN));
            fields.setMerchantName(last.getTranMerchantName());
            fields.setMerchantCity(last.getTranMerchantCity());
            fields.setMerchantZip(last.getTranMerchantZip());
        });

        return processEnterKey(fields);
    }

    /** VALIDATE-INPUT-KEY-FIELDS: resolves account id and card number via the XREF files. */
    private String validateInputKeyFields(TransactionAddRequest fields) {
        if (!CobolField.isEmpty(fields.getAcctId())) {
            if (!CobolField.isNumeric(fields.getAcctId(), ACCT_ID_LEN)) {
                return Messages.ACCT_ID_NOT_NUMERIC;
            }
            String acctId = CobolField.zeroFill(fields.getAcctId(), ACCT_ID_LEN);
            fields.setAcctId(acctId);
            Optional<CardXref> xref = cardXrefRepository.findFirstByXrefAcctId(acctId);
            if (xref.isEmpty()) {
                return Messages.ACCT_ID_NOT_FOUND;
            }
            fields.setCardNum(xref.get().getXrefCardNum());
            return null;
        }

        if (!CobolField.isEmpty(fields.getCardNum())) {
            if (!CobolField.isNumeric(fields.getCardNum(), CARD_NUM_LEN)) {
                return Messages.CARD_NUM_NOT_NUMERIC;
            }
            String cardNum = CobolField.zeroFill(fields.getCardNum(), CARD_NUM_LEN);
            fields.setCardNum(cardNum);
            Optional<CardXref> xref = cardXrefRepository.findById(cardNum);
            if (xref.isEmpty()) {
                return Messages.CARD_NUM_NOT_FOUND;
            }
            fields.setAcctId(xref.get().getXrefAcctId());
            return null;
        }

        return Messages.ACCT_OR_CARD_REQUIRED;
    }

    /** VALIDATE-INPUT-DATA-FIELDS. */
    private String validateInputDataFields(TransactionAddRequest fields) {
        if (CobolField.isEmpty(fields.getTypeCd())) {
            return Messages.TYPE_CD_EMPTY;
        }
        if (CobolField.isEmpty(fields.getCatCd())) {
            return Messages.CATEGORY_CD_EMPTY;
        }
        if (CobolField.isEmpty(fields.getSource())) {
            return Messages.SOURCE_EMPTY;
        }
        if (CobolField.isEmpty(fields.getDescription())) {
            return Messages.DESCRIPTION_EMPTY;
        }
        if (CobolField.isEmpty(fields.getAmount())) {
            return Messages.AMOUNT_EMPTY;
        }
        if (CobolField.isEmpty(fields.getOrigDate())) {
            return Messages.ORIG_DATE_EMPTY;
        }
        if (CobolField.isEmpty(fields.getProcDate())) {
            return Messages.PROC_DATE_EMPTY;
        }
        if (CobolField.isEmpty(fields.getMerchantId())) {
            return Messages.MERCHANT_ID_EMPTY;
        }
        if (CobolField.isEmpty(fields.getMerchantName())) {
            return Messages.MERCHANT_NAME_EMPTY;
        }
        if (CobolField.isEmpty(fields.getMerchantCity())) {
            return Messages.MERCHANT_CITY_EMPTY;
        }
        if (CobolField.isEmpty(fields.getMerchantZip())) {
            return Messages.MERCHANT_ZIP_EMPTY;
        }

        if (!CobolField.isNumeric(fields.getTypeCd(), TYPE_CD_LEN)) {
            return Messages.TYPE_CD_NOT_NUMERIC;
        }
        if (!CobolField.isNumeric(fields.getCatCd(), CAT_CD_LEN)) {
            return Messages.CATEGORY_CD_NOT_NUMERIC;
        }

        String amount = CobolField.pad(fields.getAmount(), AMOUNT_LEN);
        char sign = CobolField.charAt(amount, 1);
        if ((sign != '-' && sign != '+')
                || !CobolField.allDigits(CobolField.refmod(amount, 2, 8))
                || CobolField.charAt(amount, 10) != '.'
                || !CobolField.allDigits(CobolField.refmod(amount, 11, 2))) {
            return Messages.AMOUNT_FORMAT;
        }

        if (!hasDateLayout(fields.getOrigDate())) {
            return Messages.ORIG_DATE_FORMAT;
        }
        if (!hasDateLayout(fields.getProcDate())) {
            return Messages.PROC_DATE_FORMAT;
        }

        fields.setAmount(formatAmount(parseAmount(amount)));

        if (!isValidDate(fields.getOrigDate())) {
            return Messages.ORIG_DATE_INVALID;
        }
        if (!isValidDate(fields.getProcDate())) {
            return Messages.PROC_DATE_INVALID;
        }

        if (!CobolField.isNumeric(fields.getMerchantId(), MERCHANT_ID_LEN)) {
            return Messages.MERCHANT_ID_NOT_NUMERIC;
        }

        return null;
    }

    /** ADD-TRANSACTION + WRITE-TRANSACT-FILE. */
    private TransactionAddResponse addTransaction(TransactionAddRequest fields) {
        String tranId = nextTranId();
        if (transactionRepository.existsById(tranId)) {
            return TransactionAddResponse.error(Messages.TRAN_ID_EXISTS, fields);
        }

        Transaction transaction = new Transaction();
        transaction.setTranId(tranId);
        transaction.setTranTypeCd(CobolField.pad(fields.getTypeCd(), TYPE_CD_LEN));
        transaction.setTranCatCd(Integer.parseInt(CobolField.pad(fields.getCatCd(), CAT_CD_LEN)));
        transaction.setTranSource(CobolField.pad(fields.getSource(), SOURCE_LEN).trim());
        transaction.setTranDesc(CobolField.pad(fields.getDescription(), DESC_LEN).trim());
        transaction.setTranAmt(parseAmount(CobolField.pad(fields.getAmount(), AMOUNT_LEN)));
        transaction.setTranCardNum(fields.getCardNum());
        transaction.setTranMerchantId(
                Long.parseLong(CobolField.pad(fields.getMerchantId(), MERCHANT_ID_LEN)));
        transaction.setTranMerchantName(CobolField.pad(fields.getMerchantName(), MERCHANT_NAME_LEN).trim());
        transaction.setTranMerchantCity(CobolField.pad(fields.getMerchantCity(), MERCHANT_CITY_LEN).trim());
        transaction.setTranMerchantZip(CobolField.pad(fields.getMerchantZip(), MERCHANT_ZIP_LEN).trim());
        transaction.setTranOrigTs(CobolField.pad(fields.getOrigDate(), DATE_LEN).trim());
        transaction.setTranProcTs(CobolField.pad(fields.getProcDate(), DATE_LEN).trim());

        try {
            transactionRepository.saveAndFlush(transaction);
        } catch (DataIntegrityViolationException e) {
            return TransactionAddResponse.error(Messages.TRAN_ID_EXISTS, fields);
        } catch (RuntimeException e) {
            return TransactionAddResponse.error(Messages.UNABLE_TO_ADD, fields);
        }

        return TransactionAddResponse.success(Messages.addedSuccessfully(tranId), tranId, clearedFields());
    }

    /** STARTBR(HIGH-VALUES) / READPREV / ENDBR then ADD 1 TO WS-TRAN-ID-N. */
    private String nextTranId() {
        long last = transactionRepository.findFirstByOrderByTranIdDesc()
                .map(t -> Long.parseLong(t.getTranId().trim()))
                .orElse(0L);
        return CobolField.zeroFill(String.valueOf(last + 1), Transaction.TRAN_ID_LEN);
    }

    private static boolean hasDateLayout(String value) {
        String date = CobolField.pad(value, DATE_LEN);
        return CobolField.allDigits(CobolField.refmod(date, 1, 4))
                && CobolField.charAt(date, 5) == '-'
                && CobolField.allDigits(CobolField.refmod(date, 6, 2))
                && CobolField.charAt(date, 8) == '-'
                && CobolField.allDigits(CobolField.refmod(date, 9, 2));
    }

    private static boolean isValidDate(String value) {
        try {
            LocalDate.parse(CobolField.pad(value, DATE_LEN), DATE_FORMAT);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /** FUNCTION NUMVAL-C. */
    private static BigDecimal parseAmount(String amount) {
        return new BigDecimal(amount.trim().replace("+", "")).setScale(2);
    }

    /** MOVE to PIC +99999999.99. */
    static String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        BigDecimal scaled = amount.setScale(2);
        String sign = scaled.signum() < 0 ? "-" : "+";
        String digits = scaled.abs().toPlainString().replace(".", "");
        String padded = CobolField.zeroFill(digits, 10);
        return sign + padded.substring(0, 8) + "." + padded.substring(8);
    }

    private static String trimToDate(String timestamp) {
        if (timestamp == null) {
            return null;
        }
        String trimmed = timestamp.trim();
        return trimmed.length() > DATE_LEN ? trimmed.substring(0, DATE_LEN) : trimmed;
    }

    /** INITIALIZE-ALL-FIELDS. */
    private static TransactionAddRequest clearedFields() {
        return new TransactionAddRequest();
    }

    private static TransactionAddRequest copyOf(TransactionAddRequest source) {
        TransactionAddRequest copy = new TransactionAddRequest();
        copy.setAcctId(source.getAcctId());
        copy.setCardNum(source.getCardNum());
        copy.setTypeCd(source.getTypeCd());
        copy.setCatCd(source.getCatCd());
        copy.setSource(source.getSource());
        copy.setDescription(source.getDescription());
        copy.setAmount(source.getAmount());
        copy.setOrigDate(source.getOrigDate());
        copy.setProcDate(source.getProcDate());
        copy.setMerchantId(source.getMerchantId());
        copy.setMerchantName(source.getMerchantName());
        copy.setMerchantCity(source.getMerchantCity());
        copy.setMerchantZip(source.getMerchantZip());
        copy.setConfirm(source.getConfirm());
        return copy;
    }
}
