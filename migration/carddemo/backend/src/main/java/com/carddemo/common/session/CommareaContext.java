package com.carddemo.common.session;

import java.io.Serializable;

/**
 * Java equivalent of CARDDEMO-COMMAREA (app/cpy/COCOM01Y.cpy), the DFHCOMMAREA
 * every CICS program in the estate passes on XCTL/RETURN TRANSID.
 *
 * <p>CICS threaded this record between pseudo-conversational turns; the
 * consolidated app keeps one instance per HTTP session (see
 * {@link CommareaSession}). Field names and the 'A'/'U' user-type values follow
 * the copybook so a migrated screen can be read against its COBOL source.
 */
public class CommareaContext implements Serializable {

    private static final long serialVersionUID = 1L;

    /** CDEMO-USRTYP-ADMIN: administrator, routed to COADM01C. */
    public static final String USER_TYPE_ADMIN = "A";
    /** CDEMO-USRTYP-USER: ordinary user, routed to COMEN01C. */
    public static final String USER_TYPE_USER = "U";

    /** CDEMO-PGM-ENTER: first entry into the target program. */
    public static final int PGM_CONTEXT_ENTER = 0;
    /** CDEMO-PGM-REENTER: re-entry after a pseudo-conversational turn. */
    public static final int PGM_CONTEXT_REENTER = 1;

    // CDEMO-GENERAL-INFO
    private String fromTranId;
    private String fromProgram;
    private String toTranId;
    private String toProgram;
    private String userId;
    private String userType;
    private int pgmContext = PGM_CONTEXT_ENTER;

    // CDEMO-CUSTOMER-INFO
    private Long custId;
    private String custFname;
    private String custMname;
    private String custLname;

    // CDEMO-ACCOUNT-INFO
    private Long acctId;
    private String acctStatus;

    // CDEMO-CARD-INFO
    private String cardNum;

    // CDEMO-MORE-INFO
    private String lastMap;
    private String lastMapset;

    /** True for CDEMO-USRTYP-ADMIN, the gate COMEN01C/COADM01C use for admin options. */
    public boolean isAdmin() {
        return USER_TYPE_ADMIN.equals(userType);
    }

    /**
     * Records an XCTL-style hand-off: the current target becomes the origin and
     * the new target is stored, exactly as the COBOL programs do before
     * {@code EXEC CICS XCTL}.
     */
    public void transferTo(String tranId, String program) {
        this.fromTranId = this.toTranId;
        this.fromProgram = this.toProgram;
        this.toTranId = tranId;
        this.toProgram = program;
    }

    public String getFromTranId() { return fromTranId; }
    public void setFromTranId(String fromTranId) { this.fromTranId = fromTranId; }

    public String getFromProgram() { return fromProgram; }
    public void setFromProgram(String fromProgram) { this.fromProgram = fromProgram; }

    public String getToTranId() { return toTranId; }
    public void setToTranId(String toTranId) { this.toTranId = toTranId; }

    public String getToProgram() { return toProgram; }
    public void setToProgram(String toProgram) { this.toProgram = toProgram; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserType() { return userType; }
    public void setUserType(String userType) { this.userType = userType; }

    public int getPgmContext() { return pgmContext; }
    public void setPgmContext(int pgmContext) { this.pgmContext = pgmContext; }

    public Long getCustId() { return custId; }
    public void setCustId(Long custId) { this.custId = custId; }

    public String getCustFname() { return custFname; }
    public void setCustFname(String custFname) { this.custFname = custFname; }

    public String getCustMname() { return custMname; }
    public void setCustMname(String custMname) { this.custMname = custMname; }

    public String getCustLname() { return custLname; }
    public void setCustLname(String custLname) { this.custLname = custLname; }

    public Long getAcctId() { return acctId; }
    public void setAcctId(Long acctId) { this.acctId = acctId; }

    public String getAcctStatus() { return acctStatus; }
    public void setAcctStatus(String acctStatus) { this.acctStatus = acctStatus; }

    public String getCardNum() { return cardNum; }
    public void setCardNum(String cardNum) { this.cardNum = cardNum; }

    public String getLastMap() { return lastMap; }
    public void setLastMap(String lastMap) { this.lastMap = lastMap; }

    public String getLastMapset() { return lastMapset; }
    public void setLastMapset(String lastMapset) { this.lastMapset = lastMapset; }
}
