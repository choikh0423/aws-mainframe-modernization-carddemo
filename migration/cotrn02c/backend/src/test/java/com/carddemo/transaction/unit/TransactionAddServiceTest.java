package com.carddemo.transaction.unit;

import com.carddemo.transaction.dto.AddTransactionRequest;
import com.carddemo.transaction.dto.AddTransactionResponse;
import com.carddemo.transaction.dto.CopyLastTransactionResponse;
import com.carddemo.transaction.entity.CardXrefRecord;
import com.carddemo.transaction.entity.TransactionRecord;
import com.carddemo.transaction.exception.ValidationException;
import com.carddemo.transaction.repository.CardXrefRepository;
import com.carddemo.transaction.repository.TransactionRepository;
import com.carddemo.transaction.service.TransactionAddService;
import com.carddemo.transaction.validation.TransactionValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TransactionAddService.
 * Tests the add-transaction and copy-last-transaction flows.
 */
@ExtendWith(MockitoExtension.class)
class TransactionAddServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CardXrefRepository cardXrefRepository;

    private TransactionAddService service;

    @BeforeEach
    void setUp() {
        TransactionValidator validator = new TransactionValidator(cardXrefRepository);
        service = new TransactionAddService(validator, transactionRepository);
    }

    private CardXrefRecord makeXref(String cardNum, long custId, long acctId) {
        CardXrefRecord rec = new CardXrefRecord();
        rec.setXrefCardNum(cardNum);
        rec.setXrefCustId(custId);
        rec.setXrefAcctId(acctId);
        return rec;
    }

    private AddTransactionRequest makeValidRequest() {
        AddTransactionRequest req = new AddTransactionRequest();
        req.setAcctId("1");
        req.setTypeCd("01");
        req.setCatCd("5001");
        req.setSource("MANUAL");
        req.setDescription("Test transaction");
        req.setAmount("+00000250.00");
        req.setOrigDate("2024-01-15");
        req.setProcDate("2024-01-16");
        req.setMerchantId("123456789");
        req.setMerchantName("Test Merchant");
        req.setMerchantCity("Chicago");
        req.setMerchantZip("60601");
        req.setConfirm("Y");
        return req;
    }

    @Test
    @DisplayName("processSubmission: successful add — generates zero-padded tran ID")
    void processSubmission_success() {
        AddTransactionRequest req = makeValidRequest();
        CardXrefRecord xref = makeXref("4111111111111111", 100000001, 1);
        when(cardXrefRepository.findByXrefAcctId(1L)).thenReturn(Optional.of(xref));

        TransactionRecord lastTran = new TransactionRecord();
        lastTran.setTranId("0000000000000005");
        when(transactionRepository.findTopByOrderByTranIdDesc())
                .thenReturn(Optional.of(lastTran));

        AddTransactionResponse resp = service.processSubmission(req);

        assertTrue(resp.isSuccess());
        assertEquals("0000000000000006", resp.getTranId());
        assertTrue(resp.getMessage().contains("6"));

        ArgumentCaptor<TransactionRecord> captor = ArgumentCaptor.forClass(TransactionRecord.class);
        verify(transactionRepository).save(captor.capture());
        TransactionRecord saved = captor.getValue();
        assertEquals("0000000000000006", saved.getTranId());
        assertEquals("01", saved.getTranTypeCd());
        assertEquals(5001, saved.getTranCatCd());
        assertEquals("MANUAL", saved.getTranSource());
        assertEquals("Test transaction", saved.getTranDesc());
        assertEquals(new BigDecimal("250.00"), saved.getTranAmt());
        assertEquals("4111111111111111", saved.getTranCardNum());
        assertEquals(123456789L, saved.getTranMerchantId());
    }

    @Test
    @DisplayName("processSubmission: first transaction — ID starts at 1")
    void processSubmission_firstTransaction() {
        AddTransactionRequest req = makeValidRequest();
        CardXrefRecord xref = makeXref("4111111111111111", 100000001, 1);
        when(cardXrefRepository.findByXrefAcctId(1L)).thenReturn(Optional.of(xref));
        when(transactionRepository.findTopByOrderByTranIdDesc()).thenReturn(Optional.empty());

        AddTransactionResponse resp = service.processSubmission(req);

        assertTrue(resp.isSuccess());
        assertEquals("0000000000000001", resp.getTranId());
    }

    @Test
    @DisplayName("processSubmission: validation error short-circuits before save")
    void processSubmission_validationError() {
        AddTransactionRequest req = makeValidRequest();
        req.setAcctId("ABC"); // non-numeric

        assertThrows(ValidationException.class, () -> service.processSubmission(req));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("validateOnly: returns resolved card/acct and normalized amount")
    void validateOnly_success() {
        AddTransactionRequest req = makeValidRequest();
        CardXrefRecord xref = makeXref("4111111111111111", 100000001, 1);
        when(cardXrefRepository.findByXrefAcctId(1L)).thenReturn(Optional.of(xref));

        AddTransactionResponse resp = service.validateOnly(req);

        assertTrue(resp.isSuccess());
        assertEquals("4111111111111111", resp.getResolvedCardNum());
        assertEquals("00000000001", resp.getResolvedAcctId());
        assertEquals("+00000250.00", resp.getNormalizedAmount());
        assertEquals("Confirm to add this transaction...", resp.getMessage());
    }

    @Test
    @DisplayName("copyLastTransaction: returns all 11 data fields from last record")
    void copyLastTransaction_success() {
        AddTransactionRequest keyReq = new AddTransactionRequest();
        keyReq.setAcctId("1");
        CardXrefRecord xref = makeXref("4111111111111111", 100000001, 1);
        when(cardXrefRepository.findByXrefAcctId(1L)).thenReturn(Optional.of(xref));

        TransactionRecord tran = new TransactionRecord();
        tran.setTranId("0000000000000001");
        tran.setTranTypeCd("01");
        tran.setTranCatCd(5001);
        tran.setTranSource("MANUAL");
        tran.setTranDesc("Seed transaction");
        tran.setTranAmt(new BigDecimal("250.00"));
        tran.setTranMerchantId(123456789L);
        tran.setTranMerchantName("Seed Merchant");
        tran.setTranMerchantCity("Chicago");
        tran.setTranMerchantZip("60601");
        tran.setTranOrigTs("2024-01-01");
        tran.setTranProcTs("2024-01-02");
        when(transactionRepository.findTopByOrderByTranIdDesc()).thenReturn(Optional.of(tran));

        CopyLastTransactionResponse resp = service.copyLastTransaction(keyReq);

        assertTrue(resp.isSuccess());
        assertEquals("01", resp.getTypeCd());
        assertEquals("5001", resp.getCatCd());
        assertEquals("MANUAL", resp.getSource());
        assertEquals("Seed transaction", resp.getDescription());
        assertEquals("+00000250.00", resp.getAmount());
        assertEquals("2024-01-01", resp.getOrigDate());
        assertEquals("2024-01-02", resp.getProcDate());
        assertEquals("123456789", resp.getMerchantId());
        assertEquals("Seed Merchant", resp.getMerchantName());
        assertEquals("Chicago", resp.getMerchantCity());
        assertEquals("60601", resp.getMerchantZip());
    }

    @Test
    @DisplayName("copyLastTransaction: no transactions — returns empty message")
    void copyLastTransaction_empty() {
        AddTransactionRequest keyReq = new AddTransactionRequest();
        keyReq.setAcctId("1");
        CardXrefRecord xref = makeXref("4111111111111111", 100000001, 1);
        when(cardXrefRepository.findByXrefAcctId(1L)).thenReturn(Optional.of(xref));
        when(transactionRepository.findTopByOrderByTranIdDesc()).thenReturn(Optional.empty());

        CopyLastTransactionResponse resp = service.copyLastTransaction(keyReq);

        assertTrue(resp.isSuccess());
        assertEquals("No transactions found to copy.", resp.getMessage());
    }
}
