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
 * COADM01C or COMEN01C on SEC-USR-TYPE.
 *
 * <p>The legacy program upper-cases both inputs before the VSAM read and compares
 * the stored password literally; USRSEC holds it in the clear, so no encoder is
 * involved (boundary: the password column stays as the copybook defines it).
 */
@Service
public class SignOnService {

    private static final String PROGRAM_ADMIN_MENU = "COADM01C";
    private static final String PROGRAM_MAIN_MENU = "COMEN01C";

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

        String id = userId.toUpperCase(Locale.ROOT).trim();
        String pwd = password.toUpperCase(Locale.ROOT).trim();

        Optional<SecUserRecord> found;
        try {
            found = users.findById(id);
        } catch (DataAccessException e) {
            // COSGN00C's EVALUATE WHEN OTHER branch: the read itself failed.
            return SignOnResult.failure(CardDemoMessages.SIGNON_UNABLE_TO_VERIFY, "userId");
        }

        if (found.isEmpty()) {
            return SignOnResult.failure(CardDemoMessages.SIGNON_USER_NOT_FOUND, "userId");
        }
        SecUserRecord user = found.get();
        if (!user.getSecUsrPwd().trim().equals(pwd)) {
            return SignOnResult.failure(CardDemoMessages.SIGNON_WRONG_PASSWORD, "password");
        }

        String userType = user.getSecUsrType();
        String nextProgram = CommareaContext.USER_TYPE_ADMIN.equals(userType)
                ? PROGRAM_ADMIN_MENU
                : PROGRAM_MAIN_MENU;
        return SignOnResult.success(id, userType, nextProgram);
    }

    /** Builds the COMMAREA COSGN00C hands to the menu program it XCTLs to. */
    public CommareaContext toCommarea(SignOnResult result) {
        CommareaContext commarea = new CommareaContext();
        commarea.setUserId(result.userId());
        commarea.setUserType(result.userType());
        commarea.setPgmContext(CommareaContext.PGM_CONTEXT_ENTER);
        commarea.setFromTranId("CC00");
        commarea.setFromProgram("COSGN00C");
        commarea.setToProgram(result.nextProgram());
        return commarea;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
