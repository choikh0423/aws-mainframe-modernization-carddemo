package com.carddemo.batch.statement;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The report writing of CBSTM03A: 5000-CREATE-STATEMENT, 5100-WRITE-HTML-HEADER,
 * 5200-WRITE-HTML-NMADBS, 6000-WRITE-TRANS and the footer written by
 * 4000-TRNXFILE-GET.
 *
 * <p>Text records are STMTFILE's X(80) and HTML records are HTMLFILE's X(100)
 * (CBSTM03A.CBL:44-47), so every line is blank padded and truncated to its record
 * length exactly as the FD does.
 */
@Component
public class StatementRenderer {

    static final int TEXT_RECORD_LENGTH = 80;
    static final int HTML_RECORD_LENGTH = 100;

    // STATEMENT-LINES, CBSTM03A.CBL:85-146.
    private static final String ST_LINE0 = "*".repeat(31) + "START OF STATEMENT" + "*".repeat(31);
    private static final String ST_LINE_RULE = "-".repeat(80);
    private static final String ST_LINE6 = " ".repeat(33) + "Basic Details " + " ".repeat(33);
    private static final String ST_LINE11 = " ".repeat(30) + "TRANSACTION SUMMARY " + " ".repeat(30);
    private static final String ST_LINE13 =
            "Tran ID         " + StatementFormat.text("Tran Details    ", 51) + "  Tran Amount";
    private static final String ST_LINE15 = "*".repeat(32) + "END OF STATEMENT" + "*".repeat(32);
    private static final String ST_ACCT_LABEL = "Account ID         :";
    private static final String ST_BAL_LABEL = "Current Balance    :";
    private static final String ST_FICO_LABEL = "FICO Score         :";
    private static final String ST_TOTAL_LABEL = "Total EXP:";

    // HTML-LINES, CBSTM03A.CBL:148-223.
    private static final String HTML_L01 = "<!DOCTYPE html>";
    private static final String HTML_L02 = "<html lang=\"en\">";
    private static final String HTML_L03 = "<head>";
    private static final String HTML_L04 = "<meta charset=\"utf-8\">";
    private static final String HTML_L05 = "<title>HTML Table Layout</title>";
    private static final String HTML_L06 = "</head>";
    private static final String HTML_L07 = "<body style=\"margin:0px;\">";
    private static final String HTML_L08 =
            "<table  align=\"center\" frame=\"box\" style=\"width:70%; font:12px Segoe UI,sans-serif;\">";
    private static final String HTML_LTRS = "<tr>";
    private static final String HTML_LTRE = "</tr>";
    private static final String HTML_LTDE = "</td>";
    private static final String HTML_L10 =
            "<td colspan=\"3\" style=\"padding:0px 5px;background-color:#1d1d96b3;\">";
    private static final String HTML_L15 =
            "<td colspan=\"3\" style=\"padding:0px 5px;background-color:#FFAF33;\">";
    private static final String HTML_L16 = "<p style=\"font-size:16px\">Bank of XYZ</p>";
    private static final String HTML_L17 = "<p>410 Terry Ave N</p>";
    private static final String HTML_L18 = "<p>Seattle WA 99999</p>";
    private static final String HTML_L22_35 =
            "<td colspan=\"3\" style=\"padding:0px 5px;background-color:#f2f2f2;\">";
    private static final String HTML_L30_42 =
            "<td colspan=\"3\" style=\"padding:0px 5px;background-color:#33FFD1; text-align:center;\">";
    private static final String HTML_L31 = "<p style=\"font-size:16px\">Basic Details</p>";
    private static final String HTML_L43 = "<p style=\"font-size:16px\">Transaction Summary</p>";
    private static final String HTML_L47 =
            "<td style=\"width:25%; padding:0px 5px; background-color:#33FF5E; text-align:left;\">";
    private static final String HTML_L48 = "<p style=\"font-size:16px\">Tran ID</p>";
    private static final String HTML_L50 =
            "<td style=\"width:55%; padding:0px 5px; background-color:#33FF5E; text-align:left;\">";
    private static final String HTML_L51 = "<p style=\"font-size:16px\">Tran Details</p>";
    private static final String HTML_L53 =
            "<td style=\"width:20%; padding:0px 5px; background-color:#33FF5E; text-align:right;\">";
    private static final String HTML_L54 = "<p style=\"font-size:16px\">Amount</p>";
    private static final String HTML_L58 =
            "<td style=\"width:25%; padding:0px 5px; background-color:#f2f2f2; text-align:left;\">";
    private static final String HTML_L61 =
            "<td style=\"width:55%; padding:0px 5px; background-color:#f2f2f2; text-align:left;\">";
    private static final String HTML_L64 =
            "<td style=\"width:20%; padding:0px 5px; background-color:#f2f2f2; text-align:right;\">";
    private static final String HTML_L75 = "<h3>End of Statement</h3>";
    private static final String HTML_L78 = "</table>";
    private static final String HTML_L79 = "</body>";
    private static final String HTML_L80 = "</html>";
    private static final String HTML_L11_PREFIX = "<h3>Statement for Account Number: ";
    private static final String HTML_L23_PREFIX = "<p style=\"font-size:16px\">";

