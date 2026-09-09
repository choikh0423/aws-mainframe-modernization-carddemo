package com.carddemo.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * IMS child segment PAUTDTL1 of DBPAUTP0 (copybook CIPAUDTY), flattened to a table.
 * The IMS hierarchy is made explicit: {@code paAcctId} is the FK to
 * {@link PendingAuthSummaryRecord}, and the segment sequence field PAUT9CTS is
 * kept as its two source fields PA-AUTH-DATE-9C / PA-AUTH-TIME-9C.
 */
@Entity
@Table(name = "pending_auth_detail", indexes = @Index(name = "idx_pending_auth_detail_card", columnList = "pa_card_num"))
public class PendingAuthDetailRecord {

    @EmbeddedId
    private PendingAuthDetailId id;

    @Column(name = "pa_auth_orig_date", length = 6)
    private String paAuthOrigDate;

    @Column(name = "pa_auth_orig_time", length = 6)
    private String paAuthOrigTime;

    @Column(name = "pa_card_num", length = 16, nullable = false)
    private String paCardNum;

    @Column(name = "pa_auth_type", length = 4)
    private String paAuthType;

    @Column(name = "pa_card_expiry_date", length = 4)
    private String paCardExpiryDate;

    @Column(name = "pa_message_type", length = 6)
    private String paMessageType;

    @Column(name = "pa_message_source", length = 6)
    private String paMessageSource;

    @Column(name = "pa_auth_id_code", length = 6)
    private String paAuthIdCode;

    @Column(name = "pa_auth_resp_code", length = 2)
    private String paAuthRespCode;

    @Column(name = "pa_auth_resp_reason", length = 4)
    private String paAuthRespReason;

    @Column(name = "pa_processing_code")
    private Long paProcessingCode;

    @Column(name = "pa_transaction_amt", precision = 12, scale = 2)
    private BigDecimal paTransactionAmt;

    @Column(name = "pa_approved_amt", precision = 12, scale = 2)
    private BigDecimal paApprovedAmt;

    @Column(name = "pa_merchant_catagory_code", length = 4)
    private String paMerchantCatagoryCode;

    @Column(name = "pa_acqr_country_code", length = 3)
    private String paAcqrCountryCode;

    @Column(name = "pa_pos_entry_mode")
    private Integer paPosEntryMode;

    @Column(name = "pa_merchant_id", length = 15)
    private String paMerchantId;

    @Column(name = "pa_merchant_name", length = 22)
    private String paMerchantName;

    @Column(name = "pa_merchant_city", length = 13)
    private String paMerchantCity;

    @Column(name = "pa_merchant_state", length = 2)
    private String paMerchantState;

    @Column(name = "pa_merchant_zip", length = 9)
    private String paMerchantZip;

    @Column(name = "pa_transaction_id", length = 15)
    private String paTransactionId;

    @Column(name = "pa_match_status", length = 1)
    private String paMatchStatus;

    @Column(name = "pa_auth_fraud", length = 1)
    private String paAuthFraud;

    @Column(name = "pa_fraud_rpt_date", length = 8)
    private String paFraudRptDate;

    public PendingAuthDetailRecord() {
    }

    public PendingAuthDetailId getId() { return id; }
    public void setId(PendingAuthDetailId id) { this.id = id; }

    public String getPaAuthOrigDate() { return paAuthOrigDate; }
    public void setPaAuthOrigDate(String paAuthOrigDate) { this.paAuthOrigDate = paAuthOrigDate; }

    public String getPaAuthOrigTime() { return paAuthOrigTime; }
    public void setPaAuthOrigTime(String paAuthOrigTime) { this.paAuthOrigTime = paAuthOrigTime; }

    public String getPaCardNum() { return paCardNum; }
    public void setPaCardNum(String paCardNum) { this.paCardNum = paCardNum; }

