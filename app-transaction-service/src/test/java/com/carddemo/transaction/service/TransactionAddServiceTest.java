package com.carddemo.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carddemo.transaction.dto.AddTransactionRequest;
import com.carddemo.transaction.dto.AddTransactionResponse;
import com.carddemo.transaction.entity.CardXref;
import com.carddemo.transaction.entity.Transaction;
import com.carddemo.transaction.exception.TransactionValidationException;
import com.carddemo.transaction.repository.CardXrefRepository;
import com.carddemo.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionAddServiceTest {

    private static final String CARD_NUM = "4111111111111111";
    private static final long ACCT_ID = 12345678901L;

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private CardXrefRepository cardXrefRepository;

    private TransactionAddService service;

    @BeforeEach
    void setUp() {
        service = new TransactionAddService(transactionRepository, cardXrefRepository);

        CardXref xref = new CardXref();
        xref.setCardNum(CARD_NUM);
        xref.setCustId(9L);
        xref.setAcctId(ACCT_ID);
        when(cardXrefRepository.findByCardNum(CARD_NUM)).thenReturn(Optional.of(xref));
        when(cardXrefRepository.findByAcctId(ACCT_ID)).thenReturn(Optional.of(xref));
        when(transactionRepository.findMaxTranId()).thenReturn(Optional.empty());
        when(transactionRepository.existsById(anyString())).thenReturn(false);
    }

    private AddTransactionRequest validRequest() {
        AddTransactionRequest request = new AddTransactionRequest();
        request.setCardNumber(CARD_NUM);
        request.setTypeCode("01");
        request.setCategoryCode("0001");
        request.setSource("POS TERM");
        request.setDescription("Purchase at Abshire-Lowe");
        request.setAmount("-00000100.50");
        request.setOrigDate("2024-03-01");
        request.setProcDate("2024-03-02");
        request.setMerchantId("123456789");
        request.setMerchantName("Abshire-Lowe");
        request.setMerchantCity("Chicago");
        request.setMerchantZip("60601");
        request.setConfirm(true);
        return request;
    }

    private void expectError(Consumer<AddTransactionRequest> mutator, String message) {
        AddTransactionRequest request = validRequest();
        mutator.accept(request);
        assertThatThrownBy(() -> service.addTransaction(request))
                .isInstanceOf(TransactionValidationException.class)
                .hasMessage(message);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void rejectsMissingKeyFields() {
        expectError(r -> {
            r.setAccountId("   ");
            r.setCardNumber(null);
        }, "Account or Card Number must be entered...");
    }

    @Test
    void rejectsNonNumericAccountId() {
        expectError(r -> {
            r.setCardNumber(null);
            r.setAccountId("1234A");
        }, "Account ID must be Numeric...");
    }

    @Test
    void rejectsNonNumericCardNumber() {
        expectError(r -> r.setCardNumber("4111-1111"), "Card Number must be Numeric...");
    }

    @Test
    void rejectsUnknownAccountId() {
        expectError(r -> {
            r.setCardNumber(null);
            r.setAccountId("99999999999");
        }, "Account ID NOT found...");
    }

    @Test
    void rejectsUnknownCardNumber() {
        expectError(r -> r.setCardNumber("4222222222222222"), "Card Number NOT found...");
    }

    @Test
    void derivesCardNumberFromAccountId() {
        AddTransactionRequest request = validRequest();
        request.setCardNumber(null);
        request.setAccountId(String.valueOf(ACCT_ID));

        service.addTransaction(request);

        ArgumentCaptor<Transaction> saved = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(saved.capture());
        assertThat(saved.getValue().getCardNumber()).isEqualTo(CARD_NUM);
    }

    @ParameterizedTest
    @CsvSource({
            "typeCode,Type CD can NOT be empty...",
            "categoryCode,Category CD can NOT be empty...",
            "source,Source can NOT be empty...",
            "description,Description can NOT be empty...",
            "amount,Amount can NOT be empty...",
            "origDate,Orig Date can NOT be empty...",
            "procDate,Proc Date can NOT be empty...",
            "merchantId,Merchant ID can NOT be empty...",
            "merchantName,Merchant Name can NOT be empty...",
            "merchantCity,Merchant City can NOT be empty...",
            "merchantZip,Merchant Zip can NOT be empty..."
    })
    void rejectsEmptyDetailFields(String field, String message) {
        expectError(r -> blank(r, field), message);
    }

    private static void blank(AddTransactionRequest request, String field) {
        switch (field) {
            case "typeCode" -> request.setTypeCode("  ");
            case "categoryCode" -> request.setCategoryCode("");
            case "source" -> request.setSource(null);
            case "description" -> request.setDescription("");
            case "amount" -> request.setAmount("   ");
            case "origDate" -> request.setOrigDate(null);
            case "procDate" -> request.setProcDate("");
            case "merchantId" -> request.setMerchantId("  ");
            case "merchantName" -> request.setMerchantName(null);
            case "merchantCity" -> request.setMerchantCity("");
            case "merchantZip" -> request.setMerchantZip("   ");
            default -> throw new IllegalArgumentException(field);
        }
    }

    @Test
    void rejectsNonNumericTypeCode() {
        expectError(r -> r.setTypeCode("AB"), "Type CD must be Numeric...");
    }

    @Test
    void rejectsNonNumericCategoryCode() {
        expectError(r -> r.setCategoryCode("00A1"), "Category CD must be Numeric...");
    }

    @Test
    void rejectsNonNumericMerchantId() {
        expectError(r -> r.setMerchantId("12345678X"), "Merchant ID must be Numeric...");
    }

    @ParameterizedTest
    @CsvSource({"100.50", "-100.50", "-00000100.5", "-0000010050", "x00000100.50"})
    void rejectsBadAmountFormat(String amount) {
        expectError(r -> r.setAmount(amount), "Amount should be in format -99999999.99");
    }

    @ParameterizedTest
    @CsvSource({"03/01/2024", "2024-3-1", "20240301"})
    void rejectsBadOrigDateFormat(String date) {
        expectError(r -> r.setOrigDate(date), "Orig Date should be in format YYYY-MM-DD");
    }

    @Test
    void rejectsBadProcDateFormat() {
        expectError(r -> r.setProcDate("01-03-2024"), "Proc Date should be in format YYYY-MM-DD");
    }

    @Test
    void rejectsInvalidOrigCalendarDate() {
        expectError(r -> r.setOrigDate("2023-02-30"), "Orig Date - Not a valid date...");
    }

    @Test
    void rejectsInvalidProcCalendarDate() {
        expectError(r -> r.setProcDate("2024-13-01"), "Proc Date - Not a valid date...");
    }

    @Test
    void requiresConfirmationBeforePersisting() {
        AddTransactionRequest request = validRequest();
        request.setConfirm(false);

        AddTransactionResponse response = service.addTransaction(request);

        assertThat(response.isAdded()).isFalse();
        assertThat(response.getMessage()).isEqualTo("Confirm to add this transaction...");
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void addsTransactionWithMaxIdPlusOne() {
        when(transactionRepository.findMaxTranId()).thenReturn(Optional.of("0000000000000042"));

        AddTransactionResponse response = service.addTransaction(validRequest());

        assertThat(response.isAdded()).isTrue();
        assertThat(response.getTranId()).isEqualTo("0000000000000043");
        assertThat(response.getMessage())
                .isEqualTo("Transaction added successfully.  Your Tran ID is 0000000000000043.");

        ArgumentCaptor<Transaction> saved = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(saved.capture());
        Transaction transaction = saved.getValue();
        assertThat(transaction.getTranId()).isEqualTo("0000000000000043");
        assertThat(transaction.getAmount()).isEqualByComparingTo(new BigDecimal("-100.50"));
        assertThat(transaction.getCategoryCode()).isEqualTo(1);
        assertThat(transaction.getMerchantId()).isEqualTo(123456789L);
        assertThat(transaction.getCardNumber()).isEqualTo(CARD_NUM);
        assertThat(transaction.getOrigTimestamp()).isEqualTo("2024-03-01");
        assertThat(transaction.getProcTimestamp()).isEqualTo("2024-03-02");
    }

    @Test
    void startsAtOneOnEmptyFile() {
        AddTransactionResponse response = service.addTransaction(validRequest());
        assertThat(response.getTranId()).isEqualTo("0000000000000001");
    }

    @Test
    void rejectsDuplicateTranId() {
        when(transactionRepository.existsById("0000000000000001")).thenReturn(true);
        expectError(r -> {
        }, "Tran ID already exist...");
    }

    @Test
    void reportsFailureWhenSaveBreaks() {
        Mockito.doThrow(new IllegalStateException("boom"))
                .when(transactionRepository).save(any());

        assertThatThrownBy(() -> service.addTransaction(validRequest()))
                .hasMessage("Unable to Add Transaction...");
    }
}
