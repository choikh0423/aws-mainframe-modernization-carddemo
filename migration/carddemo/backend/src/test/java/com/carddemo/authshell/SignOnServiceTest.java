package com.carddemo.authshell;

import com.carddemo.common.message.CardDemoMessages;
import com.carddemo.common.session.CommareaContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** COSGN00C against the USRSEC rows seeded from app/jcl/DUSRSECJ.jcl. */
@SpringBootTest
class SignOnServiceTest {

    @Autowired
    private SignOnService service;

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

    @Test
    void inputsAreUpperCasedBeforeTheUsrsecRead() {
        assertThat(service.signOn("user0001", "password").signedOn()).isTrue();
    }

    @Test
    void missingUserIdAsksForIt() {
        SignOnResult result = service.signOn("  ", "PASSWORD");
        assertThat(result.signedOn()).isFalse();
        assertThat(result.message()).isEqualTo(CardDemoMessages.SIGNON_ENTER_USER_ID);
        assertThat(result.errorField()).isEqualTo("userId");
    }

    @Test
    void missingPasswordAsksForIt() {
        SignOnResult result = service.signOn("USER0001", "");
        assertThat(result.signedOn()).isFalse();
        assertThat(result.message()).isEqualTo(CardDemoMessages.SIGNON_ENTER_PASSWORD);
        assertThat(result.errorField()).isEqualTo("password");
    }

    @Test
    void unknownUserIdIsReportedAsNotFound() {
        SignOnResult result = service.signOn("NOSUCH01", "PASSWORD");
        assertThat(result.signedOn()).isFalse();
        assertThat(result.message()).isEqualTo(CardDemoMessages.SIGNON_USER_NOT_FOUND);
    }

    @Test
    void wrongPasswordIsReportedSeparately() {
        SignOnResult result = service.signOn("USER0001", "NOTRIGHT");
        assertThat(result.signedOn()).isFalse();
        assertThat(result.message()).isEqualTo(CardDemoMessages.SIGNON_WRONG_PASSWORD);
        assertThat(result.errorField()).isEqualTo("password");
    }

    @Test
    void successfulSignOnBuildsTheCommareaCosgn00cHandsOn() {
        CommareaContext commarea = service.toCommarea(service.signOn("ADMIN001", "PASSWORD"));
        assertThat(commarea.getUserId()).isEqualTo("ADMIN001");
        assertThat(commarea.getUserType()).isEqualTo("A");
        assertThat(commarea.getFromProgram()).isEqualTo("COSGN00C");
        assertThat(commarea.getToProgram()).isEqualTo("COADM01C");
        assertThat(commarea.getPgmContext()).isEqualTo(CommareaContext.PGM_CONTEXT_ENTER);
    }
}