    public String getPaAuthType() { return paAuthType; }
    public void setPaAuthType(String paAuthType) { this.paAuthType = paAuthType; }

    public String getPaCardExpiryDate() { return paCardExpiryDate; }
    public void setPaCardExpiryDate(String paCardExpiryDate) { this.paCardExpiryDate = paCardExpiryDate; }

    public String getPaMessageType() { return paMessageType; }
    public void setPaMessageType(String paMessageType) { this.paMessageType = paMessageType; }

    public String getPaMessageSource() { return paMessageSource; }
    public void setPaMessageSource(String paMessageSource) { this.paMessageSource = paMessageSource; }

    public String getPaAuthIdCode() { return paAuthIdCode; }
    public void setPaAuthIdCode(String paAuthIdCode) { this.paAuthIdCode = paAuthIdCode; }

    public String getPaAuthRespCode() { return paAuthRespCode; }
    public void setPaAuthRespCode(String paAuthRespCode) { this.paAuthRespCode = paAuthRespCode; }

    public String getPaAuthRespReason() { return paAuthRespReason; }
    public void setPaAuthRespReason(String paAuthRespReason) { this.paAuthRespReason = paAuthRespReason; }

    public Long getPaProcessingCode() { return paProcessingCode; }
    public void setPaProcessingCode(Long paProcessingCode) { this.paProcessingCode = paProcessingCode; }

    public BigDecimal getPaTransactionAmt() { return paTransactionAmt; }
    public void setPaTransactionAmt(BigDecimal paTransactionAmt) { this.paTransactionAmt = paTransactionAmt; }

    public BigDecimal getPaApprovedAmt() { return paApprovedAmt; }
    public void setPaApprovedAmt(BigDecimal paApprovedAmt) { this.paApprovedAmt = paApprovedAmt; }

    public String getPaMerchantCatagoryCode() { return paMerchantCatagoryCode; }
    public void setPaMerchantCatagoryCode(String paMerchantCatagoryCode) { this.paMerchantCatagoryCode = paMerchantCatagoryCode; }

    public String getPaAcqrCountryCode() { return paAcqrCountryCode; }
    public void setPaAcqrCountryCode(String paAcqrCountryCode) { this.paAcqrCountryCode = paAcqrCountryCode; }

    public Integer getPaPosEntryMode() { return paPosEntryMode; }
    public void setPaPosEntryMode(Integer paPosEntryMode) { this.paPosEntryMode = paPosEntryMode; }

    public String getPaMerchantId() { return paMerchantId; }
    public void setPaMerchantId(String paMerchantId) { this.paMerchantId = paMerchantId; }

    public String getPaMerchantName() { return paMerchantName; }
    public void setPaMerchantName(String paMerchantName) { this.paMerchantName = paMerchantName; }

    public String getPaMerchantCity() { return paMerchantCity; }
    public void setPaMerchantCity(String paMerchantCity) { this.paMerchantCity = paMerchantCity; }

    public String getPaMerchantState() { return paMerchantState; }
    public void setPaMerchantState(String paMerchantState) { this.paMerchantState = paMerchantState; }

    public String getPaMerchantZip() { return paMerchantZip; }
    public void setPaMerchantZip(String paMerchantZip) { this.paMerchantZip = paMerchantZip; }

    public String getPaTransactionId() { return paTransactionId; }
    public void setPaTransactionId(String paTransactionId) { this.paTransactionId = paTransactionId; }

    public String getPaMatchStatus() { return paMatchStatus; }
    public void setPaMatchStatus(String paMatchStatus) { this.paMatchStatus = paMatchStatus; }

    public String getPaAuthFraud() { return paAuthFraud; }
    public void setPaAuthFraud(String paAuthFraud) { this.paAuthFraud = paAuthFraud; }

    public String getPaFraudRptDate() { return paFraudRptDate; }
    public void setPaFraudRptDate(String paFraudRptDate) { this.paFraudRptDate = paFraudRptDate; }
}
