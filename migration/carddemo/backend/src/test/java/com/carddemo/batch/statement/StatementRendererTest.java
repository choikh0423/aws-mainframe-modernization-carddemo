package com.carddemo.batch.statement;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** FR-16 to FR-37: the plain-text and HTML statement bodies. */
class StatementRendererTest {

    private final StatementRenderer renderer = new StatementRenderer();

    private static final Statement STATEMENT = new Statement(
            "4111111111111111",
            StatementFixtures.customer("John", "Q", "Public",
                    "410 Terry Ave N", "Suite 100", "Seattle", "WA", "USA", "98109", "789"),
            StatementFixtures.account("00000000011", new BigDecimal("-1234.56")),
            List.of(StatementFixtures.work("4111111111111111", "0000000000000042",
                            "Coffee shop", new BigDecimal("13.75")),
                    StatementFixtures.work("4111111111111111", "0000000000000043",
                            "Book store", new BigDecimal("-3.75"))));

    @Test
    void writesEveryTextRecordAtEightyCharacters() {
        assertThat(renderer.text(STATEMENT)).allSatisfy(line ->
                assertThat(line).hasSize(StatementRenderer.TEXT_RECORD_LENGTH));
    }

    @Test
    void writesTheTextStatementInLegacyOrder() {
        List<String> lines = renderer.text(STATEMENT);

        assertThat(lines.get(0)).isEqualTo("*".repeat(31) + "START OF STATEMENT" + "*".repeat(31));
        assertThat(lines.get(1)).isEqualTo(pad("John Q Public "));
        assertThat(lines.get(2)).isEqualTo(pad("410 Terry Ave N"));
        assertThat(lines.get(3)).isEqualTo(pad("Suite 100"));
        assertThat(lines.get(4)).isEqualTo(pad("Seattle WA USA 98109 "));
        assertThat(lines.get(5)).isEqualTo("-".repeat(80));
        assertThat(lines.get(6)).isEqualTo(pad(" ".repeat(33) + "Basic Details"));
        assertThat(lines.get(8)).isEqualTo(pad("Account ID         :00000000011"));
        assertThat(lines.get(9)).isEqualTo(pad("Current Balance    :000001234.56-"));
        assertThat(lines.get(10)).isEqualTo(pad("FICO Score         :789"));
        assertThat(lines.get(12)).isEqualTo(pad(" ".repeat(30) + "TRANSACTION SUMMARY"));
        assertThat(lines.get(14)).isEqualTo(pad("Tran ID         Tran Details"
                + " ".repeat(39) + "  Tran Amount"));
        assertThat(lines).last().isEqualTo("*".repeat(32) + "END OF STATEMENT" + "*".repeat(32));
    }

    @Test
    void writesOneTextDetailLinePerTransactionAndTheirTotal() {
        List<String> lines = renderer.text(STATEMENT);

        assertThat(lines.get(16)).isEqualTo(pad("0000000000000042 "
                + StatementFormat.text("Coffee shop", 49) + "$       13.75 "));
        assertThat(lines.get(17)).isEqualTo(pad("0000000000000043 "
                + StatementFormat.text("Book store", 49) + "$        3.75-"));
        assertThat(lines.get(19)).isEqualTo(pad("Total EXP:" + " ".repeat(56) + "$       10.00 "));
    }

    @Test
    void writesEveryHtmlRecordAtOneHundredCharacters() {
        assertThat(renderer.html(STATEMENT)).allSatisfy(line ->
                assertThat(line).hasSize(StatementRenderer.HTML_RECORD_LENGTH));
    }

    @Test
    void writesTheHtmlDocumentInLegacyOrder() {
        List<String> lines = renderer.html(STATEMENT).stream().map(String::stripTrailing).toList();

        assertThat(lines.subList(0, 8)).containsExactly(
                "<!DOCTYPE html>",
                "<html lang=\"en\">",
                "<head>",
                "<meta charset=\"utf-8\">",
                "<title>HTML Table Layout</title>",
                "</head>",
                "<body style=\"margin:0px;\">",
                "<table  align=\"center\" frame=\"box\" style=\"width:70%; font:12px Segoe UI,sans-serif;\">");
        assertThat(lines.get(10))
                .isEqualTo("<h3>Statement for Account Number: 00000000011         </h3>");
        assertThat(lines).contains("<p style=\"font-size:16px\">Bank of XYZ</p>",
                "<p>410 Terry Ave N  </p>",
                "<p style=\"font-size:16px\">Basic Details</p>",
                "<p>Current Balance    : 000001234.56-</p>",
                "<p>FICO Score         : 789                 </p>",
                "<p style=\"font-size:16px\">Transaction Summary</p>",
                "<h3>End of Statement</h3>");
        assertThat(lines.subList(lines.size() - 3, lines.size()))
                .containsExactly("</table>", "</body>", "</html>");
    }

    @Test
    void writesElevenHtmlRecordsPerTransaction() {
        int withTransactions = renderer.html(STATEMENT).size();
        int withoutTransactions = renderer.html(new Statement(STATEMENT.cardNumber(),
                STATEMENT.customerRecord(), STATEMENT.accountRecord(), List.of())).size();

        assertThat(withTransactions - withoutTransactions).isEqualTo(2 * 11);
    }

