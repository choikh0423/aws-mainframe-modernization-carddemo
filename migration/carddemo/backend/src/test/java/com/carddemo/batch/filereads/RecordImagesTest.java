package com.carddemo.batch.filereads;

import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.domain.CardRecord;
import com.carddemo.common.domain.CardXrefRecord;
import com.carddemo.common.domain.CustomerRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** FR-G9, FR-A6, FR-C3, FR-X3, FR-U3: the DISPLAYed record images. */
class RecordImagesTest {

    @Test
    void rendersTheAccountRecordAsThreeHundredBytes() {
        String image = RecordImages.account(account());

        assertThat(image).hasSize(RecordImages.ACCOUNT_LENGTH);
        // The first line of app/data/ASCII/acctdata.txt.
        assertThat(image).startsWith("00000000001Y00000001940{00000020200{00000010200{"
                + "2014-11-202025-05-202025-05-2000000000000{00000000000{A000000000");
        assertThat(image.substring(112, 122)).isEqualTo("          ");
        assertThat(image.substring(122)).isBlank();
    }

    @Test
    void rendersTheCardRecordAsOneHundredAndFiftyBytes() {
        CardRecord card = new CardRecord();
        card.setCardNum("4111111111111111");
        card.setAcctId(1L);
        card.setCvvCd(123);
        card.setEmbossedName("JOHN DOE");
        card.setExpiraionDate("2028-06-30");
        card.setActiveStatus("Y");

        String image = RecordImages.card(card);

        assertThat(image).hasSize(RecordImages.CARD_LENGTH);
        assertThat(image).startsWith("411111111111111100000000001123JOHN DOE");
        assertThat(image.substring(80, 91)).isEqualTo("2028-06-30Y");
        assertThat(image.substring(91)).isBlank();
    }

    @Test
    void rendersTheXrefRecordAsFiftyBytes() {
        CardXrefRecord xref = new CardXrefRecord();
        xref.setCardNum("4111111111111111");
        xref.setCustId(9L);
        xref.setAcctId(1L);

        String image = RecordImages.cardXref(xref);

        assertThat(image).hasSize(RecordImages.XREF_LENGTH);
        assertThat(image).startsWith("411111111111111100000000900000000001");
        assertThat(image.substring(36)).isBlank();
    }

    @Test
    void rendersTheCustomerRecordAsFiveHundredBytes() {
        CustomerRecord customer = new CustomerRecord();
        customer.setCustId(1L);
        customer.setFirstName("AARON");
        customer.setMiddleName("A");
        customer.setLastName("ADAMS");
        customer.setAddrLine1("1 MAIN ST");
        customer.setAddrLine2("APT 1");
        customer.setAddrLine3("SPRINGFIELD");
        customer.setAddrStateCd("IL");
        customer.setAddrCountryCd("USA");
        customer.setAddrZip("62701");
        customer.setPhoneNum1("(217)5550000");
        customer.setPhoneNum2("(217)5550001");
        customer.setSsn(123456789L);
        customer.setGovtIssuedId("IL1234567");
        customer.setDobYyyyMmDd("1970-01-01");
        customer.setEftAccountId("1234567890");
        customer.setPriCardHolderInd("Y");
        customer.setFicoCreditScore(750);

        String image = RecordImages.customer(customer);

        assertThat(image).hasSize(RecordImages.CUSTOMER_LENGTH);
        assertThat(image).startsWith("000000001AARON");
        assertThat(image.substring(9, 34)).isEqualTo("AARON                    ");
        assertThat(image.substring(234, 239)).isEqualTo("ILUSA");
        assertThat(image.substring(279, 288)).isEqualTo("123456789");
        assertThat(image.substring(328, 332)).isEqualTo("Y750");
        assertThat(image.substring(332)).isBlank();
    }

    private AccountRecord account() {
        AccountRecord account = new AccountRecord();
        account.setAcctId(1L);
        account.setActiveStatus("Y");
        account.setCurrBal(new BigDecimal("194.00"));
        account.setCreditLimit(new BigDecimal("2020.00"));
        account.setCashCreditLimit(new BigDecimal("1020.00"));
        account.setOpenDate("2014-11-20");
        account.setExpiraionDate("2025-05-20");
        account.setReissueDate("2025-05-20");
        account.setCurrCycCredit(BigDecimal.ZERO);
        account.setCurrCycDebit(BigDecimal.ZERO);
        account.setAddrZip("A000000000");
        account.setGroupId("");
        return account;
    }
}