    /** STMTFILE, one X(80) record per line. */
    public List<String> text(Statement statement) {
        Fields fields = new Fields(statement);
        List<String> lines = new ArrayList<>();
        lines.add(ST_LINE0);
        lines.add(fields.name + " ".repeat(5));
        lines.add(fields.address1 + " ".repeat(30));
        lines.add(fields.address2 + " ".repeat(30));
        lines.add(fields.address3);
        lines.add(ST_LINE_RULE);
        lines.add(ST_LINE6);
        lines.add(ST_LINE_RULE);
        lines.add(ST_ACCT_LABEL + fields.accountId + " ".repeat(40));
        lines.add(ST_BAL_LABEL + fields.currentBalance + " ".repeat(47));
        lines.add(ST_FICO_LABEL + fields.ficoScore + " ".repeat(40));
        lines.add(ST_LINE_RULE);
        lines.add(ST_LINE11);
        lines.add(ST_LINE_RULE);
        lines.add(ST_LINE13);
        lines.add(ST_LINE_RULE);
        for (Transaction transaction : fields.transactions) {
            lines.add(transaction.id() + " " + transaction.description() + "$" + transaction.amount());
        }
        lines.add(ST_LINE_RULE);
        lines.add(ST_TOTAL_LABEL + " ".repeat(56) + "$" + StatementFormat.amountPicZ(fields.total));
        lines.add(ST_LINE15);
        return pad(lines, TEXT_RECORD_LENGTH);
    }

    /** HTMLFILE, one X(100) record per line. */
    public List<String> html(Statement statement) {
        Fields fields = new Fields(statement);
        List<String> lines = new ArrayList<>();
        lines.add(HTML_L01);
        lines.add(HTML_L02);
        lines.add(HTML_L03);
        lines.add(HTML_L04);
        lines.add(HTML_L05);
        lines.add(HTML_L06);
        lines.add(HTML_L07);
        lines.add(HTML_L08);
        lines.add(HTML_LTRS);
        lines.add(HTML_L10);
        lines.add(HTML_L11_PREFIX + fields.accountId + "</h3>");
        lines.add(HTML_LTDE);
        lines.add(HTML_LTRE);
        lines.add(HTML_LTRS);
        lines.add(HTML_L15);
        lines.add(HTML_L16);
        lines.add(HTML_L17);
        lines.add(HTML_L18);
        lines.add(HTML_LTDE);
        lines.add(HTML_LTRE);
        lines.add(HTML_LTRS);
        lines.add(HTML_L22_35);

        lines.add(HTML_L23_PREFIX + StatementFormat.upToDoubleBlank(fields.htmlName) + "  </p>");
        lines.add(paragraph(fields.address1));
        lines.add(paragraph(fields.address2));
        lines.add(paragraph(fields.address3));
        lines.add(HTML_LTDE);
        lines.add(HTML_LTRE);
        lines.add(HTML_LTRS);
        lines.add(HTML_L30_42);
        lines.add(HTML_L31);
        lines.add(HTML_LTDE);
        lines.add(HTML_LTRE);
        lines.add(HTML_LTRS);
        lines.add(HTML_L22_35);
        lines.add("<p>Account ID         : " + fields.accountId + "</p>");
        lines.add("<p>Current Balance    : " + fields.currentBalance + "</p>");
        lines.add("<p>FICO Score         : " + fields.ficoScore + "</p>");
        lines.add(HTML_LTDE);
        lines.add(HTML_LTRE);
        lines.add(HTML_LTRS);
        lines.add(HTML_L30_42);
        lines.add(HTML_L43);
        lines.add(HTML_LTDE);
        lines.add(HTML_LTRE);
        lines.add(HTML_LTRS);
        lines.add(HTML_L47);
        lines.add(HTML_L48);
        lines.add(HTML_LTDE);
        lines.add(HTML_L50);
        lines.add(HTML_L51);
        lines.add(HTML_LTDE);
        lines.add(HTML_L53);
        lines.add(HTML_L54);
        lines.add(HTML_LTDE);
        lines.add(HTML_LTRE);

        for (Transaction transaction : fields.transactions) {
            lines.add(HTML_LTRS);
            lines.add(HTML_L58);
            lines.add("<p>" + transaction.id() + "</p>");
            lines.add(HTML_LTDE);
            lines.add(HTML_L61);
            lines.add("<p>" + transaction.description() + "</p>");
            lines.add(HTML_LTDE);
            lines.add(HTML_L64);
            lines.add("<p>" + transaction.amount() + "</p>");
            lines.add(HTML_LTDE);
            lines.add(HTML_LTRE);
        }

        lines.add(HTML_LTRS);
        lines.add(HTML_L10);
        lines.add(HTML_L75);
        lines.add(HTML_LTDE);
        lines.add(HTML_LTRE);
        lines.add(HTML_L78);
        lines.add(HTML_L79);
        lines.add(HTML_L80);
        return pad(lines, HTML_RECORD_LENGTH);
    }

