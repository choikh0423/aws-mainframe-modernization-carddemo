package com.carddemo.authshell;

import com.carddemo.common.domain.SecUserRecord;
import com.carddemo.common.message.CardDemoMessages;
import com.carddemo.common.repository.SecUserRepository;
import com.carddemo.common.session.CommareaContext;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

/**
 * COSGN00C sign-on: read USRSEC by user ID, compare the password, and route to
 * COADM01C or COMEN01C on SEC-USR-TYPE (app/cbl/COSGN00C.cbl:108-140, 209-257).
 *
 * <p>PROCESS-ENTER-KEY refuses a blank user id or password before the read, then
 * upper-cases both inputs into PIC X(08) fields — so anything longer is
 * truncated, not rejected. READ-USER-SEC-FILE keys USRSEC on those 8 bytes and
 * compares SEC-USR-PWD as a fixed-length X(08) field: trailing blanks are
 * insignificant, leading blanks are not. USRSEC holds the password in the clear,
 * so no encoder is involved (boundary BD-3 of the migration plan).
 */
@Service
public class SignOnService {

    private static final String PROGRAM_ADMIN_MENU = "COADM01C";
    private static final String PROGRAM_MAIN_MENU = "COMEN01C";
    private static final String TRAN_ID = ScreenText.SIGNON_TRAN_ID;
    private static final String PROGRAM = ScreenText.SIGNON_PROGRAM;
    /** SEC-USR-ID and SEC-USR-PWD are both PIC X(08) (app/cpy/CSUSR01Y.cpy). */
    private static final int FIELD_LENGTH = ScreenText.SIGNON_FIELD_LENGTH;

    private final SecUserRepository users;

    public SignOnService(SecUserRepository users) {
        this.users = users;
    }

    public SignOnResult signOn(String userId, String password) {
        if (isBlank(userId)) {
            return SignOnResult.failure(CardDemoMessages.SIGNON_ENTER_USER_ID, "userId");
        }
        if (isBlank(password)) {
            return SignOnResult.failure(CardDemoMessages.SIGNON_ENTER_PASSWORD, "password");
        }

        // MOVE FUNCTION UPPER-CASE(USERIDI) TO WS-USER-ID / CDEMO-USER-ID, and the
        // same for the password: both receiving fields are PIC X(08).
        String id = pic(userId);
        String pwd = pic(password);

        Optional<SecUserRecord> found;
        try {
            // RIDFLD(WS-USER-ID) KEYLENGTH(LENGTH OF WS-USER-ID): the key is the
            // 8-byte field; VSAM stores it padded, the table stores it trimmed.
            found = users.findById(id.stripTrailing());
        } catch (DataAccessException e) {
            // COSGN00C's EVALUATE WHEN OTHER branch: the read itself failed.
            return SignOnResult.failure(CardDemoMessages.SIGNON_UNABLE_TO_VERIFY, "userId");
        }

        if (found.isEmpty()) {
            // WHEN 13 - DFHRESP(NOTFND).
            return SignOnResult.failure(CardDemoMessages.SIGNON_USER_NOT_FOUND, "userId");
        }
        SecUserRecord user = found.get();
        if (!pic(user.getSecUsrPwd()).equals(pwd)) {
            return SignOnResult.failure(CardDemoMessages.SIGNON_WRONG_PASSWORD, "password");
        }

        String userType = user.getSecUsrType();
        // IF CDEMO-USRTYP-ADMIN: only 'A' goes to the admin menu, everything else
        // falls through to COMEN01C (COSGN00C.cbl:230-239).
        String nextProgram = CommareaContext.USER_TYPE_ADMIN.equals(userType)
                ? PROGRAM_ADMIN_MENU
                : PROGRAM_MAIN_MENU;
        return SignOnResult.success(id.stripTrailing(), userType, nextProgram);
    }

    /** Builds the COMMAREA COSGN00C hands to the menu program it XCTLs to. */
    public CommareaContext toCommarea(SignOnResult result) {
        CommareaContext commarea = new CommareaContext();
        commarea.setUserId(result.userId());
        commarea.setUserType(result.userType());
        commarea.setPgmContext(CommareaContext.PGM_CONTEXT_ENTER);
        commarea.setFromTranId(TRAN_ID);
        commarea.setFromProgram(PROGRAM);
        commarea.setToProgram(result.nextProgram());
        return commarea;
    }

    /** MOVE FUNCTION UPPER-CASE(value) TO a PIC X(08) field. */
    private static String pic(String value) {
        String upper = value.toUpperCase(Locale.ROOT);
        if (upper.length() > FIELD_LENGTH) {
            return upper.substring(0, FIELD_LENGTH);
        }
        return upper + " ".repeat(FIELD_LENGTH - upper.length());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
