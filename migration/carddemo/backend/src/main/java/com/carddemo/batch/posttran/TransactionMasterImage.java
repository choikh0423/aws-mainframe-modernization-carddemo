package com.carddemo.batch.posttran;

import com.carddemo.common.domain.TransactionRecord;

/**
 * Renders a {@code transactions} row as the 350-byte TRAN-RECORD of CVTRA05Y,
 * the image {@code REPRO INFILE(FILEIN) OUTFILE(FILEOUT)} copies from the
 * TRANSACT KSDS to {@code TRANSACT.BKUP(+1)} (TRANBKP.jcl:23-33, REPROCT.ctl).
 *
 * <p>CVTRA05Y and CVTRA06Y are field-for-field the same layout, so the picture
 * rules are {@link DailyTransactionImage}'s.
 */
public final class TransactionMasterImage {

    /** CVTRA05Y RECLN, and the backup's DCB=(LRECL=350,RECFM=FB). */
    public static final int RECORD_LENGTH = 350;

    private TransactionMasterImage() {
    }

    public static String render(TransactionRecord transaction) {
        StringBuilder image = new StringBuilder(RECORD_LENGTH);
        image.append(DailyTransactionImage.text(transaction.getId(), 16));
        image.append(DailyTransactionImage.text(transaction.getTypeCd(), 2));
        image.append(DailyTransactionImage.digits(transaction.getCatCd(), 4));
        image.append(DailyTransactionImage.text(transaction.getSource(), 10));
        image.append(DailyTransactionImage.text(transaction.getDescription(), 100));
        image.append(DailyTransactionImage.zoned(transaction.getAmount(), 11, 2));
        image.append(DailyTransactionImage.digits(transaction.getMerchantId(), 9));
        image.append(DailyTransactionImage.text(transaction.getMerchantName(), 50));
        image.append(DailyTransactionImage.text(transaction.getMerchantCity(), 50));
        image.append(DailyTransactionImage.text(transaction.getMerchantZip(), 10));
        image.append(DailyTransactionImage.text(transaction.getCardNum(), 16));
        image.append(DailyTransactionImage.text(transaction.getOrigTs(), 26));
        image.append(DailyTransactionImage.text(transaction.getProcTs(), 26));
        image.append(" ".repeat(20));
        return image.toString();
    }
}
