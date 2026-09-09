package com.carddemo.batch.tranreport;

import com.carddemo.common.domain.TransactionCategoryBalanceRecord;

/**
 * The 50-byte TCATBALF record image (app/cpy/CVTRA01Y.cpy,
 * {@code DCB=(LRECL=50)} at app/jcl/PRTCATBL.jcl:37) that PRTCATBL's unload step
 * produces and its SORT step addresses by column:
 *
 * <pre>
 * TRANCAT-ACCT-ID,1,11,ZD
 * TRANCAT-TYPE-CD,12,2,CH
 * TRANCAT-CD,14,4,ZD
 * TRAN-CAT-BAL,18,11,ZD
 * </pre>
 */
final class CategoryBalanceRecordImage {

    static final int LENGTH = 50;

    static final int ACCT_ID = 0;
    static final int ACCT_ID_LENGTH = 11;
    static final int TYPE_CD = 11;
    static final int TYPE_CD_LENGTH = 2;
    static final int CAT_CD = 13;
    static final int CAT_CD_LENGTH = 4;
    static final int BAL = 17;
    static final int BAL_LENGTH = 11;

    private CategoryBalanceRecordImage() {
    }

    static String of(TransactionCategoryBalanceRecord balance) {
        return String.format("%011d", balance.getId().getAcctId())
                + pad(balance.getId().getTypeCd())
                + String.format("%04d", balance.getId().getCatCd())
                + TransactionRecordImage.signed(balance.getBal(), BAL_LENGTH)
                + " ".repeat(LENGTH - BAL - BAL_LENGTH);
    }

    private static String pad(String typeCd) {
        String raw = typeCd == null ? "" : typeCd;
        return raw.length() >= TYPE_CD_LENGTH ? raw.substring(0, TYPE_CD_LENGTH)
                : raw + " ".repeat(TYPE_CD_LENGTH - raw.length());
    }
}
