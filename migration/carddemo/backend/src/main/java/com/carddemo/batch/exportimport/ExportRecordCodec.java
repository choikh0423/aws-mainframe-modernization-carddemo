package com.carddemo.batch.exportimport;

import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.domain.CardRecord;
import com.carddemo.common.domain.CardXrefRecord;
import com.carddemo.common.domain.CustomerRecord;
import com.carddemo.common.domain.TransactionRecord;

import java.math.BigDecimal;
import java.util.Arrays;

import static com.carddemo.batch.exportimport.MainframeFieldCodec.putBinary;
import static com.carddemo.batch.exportimport.MainframeFieldCodec.putBinaryDecimal;
import static com.carddemo.batch.exportimport.MainframeFieldCodec.putPacked;
import static com.carddemo.batch.exportimport.MainframeFieldCodec.putText;
import static com.carddemo.batch.exportimport.MainframeFieldCodec.putZoned;
import static com.carddemo.batch.exportimport.MainframeFieldCodec.putZonedSigned;

/**
 * Builds the 500-byte EXPORT-RECORD of {@code app/cpy/CVEXPORT.cpy}, the record
 * CBEXPORT writes for every master record it reads (CBEXPORT.cbl:2200/3200/4200/
 * 5200/5700-CREATE-*).
 *
 * <p>Each record is space filled first, which is what
 * {@code INITIALIZE EXPORT-RECORD} does to the 460-byte data area before the
 * mapping paragraph fills its own fields (CBEXPORT.cbl:271), so the tail of a
 * record is blank rather than binary zeros.
 *
 * <p>The branch id and region code are literals in the COBOL - there is no
 * branch parameter in the program or in CBEXPORT.jcl - so they are literals
 * here too (CBEXPORT.cbl:278-279).
 */
public final class ExportRecordCodec {

    public static final int RECORD_LENGTH = 500;

    /** EXPORT-BRANCH-ID, hard coded in CBEXPORT.cbl:278. */
    public static final String BRANCH_ID = "0001";
    /** EXPORT-REGION-CODE, hard coded in CBEXPORT.cbl:279. */
    public static final String REGION_CODE = "NORTH";

    public static final char TYPE_CUSTOMER = 'C';
    public static final char TYPE_ACCOUNT = 'A';
    public static final char TYPE_XREF = 'X';
    public static final char TYPE_TRANSACTION = 'T';
    public static final char TYPE_CARD = 'D';

    static final int OFF_REC_TYPE = 0;
    static final int OFF_TIMESTAMP = 1;
    static final int LEN_TIMESTAMP = 26;
    static final int OFF_SEQUENCE = 27;
    static final int LEN_SEQUENCE = 4;
    static final int OFF_BRANCH_ID = 31;
    static final int OFF_REGION_CODE = 35;
    /** Start of EXPORT-RECORD-DATA; every data-area offset below is relative to it. */
    static final int DATA = 40;

    private ExportRecordCodec() {
    }

    /** EXPORT-CUSTOMER-DATA (CVEXPORT.cpy:24-42) from CBEXPORT.cbl:282-299. */
    public static byte[] customerRecord(CustomerRecord customer, String timestamp) {
        byte[] record = newRecord(TYPE_CUSTOMER, timestamp);
        putBinary(record, DATA, 4, customer.getCustId());
        putText(record, DATA + 4, 25, customer.getFirstName());
        putText(record, DATA + 29, 25, customer.getMiddleName());
        putText(record, DATA + 54, 25, customer.getLastName());
        putText(record, DATA + 79, 50, customer.getAddrLine1());
        putText(record, DATA + 129, 50, customer.getAddrLine2());
        putText(record, DATA + 179, 50, customer.getAddrLine3());
        putText(record, DATA + 229, 2, customer.getAddrStateCd());
        putText(record, DATA + 231, 3, customer.getAddrCountryCd());
        putText(record, DATA + 234, 10, customer.getAddrZip());
        putText(record, DATA + 244, 15, customer.getPhoneNum1());
        putText(record, DATA + 259, 15, customer.getPhoneNum2());
        putZoned(record, DATA + 274, 9, customer.getSsn());
        putText(record, DATA + 283, 20, customer.getGovtIssuedId());
        putText(record, DATA + 303, 10, customer.getDobYyyyMmDd());
        putText(record, DATA + 313, 10, customer.getEftAccountId());
        putText(record, DATA + 323, 1, customer.getPriCardHolderInd());
        putPacked(record, DATA + 324, 2, BigDecimal.valueOf(customer.getFicoCreditScore()), 0, false);
        return record;
    }

