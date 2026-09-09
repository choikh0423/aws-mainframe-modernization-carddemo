package com.carddemo.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;


/**
 * USRSEC VSAM KSDS record CSUSR01Y SEC-USER-DATA (RECLN 80).
 * SEC-USR-TYPE is 'A' (administrator) or 'U' (ordinary user); COSGN00C routes to
 * COADM01C or COMEN01C on that value.
 */
@Entity
@Table(name = "sec_users")
public class SecUserRecord {

    @Id
    @Column(name = "sec_usr_id", length = 8, nullable = false)
    private String secUsrId;

    @Column(name = "sec_usr_fname", length = 20, nullable = false)
    private String secUsrFname;

    @Column(name = "sec_usr_lname", length = 20, nullable = false)
    private String secUsrLname;

    @Column(name = "sec_usr_pwd", length = 8, nullable = false)
    private String secUsrPwd;

    @Column(name = "sec_usr_type", length = 1, nullable = false)
    private String secUsrType;

    public SecUserRecord() {
    }

    public String getSecUsrId() { return secUsrId; }
    public void setSecUsrId(String secUsrId) { this.secUsrId = secUsrId; }

    public String getSecUsrFname() { return secUsrFname; }
    public void setSecUsrFname(String secUsrFname) { this.secUsrFname = secUsrFname; }

    public String getSecUsrLname() { return secUsrLname; }
    public void setSecUsrLname(String secUsrLname) { this.secUsrLname = secUsrLname; }

    public String getSecUsrPwd() { return secUsrPwd; }
    public void setSecUsrPwd(String secUsrPwd) { this.secUsrPwd = secUsrPwd; }

    public String getSecUsrType() { return secUsrType; }
    public void setSecUsrType(String secUsrType) { this.secUsrType = secUsrType; }
}
