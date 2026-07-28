package com.carddemo.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * TRAN-RECORD (copybook CVTRA05Y, RECLN = 350).
 */
@Entity
@Table(name = "transact")
public class Transaction {

    public static final int TRAN_ID_LEN = 16;
    public static final int TRAN_TYPE_CD_LEN = 2;
    public static final int TRAN_CAT_CD_LEN = 4;
    public static final int TRAN_SOURCE_LEN = 10;
    public static final int TRAN_DESC_LEN = 100;
    public static final int TRAN_MERCHANT_ID_LEN = 9;
    public static final int TRAN_MERCHANT_NAME_LEN = 50;
    public static final int TRAN_MERCHANT_CITY_LEN = 50;
    public static final int TRAN_MERCHANT_ZIP_LEN = 10;
    public static final int TRAN_CARD_NUM_LEN = 16;
    public static final int TRAN_TS_LEN = 26;

    @Id
    @Column(name = "tran_id", length = TRAN_ID_LEN, nullable = false)
    private String tranId;

    @Column(name = "tran_type_cd", length = TRAN_TYPE_CD_LEN)
    private String tranTypeCd;

    @Column(name = "tran_cat_cd")
    private Integer tranCatCd;

    @Column(name = "tran_source", length = TRAN_SOURCE_LEN)
    private String tranSource;

    @Column(name = "tran_desc", length = TRAN_DESC_LEN)
    private String tranDesc;

    @Column(name = "tran_amt", precision = 11, scale = 2)
    private BigDecimal tranAmt;

    @Column(name = "tran_merchant_id")
    private Long tranMerchantId;

    @Column(name = "tran_merchant_name", length = TRAN_MERCHANT_NAME_LEN)
    private String tranMerchantName;

    @Column(name = "tran_merchant_city", length = TRAN_MERCHANT_CITY_LEN)
    private String tranMerchantCity;

    @Column(name = "tran_merchant_zip", length = TRAN_MERCHANT_ZIP_LEN)
    private String tranMerchantZip;

    @Column(name = "tran_card_num", length = TRAN_CARD_NUM_LEN)
    private String tranCardNum;

    @Column(name = "tran_orig_ts", length = TRAN_TS_LEN)
    private String tranOrigTs;

    @Column(name = "tran_proc_ts", length = TRAN_TS_LEN)
    private String tranProcTs;

    public String getTranId() {
        return tranId;
    }

    public void setTranId(String tranId) {
        this.tranId = tranId;
    }

    public String getTranTypeCd() {
        return tranTypeCd;
    }

    public void setTranTypeCd(String tranTypeCd) {
        this.tranTypeCd = tranTypeCd;
    }

    public Integer getTranCatCd() {
        return tranCatCd;
    }

    public void setTranCatCd(Integer tranCatCd) {
        this.tranCatCd = tranCatCd;
    }

    public String getTranSource() {
        return tranSource;
    }

    public void setTranSource(String tranSource) {
        this.tranSource = tranSource;
    }

    public String getTranDesc() {
        return tranDesc;
    }

    public void setTranDesc(String tranDesc) {
        this.tranDesc = tranDesc;
    }

    public BigDecimal getTranAmt() {
        return tranAmt;
    }

    public void setTranAmt(BigDecimal tranAmt) {
        this.tranAmt = tranAmt;
    }

    public Long getTranMerchantId() {
        return tranMerchantId;
    }

    public void setTranMerchantId(Long tranMerchantId) {
        this.tranMerchantId = tranMerchantId;
    }

    public String getTranMerchantName() {
        return tranMerchantName;
    }

    public void setTranMerchantName(String tranMerchantName) {
        this.tranMerchantName = tranMerchantName;
    }

    public String getTranMerchantCity() {
        return tranMerchantCity;
    }

    public void setTranMerchantCity(String tranMerchantCity) {
        this.tranMerchantCity = tranMerchantCity;
    }

    public String getTranMerchantZip() {
        return tranMerchantZip;
    }

    public void setTranMerchantZip(String tranMerchantZip) {
        this.tranMerchantZip = tranMerchantZip;
    }

    public String getTranCardNum() {
        return tranCardNum;
    }

    public void setTranCardNum(String tranCardNum) {
        this.tranCardNum = tranCardNum;
    }

    public String getTranOrigTs() {
        return tranOrigTs;
    }

    public void setTranOrigTs(String tranOrigTs) {
        this.tranOrigTs = tranOrigTs;
    }

    public String getTranProcTs() {
        return tranProcTs;
    }

    public void setTranProcTs(String tranProcTs) {
        this.tranProcTs = tranProcTs;
    }
}