    /** EXPORT-ACCOUNT-DATA (CVEXPORT.cpy:47-60) from CBEXPORT.cbl:351-362. */
    public static byte[] accountRecord(AccountRecord account, String timestamp) {
        byte[] record = newRecord(TYPE_ACCOUNT, timestamp);
        putZoned(record, DATA, 11, account.getAcctId());
        putText(record, DATA + 11, 1, account.getActiveStatus());
        putPacked(record, DATA + 12, 7, account.getCurrBal(), 2, true);
        putZonedSigned(record, DATA + 19, 12, account.getCreditLimit(), 2);
        putPacked(record, DATA + 31, 7, account.getCashCreditLimit(), 2, true);
        putText(record, DATA + 38, 10, account.getOpenDate());
        putText(record, DATA + 48, 10, account.getExpiraionDate());
        putText(record, DATA + 58, 10, account.getReissueDate());
        putZonedSigned(record, DATA + 68, 12, account.getCurrCycCredit(), 2);
        putBinaryDecimal(record, DATA + 80, 8, account.getCurrCycDebit(), 2);
        putText(record, DATA + 88, 10, account.getAddrZip());
        putText(record, DATA + 98, 10, account.getGroupId());
        return record;
    }

    /** EXPORT-XREF-DATA (CVEXPORT.cpy:84-88) from CBEXPORT.cbl:415-417. */
    public static byte[] cardXrefRecord(CardXrefRecord xref, String timestamp) {
        byte[] record = newRecord(TYPE_XREF, timestamp);
        putText(record, DATA, 16, xref.getCardNum());
        putZoned(record, DATA + 16, 9, xref.getCustId());
        putBinary(record, DATA + 25, 8, xref.getAcctId());
        return record;
    }

    /** EXPORT-TRAN-DATA (CVEXPORT.cpy:65-79) from CBEXPORT.cbl:470-482. */
    public static byte[] transactionRecord(TransactionRecord transaction, String timestamp) {
        byte[] record = newRecord(TYPE_TRANSACTION, timestamp);
        putText(record, DATA, 16, transaction.getId());
        putText(record, DATA + 16, 2, transaction.getTypeCd());
        putZoned(record, DATA + 18, 4, transaction.getCatCd());
        putText(record, DATA + 22, 10, transaction.getSource());
        putText(record, DATA + 32, 100, transaction.getDescription());
        putPacked(record, DATA + 132, 6, transaction.getAmount(), 2, true);
        putBinary(record, DATA + 138, 4, transaction.getMerchantId());
        putText(record, DATA + 142, 50, transaction.getMerchantName());
        putText(record, DATA + 192, 50, transaction.getMerchantCity());
        putText(record, DATA + 242, 10, transaction.getMerchantZip());
        putText(record, DATA + 252, 16, transaction.getCardNum());
        putText(record, DATA + 268, 26, transaction.getOrigTs());
        putText(record, DATA + 294, 26, transaction.getProcTs());
        return record;
    }

    /** EXPORT-CARD-DATA (CVEXPORT.cpy:93-100) from CBEXPORT.cbl:535-540. */
    public static byte[] cardRecord(CardRecord card, String timestamp) {
        byte[] record = newRecord(TYPE_CARD, timestamp);
        putText(record, DATA, 16, card.getCardNum());
        putBinary(record, DATA + 16, 8, card.getAcctId());
        putBinary(record, DATA + 24, 2, card.getCvvCd());
        putText(record, DATA + 26, 50, card.getEmbossedName());
        putText(record, DATA + 76, 10, card.getExpiraionDate());
        putText(record, DATA + 86, 1, card.getActiveStatus());
        return record;
    }

    /**
     * Stamps the sequence number into a record already built. CBEXPORT numbers
     * records as it writes them (CBEXPORT.cbl:276-277); the migrated job does
     * the same in its writer so a restart continues the numbering instead of
     * starting again at 1.
     */
    public static void putSequenceNumber(byte[] record, long sequenceNumber) {
        putBinary(record, OFF_SEQUENCE, LEN_SEQUENCE, sequenceNumber);
    }

    public static long sequenceNumber(byte[] record) {
        return MainframeFieldCodec.getBinary(record, OFF_SEQUENCE, LEN_SEQUENCE);
    }

    public static char recordType(byte[] record) {
        return (char) (record[OFF_REC_TYPE] & 0xFF);
    }

    public static String timestamp(byte[] record) {
        return MainframeFieldCodec.getText(record, OFF_TIMESTAMP, LEN_TIMESTAMP);
    }

    private static byte[] newRecord(char recordType, String timestamp) {
        byte[] record = new byte[RECORD_LENGTH];
        Arrays.fill(record, (byte) ' ');
        record[OFF_REC_TYPE] = (byte) recordType;
        putText(record, OFF_TIMESTAMP, LEN_TIMESTAMP, timestamp);
        putBinary(record, OFF_SEQUENCE, LEN_SEQUENCE, 0);
        putText(record, OFF_BRANCH_ID, 4, BRANCH_ID);
        putText(record, OFF_REGION_CODE, 5, REGION_CODE);
        return record;
    }
}
