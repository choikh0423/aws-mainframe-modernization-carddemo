package com.carddemo.transaction.service;

import com.carddemo.transaction.dto.AddTransactionRequest;
import com.carddemo.transaction.dto.AddTransactionResponse;
import com.carddemo.transaction.entity.CardXref;
import com.carddemo.transaction.entity.Transaction;
import com.carddemo.transaction.exception.TransactionAddFailedException;
import com.carddemo.transaction.exception.TransactionValidationException;
import com.carddemo.transaction.repository.CardXrefRepository;
import com.carddemo.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Port of the CICS COBOL program COTRN02C (transaction CT02) - add a transaction
 * to the TRANSACT file.
 *
 * <p>Paragraph mapping:
 * <ul>
 *   <li>PROCESS-ENTER-KEY -&gt; {@link #addTransaction(AddTransactionRequest)}</li>
 *   <li>VALIDATE-INPUT-KEY-FIELDS -&gt; {@link #validateInputKeyFields(AddTransactionRequest)}</li>
 *   <li>VALIDATE-INPUT-DATA-FIELDS -&gt; {@link #validateInputDataFields(AddTransactionRequest)}</li>
 *   <li>ADD-TRANSACTION / WRITE-TRANSACT-FILE -&gt; {@link #writeTransaction(AddTransactionRequest, CardKey)}</li>
 *   <li>READ-CXACAIX-FILE / READ-CCXREF-FILE -&gt; the two lookups in
 *       {@link #validateInputKeyFields(AddTransactionRequest)}</li>
 * </ul>
 */
@Service
public class TransactionAddService {

    private static final int TRAN_ID_LENGTH = 16;
    private static final Pattern NUMERIC = Pattern.compile("\\d+");
    /** COBOL: sign, 8 digits, '.', 2 digits (PIC +99999999.99). */
    private static final Pattern AMOUNT = Pattern.compile("[-+]\\d{8}\\.\\d{2}");
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    private final TransactionRepository transactionRepository;
    private final CardXrefRepository cardXrefRepository;

    public TransactionAddService(TransactionRepository transactionRepository,
                                 CardXrefRepository cardXrefRepository) {
        this.transactionRepository = transactionRepository;
        this.cardXrefRepository = cardXrefRepository;
    }

    /** Account id / card number pair resolved from the XREF file. */
    record CardKey(String accountId, String cardNumber) {
    }

    @Transactional
    public AddTransactionResponse addTransaction(AddTransactionRequest request) {
        CardKey key = validateInputKeyFields(request);
        validateInputDataFields(request);

        if (!request.isConfirm()) {
            return AddTransactionResponse.confirmationRequired();
        }
        return writeTransaction(request, key);
    }

    /**
     * VALIDATE-INPUT-KEY-FIELDS, including READ-CXACAIX-FILE and READ-CCXREF-FILE.
     */
    CardKey validateInputKeyFields(AddTransactionRequest request) {
        String accountId = trimToEmpty(request.getAccountId());
        String cardNumber = trimToEmpty(request.getCardNumber());

        if (!accountId.isEmpty()) {
            if (!NUMERIC.matcher(accountId).matches()) {
                throw new TransactionValidationException("accountId", "Account ID must be Numeric...");
            }
            CardXref xref = cardXrefRepository.findByAcctId(Long.parseLong(accountId))
                    .orElseThrow(() ->
                            new TransactionValidationException("accountId", "Account ID NOT found..."));
            return new CardKey(pad(accountId, 11), trimToEmpty(xref.getCardNum()));
        }

        if (!cardNumber.isEmpty()) {
            if (!NUMERIC.matcher(cardNumber).matches()) {
                throw new TransactionValidationException("cardNumber", "Card Number must be Numeric...");
            }
            String paddedCardNumber = pad(cardNumber, TRAN_ID_LENGTH);
            CardXref xref = cardXrefRepository.findByCardNum(paddedCardNumber)
                    .orElseThrow(() ->
                            new TransactionValidationException("cardNumber", "Card Number NOT found..."));
            return new CardKey(String.valueOf(xref.getAcctId()), paddedCardNumber);
        }

        throw new TransactionValidationException("accountId",
                "Account or Card Number must be entered...");
    }

    /** VALIDATE-INPUT-DATA-FIELDS. */
    void validateInputDataFields(AddTransactionRequest request) {
        requireNotEmpty(request.getTypeCode(), "typeCode", "Type CD can NOT be empty...");
        requireNotEmpty(request.getCategoryCode(), "categoryCode", "Category CD can NOT be empty...");
        requireNotEmpty(request.getSource(), "source", "Source can NOT be empty...");
        requireNotEmpty(request.getDescription(), "description", "Description can NOT be empty...");
        requireNotEmpty(request.getAmount(), "amount", "Amount can NOT be empty...");
        requireNotEmpty(request.getOrigDate(), "origDate", "Orig Date can NOT be empty...");
        requireNotEmpty(request.getProcDate(), "procDate", "Proc Date can NOT be empty...");
        requireNotEmpty(request.getMerchantId(), "merchantId", "Merchant ID can NOT be empty...");
        requireNotEmpty(request.getMerchantName(), "merchantName", "Merchant Name can NOT be empty...");
        requireNotEmpty(request.getMerchantCity(), "merchantCity", "Merchant City can NOT be empty...");
        requireNotEmpty(request.getMerchantZip(), "merchantZip", "Merchant Zip can NOT be empty...");

        requireNumeric(request.getTypeCode(), "typeCode", "Type CD must be Numeric...");
        requireNumeric(request.getCategoryCode(), "categoryCode", "Category CD must be Numeric...");

        if (!AMOUNT.matcher(trimToEmpty(request.getAmount())).matches()) {
            throw new TransactionValidationException("amount",
                    "Amount should be in format -99999999.99");
        }

        requireDate(request.getOrigDate(), "origDate",
                "Orig Date should be in format YYYY-MM-DD", "Orig Date - Not a valid date...");
        requireDate(request.getProcDate(), "procDate",
                "Proc Date should be in format YYYY-MM-DD", "Proc Date - Not a valid date...");

        requireNumeric(request.getMerchantId(), "merchantId", "Merchant ID must be Numeric...");
    }

    /** ADD-TRANSACTION plus WRITE-TRANSACT-FILE. */
    AddTransactionResponse writeTransaction(AddTransactionRequest request, CardKey key) {
        String tranId = nextTranId();

        Transaction transaction = new Transaction();
        transaction.setTranId(tranId);
        transaction.setTypeCode(trimToEmpty(request.getTypeCode()));
        transaction.setCategoryCode(Integer.valueOf(trimToEmpty(request.getCategoryCode())));
        transaction.setSource(trimToEmpty(request.getSource()));
        transaction.setDescription(trimToEmpty(request.getDescription()));
        transaction.setAmount(new BigDecimal(stripAmountSign(trimToEmpty(request.getAmount()))));
        transaction.setCardNumber(key.cardNumber());
        transaction.setMerchantId(Long.valueOf(trimToEmpty(request.getMerchantId())));
        transaction.setMerchantName(trimToEmpty(request.getMerchantName()));
        transaction.setMerchantCity(trimToEmpty(request.getMerchantCity()));
        transaction.setMerchantZip(trimToEmpty(request.getMerchantZip()));
        transaction.setOrigTimestamp(trimToEmpty(request.getOrigDate()));
        transaction.setProcTimestamp(trimToEmpty(request.getProcDate()));

        if (transactionRepository.existsById(tranId)) {
            throw new TransactionValidationException("tranId", "Tran ID already exist...");
        }

        try {
            transactionRepository.save(transaction);
        } catch (DataIntegrityViolationException e) {
            throw new TransactionValidationException("tranId", "Tran ID already exist...");
        } catch (RuntimeException e) {
            throw new TransactionAddFailedException("Unable to Add Transaction...", e);
        }

        return AddTransactionResponse.added(tranId);
    }

    /**
     * ADD-TRANSACTION: browse TRANSACT backwards from HIGH-VALUES to get the
     * highest key, add 1, and keep the 16 character zero padded form. An empty
     * file yields id 1 (COBOL moves ZEROS to TRAN-ID on ENDFILE).
     */
    private String nextTranId() {
        long maxId = transactionRepository.findMaxTranId()
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .map(Long::parseLong)
                .orElse(0L);
        return pad(Long.toString(maxId + 1), TRAN_ID_LENGTH);
    }

    private static String stripAmountSign(String amount) {
        return amount.startsWith("+") ? amount.substring(1) : amount;
    }

    private static void requireNotEmpty(String value, String field, String message) {
        if (trimToEmpty(value).isEmpty()) {
            throw new TransactionValidationException(field, message);
        }
    }

    private static void requireNumeric(String value, String field, String message) {
        if (!NUMERIC.matcher(trimToEmpty(value)).matches()) {
            throw new TransactionValidationException(field, message);
        }
    }

    /**
     * The COBOL layout check followed by the CSUTLDTC calendar validity call.
     */
    private static void requireDate(String value, String field, String formatMessage,
                                    String invalidMessage) {
        String date = trimToEmpty(value);
        if (!DATE.matcher(date).matches()) {
            throw new TransactionValidationException(field, formatMessage);
        }
        try {
            LocalDate.parse(date, DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new TransactionValidationException(field, invalidMessage);
        }
    }

    private static String pad(String value, int length) {
        return "0".repeat(Math.max(0, length - value.length())) + value;
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