    private static String paragraph(String value) {
        return "<p>" + StatementFormat.upToDoubleBlank(value) + "  </p>";
    }

    private static List<String> pad(List<String> lines, int recordLength) {
        List<String> records = new ArrayList<>(lines.size());
        for (String line : lines) {
            records.add(StatementFormat.text(line, recordLength));
        }
        return records;
    }

    /** The values 5000-CREATE-STATEMENT moves into STATEMENT-LINES. */
    private static final class Fields {

        private final String name;
        private final String htmlName;
        private final String address1;
        private final String address2;
        private final String address3;
        private final String accountId;
        private final String currentBalance;
        private final String ficoScore;
        private final List<Transaction> transactions = new ArrayList<>();
        private BigDecimal total = BigDecimal.ZERO;

        private Fields(Statement statement) {
            String customer = statement.customerRecord();
            String account = statement.accountRecord();

            name = StatementFormat.text(
                    StatementFormat.upToBlank(CustomerLayout.firstName(customer)) + " "
                            + StatementFormat.upToBlank(CustomerLayout.middleName(customer)) + " "
                            + StatementFormat.upToBlank(CustomerLayout.lastName(customer)) + " ",
                    75);
            htmlName = StatementFormat.text(name, 50);
            address1 = StatementFormat.text(CustomerLayout.addressLine1(customer), 50);
            address2 = StatementFormat.text(CustomerLayout.addressLine2(customer), 50);
            address3 = StatementFormat.text(
                    StatementFormat.upToBlank(CustomerLayout.addressLine3(customer)) + " "
                            + StatementFormat.upToBlank(CustomerLayout.stateCode(customer)) + " "
                            + StatementFormat.upToBlank(CustomerLayout.countryCode(customer)) + " "
                            + StatementFormat.upToBlank(CustomerLayout.zip(customer)) + " ",
                    80);
            accountId = StatementFormat.text(AccountLayout.accountId(account), 20);
            currentBalance = StatementFormat.amountPic9(AccountLayout.currentBalance(account));
            ficoScore = StatementFormat.text(CustomerLayout.ficoScore(customer), 20);

            for (String record : statement.transactionRecords()) {
                BigDecimal amount = TrnxLayout.amount(record);
                transactions.add(new Transaction(
                        TrnxLayout.transactionId(record),
                        StatementFormat.text(TrnxLayout.description(record), 49),
                        StatementFormat.amountPicZ(amount)));
                total = total.add(amount);
            }
        }
    }

    private record Transaction(String id, String description, String amount) {
    }
}
