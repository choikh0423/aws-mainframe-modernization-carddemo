package com.carddemo.authshell;

import com.carddemo.common.domain.SecUserRecord;
import com.carddemo.common.message.CardDemoMessages;
import com.carddemo.common.repository.SecUserRepository;
import com.carddemo.common.session.CommareaContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * COSGN00C (FR-SGN-03…14) against the USRSEC rows seeded from
 * app/jcl/DUSRSECJ.jcl. The two branches the seeded data cannot produce — an
 * unexpected SEC-USR-TYPE and a failing read — use a stubbed USRSEC.
 */
@SpringBootTest
class SignOnServiceTest {

    @Autowired
    private SignOnService service;

    private static SecUserRecord usrsecRecord(String id, String password, String type) {
        SecUserRecord record = new SecUserRecord();
        record.setSecUsrId(id);
        record.setSecUsrFname("TEST");
        record.setSecUsrLname("USER");
        record.setSecUsrPwd(password);
        record.setSecUsrType(type);
        return record;
    }

    @Test
    void administratorIsRoutedToTheAdminMenu() {
        SignOnResult result = service.signOn("ADMIN001", "PASSWORD");
        assertThat(result.signedOn()).isTrue();
        assertThat(result.userType()).isEqualTo(CommareaContext.USER_TYPE_ADMIN);
        assertThat(result.nextProgram()).isEqualTo("COADM01C");
    }

    @Test
    void ordinaryUserIsRoutedToTheMainMenu() {
        SignOnResult result = service.signOn("USER0001", "PASSWORD");
        assertThat(result.signedOn()).isTrue();
        assertThat(result.userType()).isEqualTo(CommareaContext.USER_TYPE_USER);
        assertThat(result.nextProgram()).isEqualTo("COMEN01C");
    }

    /** IF CDEMO-USRTYP-ADMIN tests for 'A' only: anything else lands on CM00. */
    @Test
    void anUnexpectedUserTypeFallsThroughToTheMainMenu() {
        SecUserRepository usrsec = mock(SecUserRepository.class);
        given(usrsec.findById("ODDTYPE1"))
                .willReturn(Optional.of(usrsecRecord("ODDTYPE1", "PASSWORD", "X")));

        SignOnResult result = new SignOnService(usrsec).signOn("ODDTYPE1", "PASSWORD");

        assertThat(result.signedOn()).isTrue();
        assertThat(result.userType()).isEqualTo("X");
        assertThat(result.nextProgram()).isEqualTo("COMEN01C");
    }

    @Test
    void inputsAreUpperCasedBeforeTheUsrsecRead() {
        assertThat(service.signOn("user0001", "password").signedOn()).isTrue();
    }

    /** Both inputs land in PIC X(08) fields, so the ninth character is dropped. */
    @Test
    void inputsLongerThanTheEightBytePicAreTruncated() {
        assertThat(service.signOn("USER0001X", "PASSWORDX").signedOn()).isTrue();
    }

    /** SEC-USR-PWD is X(08): trailing blanks make no difference to the compare. */
    @Test
    void storedPasswordComparesAsAFixedLengthField() {
        SecUserRepository usrsec = mock(SecUserRepository.class);
        given(usrsec.findById("PADDED01"))
                .willReturn(Optional.of(usrsecRecord("PADDED01", "PASS    ", "U")));

        assertThat(new SignOnService(usrsec).signOn("PADDED01", "PASS").signedOn()).isTrue();
    }

    /** A leading blank is part of the field, so it does not compare equal. */
    @Test
    void leadingBlanksAreNotIgnored() {
        SignOnResult result = service.signOn("USER0001", " PASSWORD");
        assertThat(result.signedOn()).isFalse();
        assertThat(result.message()).isEqualTo(CardDemoMessages.SIGNON_WRONG_PASSWORD);
    }

    @Test
    void missingUserIdAsksForIt() {
        SecUserRepository usrsec = mock(SecUserRepository.class);

        SignOnResult result = new SignOnService(usrsec).signOn("  ", "PASSWORD");

        assertThat(result.signedOn()).isFalse();
        assertThat(result.message()).isEqualTo(CardDemoMessages.SIGNON_ENTER_USER_ID);
        assertThat(result.errorField()).isEqualTo("userId");
        verifyNoInteractions(usrsec);
    }

    @Test
    void missingPasswordAsksForIt() {
        SecUserRepository usrsec = mock(SecUserRepository.class);

        SignOnResult result = new SignOnService(usrsec).signOn("USER0001", "");

        assertThat(result.signedOn()).isFalse();
        assertThat(result.message()).isEqualTo(CardDemoMessages.SIGNON_ENTER_PASSWORD);
        assertThat(result.errorField()).isEqualTo("password");
        verifyNoInteractions(usrsec);
    }

    @Test
    void unknownUserIdIsReportedAsNotFound() {
        SignOnResult result = service.signOn("NOSUCH01", "PASSWORD");
        assertThat(result.signedOn()).isFalse();
        assertThat(result.message()).isEqualTo(CardDemoMessages.SIGNON_USER_NOT_FOUND);
        assertThat(result.errorField()).isEqualTo("userId");
    }

    @Test
    void wrongPasswordIsReportedSeparately() {
        SignOnResult result = service.signOn("USER0001", "NOTRIGHT");
        assertThat(result.signedOn()).isFalse();
        assertThat(result.message()).isEqualTo(CardDemoMessages.SIGNON_WRONG_PASSWORD);
        assertThat(result.errorField()).isEqualTo("password");
    }

    /** The EVALUATE WHEN OTHER branch: the USRSEC read itself failed. */
    @Test
    void aFailingUsrsecReadIsReportedAsUnableToVerify() {
        SecUserRepository usrsec = mock(SecUserRepository.class);
        given(usrsec.findById(anyString()))
                .willThrow(new DataAccessResourceFailureException("USRSEC unavailable"));

        SignOnResult result = new SignOnService(usrsec).signOn("USER0001", "PASSWORD");

        assertThat(result.signedOn()).isFalse();
        assertThat(result.message()).isEqualTo(CardDemoMessages.SIGNON_UNABLE_TO_VERIFY);
        assertThat(result.errorField()).isEqualTo("userId");
    }

    @Test
    void successfulSignOnBuildsTheCommareaCosgn00cHandsOn() {
        CommareaContext commarea = service.toCommarea(service.signOn("ADMIN001", "PASSWORD"));
        assertThat(commarea.getUserId()).isEqualTo("ADMIN001");
        assertThat(commarea.getUserType()).isEqualTo("A");
        assertThat(commarea.getFromTranId()).isEqualTo("CC00");
        assertThat(commarea.getFromProgram()).isEqualTo("COSGN00C");
        assertThat(commarea.getToProgram()).isEqualTo("COADM01C");
        assertThat(commarea.getPgmContext()).isEqualTo(CommareaContext.PGM_CONTEXT_ENTER);
    }
}
