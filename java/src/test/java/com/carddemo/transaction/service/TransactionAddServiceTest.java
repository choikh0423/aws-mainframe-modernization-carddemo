package com.carddemo.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.transaction.api.TransactionAddRequest;
import com.carddemo.transaction.api.TransactionAddResponse;
import com.carddemo.transaction.domain.CardXref;
import com.carddemo.transaction.domain.Transaction;
import com.carddemo.transaction.repository.CardXrefRepository;
import com.carddemo.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TransactionAddServiceTest {

    private static final String ACCT_ID = "00000000011";
    private static final String CARD_NUM = "4111111111111111";

    @Autowired
    private TransactionAddService service;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private CardXrefRepository cardXrefRepository;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        cardXrefRepository.deleteAll();
        CardXref xref = new CardXref();
        xref.setXrefCardNum(CARD_NUM);
        xref.setXrefCustId(1L);
        xref.setXrefAcctId(ACCT_ID);
        cardXrefRepository.save(xref);
    }

    private TransactionAddRequest validRequest() {
        TransactionAddRequest r = new TransactionAddRequest();
        r.setAcctId(ACCT_ID);
        r.setTypeCd("01");
        r.setCatCd("0001");
        r.setSource("POS");
        r.setDescription("Coffee");
        r.setAmount("-00000012.34");
        r.setOrigDate("2024-01-15");
        r.setProcDate("2024-01-16");
        r.setMerchantId("000000123");
        r.setMerchantName("ACME");
        r.setMerchantCity("NYC");
        r.setMerchantZip("10001");
        r.setConfirm("Y");
        return r;
    }

    @Test
    void addsTransactionAndDerivesCardNumberFromAccountId() {
        TransactionAddResponse response = service.processEnterKey(validRequest());

        assertThat(response.isAdded()).isTrue();
        assertThat(response.getMessage())
                .isEqualTo("Transaction added successfully.  Your Tran ID is 0000000000000001.");

        Transaction saved = transactionRepository.findById("0000000000000001").orElseThrow();
        assertThat(saved.getTranCardNum()).isEqualTo(CARD_NUM);
        assertThat(saved.getTranAmt()).isEqualByComparingTo(new BigDecimal("-12.34"));
        assertThat(saved.getTranCatCd()).isEqualTo(1);
        assertThat(saved.getTranMerchantId()).isEqualTo(123L);
        assertThat(saved.getTranOrigTs()).isEqualTo("2024-01-15");
    }

    @Test
    void derivesAccountIdFromCardNumber() {
        TransactionAddRequest request = validRequest();
        request.setAcctId(null);
        request.setCardNum(CARD_NUM);

        TransactionAddResponse response = service.processEnterKey(request);

        assertThat(response.isAdded()).isTrue();
        assertThat(transactionRepository.findById(response.getTranId()).orElseThrow().getTranCardNum())
                .isEqualTo(CARD_NUM);
    }

    @Test
    void nextTranIdIsMaxPlusOne() {
        Transaction existing = new Transaction();
        existing.setTranId("0000000000000041");
        existing.setTranCatCd(1);
        existing.setTranAmt(new BigDecimal("1.00"));
        transactionRepository.saveAndFlush(existing);

        TransactionAddResponse response = service.processEnterKey(validRequest());

        assertThat(response.getTranId()).isEqualTo("0000000000000042");
    }

    @Test
    void duplicateTranIdIsReported() {
        // "1" sorts last, so the derived next id (1 + 1 -> 0000000000000002) collides
        // with an existing record: the DUPREC case of WRITE-TRANSACT-FILE.
        save("1");
        save("0000000000000002");

        TransactionAddResponse response = service.processEnterKey(validRequest());

        assertThat(response.isError()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Tran ID already exist...");
        assertThat(transactionRepository.count()).isEqualTo(2);
    }

    private void save(String tranId) {
        Transaction transaction = new Transaction();
        transaction.setTranId(tranId);
        transaction.setTranCatCd(1);
        transaction.setTranAmt(new BigDecimal("1.00"));
        transactionRepository.saveAndFlush(transaction);
    }

    @Test
    void confirmBlankPromptsForConfirmation() {
        TransactionAddRequest request = validRequest();
        request.setConfirm(null);

        TransactionAddResponse response = service.processEnterKey(request);

        assertThat(response.isError()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Confirm to add this transaction...");
        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void confirmNDoesNotPersist() {
        TransactionAddRequest request = validRequest();
        request.setConfirm("N");

        TransactionAddResponse response = service.processEnterKey(request);

        assertThat(response.getMessage()).isEqualTo("Confirm to add this transaction...");
        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void confirmOtherValueIsRejected() {
        TransactionAddRequest request = validRequest();
        request.setConfirm("X");

        TransactionAddResponse response = service.processEnterKey(request);

        assertThat(response.getMessage()).isEqualTo("Invalid value. Valid values are (Y/N)...");
    }

    @Test
    void keyFieldValidation() {
        TransactionAddRequest empty = validRequest();
        empty.setAcctId(null);
        assertThat(service.processEnterKey(empty).getMessage())
                .isEqualTo("Account or Card Number must be entered...");

        TransactionAddRequest nonNumericAcct = validRequest();
        nonNumericAcct.setAcctId("0000000001A");
        assertThat(service.processEnterKey(nonNumericAcct).getMessage())
                .isEqualTo("Account ID must be Numeric...");

        TransactionAddRequest unknownAcct = validRequest();
        unknownAcct.setAcctId("00000000099");
        assertThat(service.processEnterKey(unknownAcct).getMessage())
                .isEqualTo("Account ID NOT found...");

        TransactionAddRequest nonNumericCard = validRequest();
        nonNumericCard.setAcctId(null);
        nonNumericCard.setCardNum("411111111111111A");
        assertThat(service.processEnterKey(nonNumericCard).getMessage())
                .isEqualTo("Card Number must be Numeric...");

        TransactionAddRequest unknownCard = validRequest();
        unknownCard.setAcctId(null);
        unknownCard.setCardNum("4111111111119999");
        assertThat(service.processEnterKey(unknownCard).getMessage())
                .isEqualTo("Card Number NOT found...");
    }

    @Test
    void mandatoryDataFieldsAreEnforcedInCobolOrder() {
        assertMessage(r -> r.setTypeCd(null), "Type CD can NOT be empty...");
        assertMessage(r -> r.setCatCd(null), "Category CD can NOT be empty...");
        assertMessage(r -> r.setSource(null), "Source can NOT be empty...");
        assertMessage(r -> r.setDescription(null), "Description can NOT be empty...");
        assertMessage(r -> r.setAmount(null), "Amount can NOT be empty...");
        assertMessage(r -> r.setOrigDate(null), "Orig Date can NOT be empty...");
        assertMessage(r -> r.setProcDate(null), "Proc Date can NOT be empty...");
        assertMessage(r -> r.setMerchantId(null), "Merchant ID can NOT be empty...");
        assertMessage(r -> r.setMerchantName(null), "Merchant Name can NOT be empty...");
        assertMessage(r -> r.setMerchantCity(null), "Merchant City can NOT be empty...");
        assertMessage(r -> r.setMerchantZip(null), "Merchant Zip can NOT be empty...");
    }

    @Test
    void formatValidations() {
        assertMessage(r -> r.setTypeCd("0A"), "Type CD must be Numeric...");
        assertMessage(r -> r.setCatCd("00A1"), "Category CD must be Numeric...");
        assertMessage(r -> r.setAmount("12.34"), "Amount should be in format -99999999.99");
        assertMessage(r -> r.setAmount("-0000012.345"), "Amount should be in format -99999999.99");
        assertMessage(r -> r.setOrigDate("15/01/2024"), "Orig Date should be in format YYYY-MM-DD");
        assertMessage(r -> r.setProcDate("2024/01/16"), "Proc Date should be in format YYYY-MM-DD");
        assertMessage(r -> r.setOrigDate("2024-02-31"), "Orig Date - Not a valid date...");
        assertMessage(r -> r.setProcDate("2024-13-01"), "Proc Date - Not a valid date...");
        assertMessage(r -> r.setMerchantId("00000012A"), "Merchant ID must be Numeric...");
    }

    @Test
    void copyLastPrefillsFieldsAndAsksForConfirmation() {
        service.processEnterKey(validRequest());

        TransactionAddRequest request = new TransactionAddRequest();
        request.setAcctId(ACCT_ID);
        TransactionAddResponse response = service.copyLastTransaction(request);

        assertThat(response.getMessage()).isEqualTo("Confirm to add this transaction...");
        assertThat(response.getFields().getTypeCd()).isEqualTo("01");
        assertThat(response.getFields().getCatCd()).isEqualTo("0001");
        assertThat(response.getFields().getAmount()).isEqualTo("-00000012.34");
        assertThat(response.getFields().getOrigDate()).isEqualTo("2024-01-15");
        assertThat(response.getFields().getMerchantId()).isEqualTo("000000123");
        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    @Test
    void copyLastCanBeConfirmedToAddACopy() {
        service.processEnterKey(validRequest());

        TransactionAddRequest request = new TransactionAddRequest();
        request.setAcctId(ACCT_ID);
        request.setConfirm("y");
        TransactionAddResponse response = service.copyLastTransaction(request);

        assertThat(response.isAdded()).isTrue();
        assertThat(response.getTranId()).isEqualTo("0000000000000002");
        assertThat(transactionRepository.count()).isEqualTo(2);
    }

    private void assertMessage(java.util.function.Consumer<TransactionAddRequest> mutator, String expected) {
        TransactionAddRequest request = validRequest();
        mutator.accept(request);
        assertThat(service.processEnterKey(request).getMessage()).isEqualTo(expected);
    }
}
