package com.carddemo.batch.exportimport;

import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.domain.CardRecord;
import com.carddemo.common.domain.CardXrefRecord;
import com.carddemo.common.domain.CustomerRecord;
import com.carddemo.common.domain.TransactionRecord;

import java.math.BigDecimal;

/**
 * Master records shaped like the {@code app/data/ASCII} unloads but with values
 * chosen to exercise the awkward parts of the layout: a negative balance, a
 * short name that has to be space padded, and ids that fill their pictures.
 */
final class ExportImportFixtures {

    private ExportImportFixtures() {
    }

    static CustomerRecord customer() {
        CustomerRecord customer = new CustomerRecord();
        customer.setCustId(1L);
        customer.setFirstName("JOHN");
        customer.setMiddleName("Q");
        customer.setLastName("DOE");
        customer.setAddrLine1("1 MAIN STREET");
        customer.setAddrLine2("APT 2");
        customer.setAddrLine3("NEW YORK");
        customer.setAddrStateCd("NY");
        customer.setAddrCountryCd("USA");
        customer.setAddrZip("10001");
        customer.setPhoneNum1("(212)555-0100");
        customer.setPhoneNum2("(212)555-0101");
        customer.setSsn(123456789L);
        customer.setGovtIssuedId("NY-DL-000123456");
        customer.setDobYyyyMmDd("1978-05-14");
        customer.setEftAccountId("0000001111");
        customer.setPriCardHolderInd("Y");
        customer.setFicoCreditScore(789);
        return customer;
    }

    static AccountRecord account() {
        AccountRecord account = new AccountRecord();
        account.setAcctId(11111111111L);
        account.setActiveStatus("Y");
        account.setCurrBal(new BigDecimal("-1234.56"));
        account.setCreditLimit(new BigDecimal("5000.00"));
        account.setCashCreditLimit(new BigDecimal("500.00"));
        account.setOpenDate("2020-01-01");
        account.setExpiraionDate("2027-01-01");
        account.setReissueDate("2024-01-01");
        account.setCurrCycCredit(new BigDecimal("-10.00"));
        account.setCurrCycDebit(new BigDecimal("42.13"));
        account.setAddrZip("10001");
        account.setGroupId("ZEROAPR");
        return account;
    }

    static CardXrefRecord cardXref() {
        CardXrefRecord xref = new CardXrefRecord();
        xref.setCardNum("4111111111111111");
        xref.setCustId(1L);
        xref.setAcctId(11111111111L);
        return xref;
    }

    static TransactionRecord transaction() {
        TransactionRecord transaction = new TransactionRecord();
        transaction.setId("0000000000000001");
        transaction.setTypeCd("01");
        transaction.setCatCd(5411);
        transaction.setSource("POS       ");
        transaction.setDescription("GROCERIES");
        transaction.setAmount(new BigDecimal("13.75"));
        transaction.setMerchantId(999999999L);
        transaction.setMerchantName("CORNER STORE");
        transaction.setMerchantCity("NEW YORK");
        transaction.setMerchantZip("10001");
        transaction.setCardNum("4111111111111111");
        transaction.setOrigTs("2026-09-09 19:13:41.000000");
        transaction.setProcTs("2026-09-09 19:14:02.000000");
        return transaction;
    }

    static CardRecord card() {
        CardRecord card = new CardRecord();
        card.setCardNum("4111111111111111");
        card.setAcctId(11111111111L);
        card.setCvvCd(123);
        card.setEmbossedName("JOHN Q DOE");
        card.setExpiraionDate("2027-01-01");
        card.setActiveStatus("Y");
        return card;
    }
}
