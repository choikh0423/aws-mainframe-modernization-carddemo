package com.carddemo.batch.filereads;

import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.domain.CardRecord;
import com.carddemo.common.domain.CardXrefRecord;
import com.carddemo.common.domain.CustomerRecord;

/**
 * Renders a master-file row back into the copybook record image the S-15 programs
 * emitted with {@code DISPLAY <record>} (FR-G9).
 *
 * <p>Each image is the copybook layout field by field, padded to the record length, so
 * it is byte-identical to the corresponding line of the ASCII unload the row was
 * loaded from (`app/data/ASCII/**`).
 */
public final class RecordImages {

    public static final int ACCOUNT_LENGTH = 300;
    public static final int CARD_LENGTH = 150;
    public static final int XREF_LENGTH = 50;
    public static final int CUSTOMER_LENGTH = 500;

    private RecordImages() {
    }

    /** CVACT01Y ACCOUNT-RECORD, 300 bytes. */
    public static String account(AccountRecord account) {
        StringBuilder image = new StringBuilder(ACCOUNT_LENGTH);
        image.append(CobolPicture.unsigned(account.getAcctId(), 11))
                .append(CobolPicture.text(account.getActiveStatus(), 1))
                .append(CobolPicture.signed(account.getCurrBal(), 10, 2))
                .append(CobolPicture.signed(account.getCreditLimit(), 10, 2))
                .append(CobolPicture.signed(account.getCashCreditLimit(), 10, 2))
                .append(CobolPicture.text(account.getOpenDate(), 10))
                .append(CobolPicture.text(account.getExpiraionDate(), 10))
                .append(CobolPicture.text(account.getReissueDate(), 10))
                .append(CobolPicture.signed(account.getCurrCycCredit(), 10, 2))
                .append(CobolPicture.signed(account.getCurrCycDebit(), 10, 2))
                .append(CobolPicture.text(account.getAddrZip(), 10))
                .append(CobolPicture.text(account.getGroupId(), 10));
        return CobolPicture.text(image.toString(), ACCOUNT_LENGTH);
    }

    /** CVACT02Y CARD-RECORD, 150 bytes. */
    public static String card(CardRecord card) {
        String image = CobolPicture.text(card.getCardNum(), 16)
                + CobolPicture.unsigned(card.getAcctId(), 11)
                + CobolPicture.unsigned(card.getCvvCd(), 3)
                + CobolPicture.text(card.getEmbossedName(), 50)
                + CobolPicture.text(card.getExpiraionDate(), 10)
                + CobolPicture.text(card.getActiveStatus(), 1);
        return CobolPicture.text(image, CARD_LENGTH);
    }

    /** CVACT03Y CARD-XREF-RECORD, 50 bytes. */
    public static String cardXref(CardXrefRecord xref) {
        String image = CobolPicture.text(xref.getCardNum(), 16)
                + CobolPicture.unsigned(xref.getCustId(), 9)
                + CobolPicture.unsigned(xref.getAcctId(), 11);
        return CobolPicture.text(image, XREF_LENGTH);
    }

    /** CVCUS01Y CUSTOMER-RECORD, 500 bytes. */
    public static String customer(CustomerRecord customer) {
        StringBuilder image = new StringBuilder(CUSTOMER_LENGTH);
        image.append(CobolPicture.unsigned(customer.getCustId(), 9))
                .append(CobolPicture.text(customer.getFirstName(), 25))
                .append(CobolPicture.text(customer.getMiddleName(), 25))
                .append(CobolPicture.text(customer.getLastName(), 25))
                .append(CobolPicture.text(customer.getAddrLine1(), 50))
                .append(CobolPicture.text(customer.getAddrLine2(), 50))
                .append(CobolPicture.text(customer.getAddrLine3(), 50))
                .append(CobolPicture.text(customer.getAddrStateCd(), 2))
                .append(CobolPicture.text(customer.getAddrCountryCd(), 3))
                .append(CobolPicture.text(customer.getAddrZip(), 10))
                .append(CobolPicture.text(customer.getPhoneNum1(), 15))
                .append(CobolPicture.text(customer.getPhoneNum2(), 15))
                .append(CobolPicture.unsigned(customer.getSsn(), 9))
                .append(CobolPicture.text(customer.getGovtIssuedId(), 20))
                .append(CobolPicture.text(customer.getDobYyyyMmDd(), 10))
                .append(CobolPicture.text(customer.getEftAccountId(), 10))
                .append(CobolPicture.text(customer.getPriCardHolderInd(), 1))
                .append(CobolPicture.unsigned(customer.getFicoCreditScore(), 3));
        return CobolPicture.text(image.toString(), CUSTOMER_LENGTH);
    }
}