    @Test
    void writesTheStatementForACardWithNoTransactions() {
        Statement statement = new Statement(STATEMENT.cardNumber(), STATEMENT.customerRecord(),
                STATEMENT.accountRecord(), List.of());

        List<String> lines = renderer.text(statement);

        assertThat(lines.get(16)).isEqualTo("-".repeat(80));
        assertThat(lines.get(17)).isEqualTo(pad("Total EXP:" + " ".repeat(56) + "$         .00 "));
    }

    @Test
    void buildsTheNameLineFromTheThreeNamePartsCutAtTheirFirstBlank() {
        Statement statement = withCustomer(StatementFixtures.customer("John Paul", "Q Public",
                "Public Jr", "410 Terry Ave N", "Suite 100", "Seattle", "WA", "USA", "98109", "789"));

        assertThat(renderer.text(statement).get(1))
                .isEqualTo(pad(StatementFormat.text("John Q Public ", 75)));
    }

    @Test
    void buildsTheThreeAddressLinesAtTheirCopybookWidths() {
        List<String> lines = renderer.text(STATEMENT);

        assertThat(lines.get(2)).startsWith(StatementFormat.text("410 Terry Ave N", 50));
        assertThat(lines.get(3)).startsWith(StatementFormat.text("Suite 100", 50));
        assertThat(lines.get(4)).isEqualTo(StatementFormat.text("Seattle WA USA 98109 ", 80));
    }

    @Test
    void writesAccountIdAndFicoLeftJustifiedInTwentyColumns() {
        List<String> lines = renderer.text(STATEMENT);

        assertThat(lines.get(8))
                .isEqualTo(pad("Account ID         :" + StatementFormat.text("00000000011", 20)));
        assertThat(lines.get(10))
                .isEqualTo(pad("FICO Score         :" + StatementFormat.text("789", 20)));
    }

    @Test
    void truncatesTheDescriptionToFortyNineColumns() {
        Statement statement = new Statement(STATEMENT.cardNumber(), STATEMENT.customerRecord(),
                STATEMENT.accountRecord(),
                List.of(StatementFixtures.work("4111111111111111", "0000000000000042",
                        "X".repeat(100), new BigDecimal("13.75"))));

        assertThat(renderer.text(statement).get(16))
                .isEqualTo(pad("0000000000000042 " + "X".repeat(49) + "$       13.75 "));
    }

    @Test
    void writesTheHtmlAccountBanner() {
        assertThat(renderer.html(STATEMENT).get(10).stripTrailing())
                .isEqualTo("<h3>Statement for Account Number: "
                        + StatementFormat.text("00000000011", 20) + "</h3>");
    }

    @Test
    void cutsTheHtmlNameAndAddressAtTheFirstDoubleBlank() {
        Statement statement = withCustomer(StatementFixtures.customer("John", "Q", "Public",
                "410 Terry  Ave N", "Suite 100", "Seattle", "WA", "USA", "98109", "789"));

        List<String> lines = renderer.html(statement).stream().map(String::stripTrailing).toList();

        assertThat(lines.get(22)).isEqualTo("<p style=\"font-size:16px\">John Q Public  </p>");
        assertThat(lines.get(23)).isEqualTo("<p>410 Terry  </p>");
    }

    @Test
    void dropsHtmlNameCharactersPastColumnFifty() {
        Statement statement = withCustomer(StatementFixtures.customer("A".repeat(25), "B".repeat(25),
                "C".repeat(25), "410 Terry Ave N", "Suite 100", "Seattle", "WA", "USA", "98109", "789"));

        assertThat(renderer.html(statement).get(22).stripTrailing())
                .isEqualTo("<p style=\"font-size:16px\">" + "A".repeat(25) + " " + "B".repeat(24) + "  </p>");
    }

    @Test
    void writesTheHtmlBasicDetailLinesWithTheirTrailingBlanks() {
        List<String> lines = renderer.html(STATEMENT).stream().map(String::stripTrailing).toList();

        assertThat(lines.get(30)).isEqualTo("<p style=\"font-size:16px\">Basic Details</p>");
        assertThat(lines.get(35))
                .isEqualTo("<p>Account ID         : " + StatementFormat.text("00000000011", 20) + "</p>");
        assertThat(lines.get(36)).isEqualTo("<p>Current Balance    : 000001234.56-</p>");
        assertThat(lines.get(37))
                .isEqualTo("<p>FICO Score         : " + StatementFormat.text("789", 20) + "</p>");
    }

    @Test
    void writesThreeHtmlCellsPerTransaction() {
        List<String> lines = renderer.html(STATEMENT).stream().map(String::stripTrailing).toList();
        int first = lines.indexOf("<p>0000000000000042</p>");

        assertThat(first).isPositive();
        assertThat(lines.get(first - 1)).isEqualTo(
                "<td style=\"width:25%; padding:0px 5px; background-color:#f2f2f2; text-align:left;\">");
        assertThat(lines.get(first + 3))
                .isEqualTo("<p>" + StatementFormat.text("Coffee shop", 49) + "</p>");
        assertThat(lines.get(first + 6)).isEqualTo("<p>       13.75 </p>");
    }

    private static Statement withCustomer(String customerRecord) {
        return new Statement(STATEMENT.cardNumber(), customerRecord, STATEMENT.accountRecord(),
                STATEMENT.transactionRecords());
    }

    private static String pad(String value) {
        return StatementFormat.text(value, StatementRenderer.TEXT_RECORD_LENGTH);
    }
}
