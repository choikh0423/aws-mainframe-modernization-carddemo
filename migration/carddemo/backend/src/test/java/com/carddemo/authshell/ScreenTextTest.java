package com.carddemo.authshell;

import com.carddemo.common.message.CardDemoMessages;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-SHL-08, FR-SGN-01, FR-MEN-01, FR-ADM-01: every literal the three screens
 * show is the one the BMS map or the COBOL source holds. The maps themselves are
 * read here so the assertion cannot drift from {@code app/bms/**}.
 */
class ScreenTextTest {

    private static final Path BMS = Path.of("..", "..", "..", "app", "bms");
    private static final Path CBL = Path.of("..", "..", "..", "app", "cbl");

    private static String source(Path directory, String name) throws IOException {
        return Files.readString(directory.resolve(name), StandardCharsets.ISO_8859_1);
    }

    /** The literal as a blank-padded PIC X(50) VALUE clause writes it. */
    private static Pattern paddedLiteral(String message) {
        return Pattern.compile(Pattern.quote("'" + message) + " *'");
    }

    /** BMS continues a literal past column 71 with a '-' in column 72. */
    private static String unfolded(String map) {
        return map.replaceAll("-\\s*\\R\\s+", "");
    }

    @Test
    void signOnScreenTextIsVerbatimFromTheMap() throws IOException {
        String map = unfolded(source(BMS, "COSGN00.bms"));

        assertThat(map)
                .contains("INITIAL='" + ScreenText.SIGNON_BANNER + "'")
                .contains("INITIAL='" + ScreenText.SIGNON_PROMPT + "'")
                .contains("INITIAL='" + ScreenText.SIGNON_USER_ID_LABEL + "'")
                .contains("INITIAL='" + ScreenText.SIGNON_PASSWORD_LABEL + "'")
                .contains("INITIAL='" + ScreenText.SIGNON_FIELD_HINT + "'")
                .contains("INITIAL='" + ScreenText.SIGNON_PF_KEYS + "'")
                .contains("INITIAL='" + ScreenText.SIGNON_APPLID_LABEL + "'")
                .contains("INITIAL='" + ScreenText.SIGNON_SYSID_LABEL + "'");

        for (String line : ScreenText.SIGNON_ART) {
            assertThat(map).contains("INITIAL='" + line + "'");
            assertThat(line).hasSize(42);
        }
        assertThat(ScreenText.SIGNON_ART).hasSize(9);
    }

    @Test
    void menuScreenTextIsVerbatimFromTheMaps() throws IOException {
        String main = unfolded(source(BMS, "COMEN01.bms"));
        String admin = unfolded(source(BMS, "COADM01.bms"));

        assertThat(main)
                .contains("INITIAL='" + ScreenText.MAIN_MENU_TITLE + "'")
                .contains("INITIAL='" + ScreenText.MENU_PROMPT + "'")
                .contains("INITIAL='" + ScreenText.MENU_PF_KEYS + "'");
        assertThat(admin)
                .contains("INITIAL='" + ScreenText.ADMIN_MENU_TITLE + "'")
                .contains("INITIAL='" + ScreenText.MENU_PROMPT + "'")
                .contains("INITIAL='" + ScreenText.MENU_PF_KEYS + "'");

        assertThat(ScreenText.MENU_OPTION_LENGTH).isEqualTo(2);
        assertThat(ScreenText.MENU_OPTION_LINE_LENGTH).isEqualTo(40);
        assertThat(ScreenText.SIGNON_FIELD_LENGTH).isEqualTo(8);
    }

    @Test
    void everyShellMessageIsTheCobolLiteral() throws IOException {
        String signOn = source(CBL, "COSGN00C.cbl");
        String mainMenu = source(CBL, "COMEN01C.cbl");
        String adminMenu = source(CBL, "COADM01C.cbl");
        String messages = Files.readString(
                Path.of("..", "..", "..", "app", "cpy", "CSMSG01Y.cpy"), StandardCharsets.ISO_8859_1);

        // CCDA-MSG-* are PIC X(50) VALUE literals, blank-padded to the field width.
        assertThat(messages)
                .containsPattern(paddedLiteral(CardDemoMessages.THANK_YOU))
                .containsPattern(paddedLiteral(CardDemoMessages.INVALID_KEY));

        for (String literal : List.of(CardDemoMessages.SIGNON_ENTER_USER_ID,
                CardDemoMessages.SIGNON_ENTER_PASSWORD, CardDemoMessages.SIGNON_WRONG_PASSWORD,
                CardDemoMessages.SIGNON_USER_NOT_FOUND, CardDemoMessages.SIGNON_UNABLE_TO_VERIFY)) {
            assertThat(signOn).contains("'" + literal + "'");
        }

        assertThat(mainMenu)
                .contains("'" + CardDemoMessages.MENU_INVALID_OPTION + "'")
                .contains("'" + CardDemoMessages.MENU_ADMIN_ONLY + "'")
                .contains("'This option '")
                .contains("' is not installed...'")
                .contains("'is coming soon ...'");
        assertThat(adminMenu)
                .contains("'" + CardDemoMessages.MENU_INVALID_OPTION + "'")
                .contains("'is not installed ...'");
    }
}
