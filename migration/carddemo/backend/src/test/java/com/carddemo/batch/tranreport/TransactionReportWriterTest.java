package com.carddemo.batch.tranreport;

import com.carddemo.common.batch.AbendException;
import com.carddemo.common.batch.AbendService;
import com.carddemo.common.domain.CardXrefRecord;
import com.carddemo.common.domain.TransactionCategoryId;
import com.carddemo.common.domain.TransactionCategoryRecord;
import com.carddemo.common.domain.TransactionTypeRecord;
import com.carddemo.common.repository.CardXrefRepository;
import com.carddemo.common.repository.TransactionCategoryRepository;
import com.carddemo.common.repository.TransactionTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.test.MetaDataInstanceFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CBTRN03C's report body: the page/account/grand totals (FR-14-010 to FR-14-014),
 * the fixed-width layout (FR-14-015 to FR-14-020) and the three lookup abends
 * (FR-14-007 to FR-14-009).
 */
class TransactionReportWriterTest {

    private static final String START = "2022-07-01";
    private static final String END = "2022-07-06";
    private static final String CARD_A = "4111111111111111";
    private static final String CARD_B = "4222222222222222";

    @TempDir
    Path work;

    private final Map<String, Long> xrefs = new HashMap<>();
    private final Map<String, String> types = new HashMap<>();
    private final Map<String, String> categories = new HashMap<>();

    private CardXrefRepository cardXrefs;
    private TransactionTypeRepository transactionTypes;
    private TransactionCategoryRepository transactionCategories;

    @BeforeEach
    void setUp() {
        xrefs.put(CARD_A, 10000000001L);
        xrefs.put(CARD_B, 10000000002L);
        types.put("01", "Purchase");
        categories.put("011000", "Regular Sales Draft");

        cardXrefs = mock(CardXrefRepository.class);
        when(cardXrefs.findById(any())).thenAnswer(call -> {
            Long acctId = xrefs.get(call.<String>getArgument(0));
            if (acctId == null) {
                return Optional.empty();
            }
            CardXrefRecord xref = new CardXrefRecord();
            xref.setCardNum(call.getArgument(0));
            xref.setAcctId(acctId);
            return Optional.of(xref);
        });

        transactionTypes = mock(TransactionTypeRepository.class);
        when(transactionTypes.findById(any())).thenAnswer(call -> {
            String desc = types.get(call.<String>getArgument(0));
            if (desc == null) {
                return Optional.empty();
            }
            TransactionTypeRecord type = new TransactionTypeRecord();
            type.setTranType(call.getArgument(0));
            type.setTranTypeDesc(desc);
            return Optional.of(type);
        });

        transactionCategories = mock(TransactionCategoryRepository.class);
        when(transactionCategories.findById(any())).thenAnswer(call -> {
            TransactionCategoryId id = call.getArgument(0);
            String desc = categories.get(id.getTranTypeCd() + String.format("%04d", id.getTranCatCd()));
            if (desc == null) {
                return Optional.empty();
            }
            TransactionCategoryRecord category = new TransactionCategoryRecord();
            category.setId(id);
            category.setTranCatTypeDesc(desc);
            return Optional.of(category);
        });
    }

    @Test
    void writesTheHeaderBlockWithTheReportedDateRange() throws IOException {
        List<String> report = run(transaction("0000000000000001", CARD_A, "2022-07-01", "1.00"));

        assertThat(report.get(0).stripTrailing()).isEqualTo(pad("DALYREPT", 38)
                + pad("Daily Transaction Report", 41) + "Date Range: 2022-07-01 to 2022-07-06");
        assertThat(report.get(1)).isBlank();
        assertThat(report.get(2)).startsWith("Transaction ID   Account ID  Transaction Type   Tran Category");
        assertThat(report.get(3)).isEqualTo("-".repeat(133));
    }

    @Test
    void everyRecordIs133Bytes() throws IOException {
        assertThat(run(fullPage())).allMatch(line -> line.length() == 133);
    }

    @Test
    void laysOutTheDetailLineFieldByField() throws IOException {
        List<String> report = run(transaction("0000000000000001", CARD_A, "2022-07-01", "-1307.70"));

        assertThat(report.get(4).stripTrailing()).isEqualTo(
                "0000000000000001 10000000001 01-Purchase        1000-Regular Sales Draft"
                        + "           POS TERM      -      1,307.70");
    }

    /** 1100-WRITE-TRANSACTION-REPORT closes a page every WS-PAGE-SIZE lines. */
    @Test
    void writesAPageTotalAndFreshHeadersEveryTwentyLines() throws IOException {
        List<String> report = run(fullPage());

        assertThat(report.get(4 + 16)).startsWith("Page Total ....");
        assertThat(report.get(4 + 16).stripTrailing())
                .endsWith(TranReportPictures.totalAmount(new BigDecimal("16.00")).stripTrailing());
        assertThat(report.get(4 + 18)).startsWith("DALYREPT");
    }

    /** 1120-WRITE-ACCOUNT-TOTALS runs on a card break, before the new XREF lookup. */
    @Test
    void writesAnAccountTotalWhenTheCardNumberChanges() throws IOException {
        List<String> report = run(
                transaction("0000000000000001", CARD_A, "2022-07-01", "10.00"),
                transaction("0000000000000002", CARD_A, "2022-07-02", "5.00"),
                transaction("0000000000000003", CARD_B, "2022-07-03", "1.00"));

        assertThat(report.get(6)).startsWith("Account Total....");
        assertThat(report.get(6).stripTrailing())
                .endsWith(TranReportPictures.totalAmount(new BigDecimal("15.00")).stripTrailing());
        assertThat(report.get(7)).isEqualTo("-".repeat(133));
        assertThat(report.get(8)).contains("10000000002");
    }

    /**
     * At end of file TRAN-RECORD still holds the last record, so CBTRN03C adds
     * its amount to the totals a second time and never writes an Account Total
     * for the last card. The quirk is reproduced, not fixed.
     */
    @Test
    void countsTheLastRecordTwiceInTheClosingTotals() throws IOException {
        List<String> report = run(
                transaction("0000000000000001", CARD_A, "2022-07-01", "10.00"),
                transaction("0000000000000002", CARD_A, "2022-07-02", "5.00"));

        assertThat(report).noneMatch(line -> line.startsWith("Account Total"));
        assertThat(report.get(6)).startsWith("Page Total ....");
        assertThat(report.get(6).stripTrailing())
                .endsWith(TranReportPictures.totalAmount(new BigDecimal("20.00")).stripTrailing());
        assertThat(report.get(8)).startsWith("Grand Total....");
        assertThat(report.get(8).stripTrailing())
                .endsWith(TranReportPictures.totalAmount(new BigDecimal("20.00")).stripTrailing());
    }

    /** The Grand Total is the sum of the page totals, so it excludes nothing else. */
    @Test
    void accumulatesTheGrandTotalOutOfThePageTotals() throws IOException {
        List<String> report = run(fullPage());

        String grandTotal = report.get(report.size() - 1);
        assertThat(grandTotal).startsWith("Grand Total....");
        // 16.00 from the first page, then 9.00 plus the end-of-file repeat of the last record.
        assertThat(grandTotal.stripTrailing())
                .endsWith(TranReportPictures.totalAmount(new BigDecimal("26.00")).stripTrailing());
    }

    /** A record outside the range hits NEXT SENTENCE: no detail line, no total. */
    @Test
    void skipsTransactionsOutsideTheReportedRange() throws IOException {
        List<String> report = run(
                transaction("0000000000000001", CARD_A, "2022-06-30", "99.00"),
                transaction("0000000000000002", CARD_A, "2022-07-01", "10.00"),
                transaction("0000000000000003", CARD_A, "2022-07-07", "99.00"));

        assertThat(report).noneMatch(line -> line.contains("99.00"));
        assertThat(report).hasSize(5);
        assertThat(report.get(4)).contains("0000000000000002");
    }

    /**
     * The last record read is the one left in TRAN-RECORD, so a trailing
     * out-of-range record suppresses the closing totals altogether.
     */
    @Test
    void writesNoClosingTotalsWhenTheLastRecordIsOutOfRange() throws IOException {
        List<String> report = run(
                transaction("0000000000000001", CARD_A, "2022-07-01", "10.00"),
                transaction("0000000000000002", CARD_A, "2022-07-07", "99.00"));

        assertThat(report).noneMatch(line -> line.startsWith("Page Total"));
        assertThat(report).noneMatch(line -> line.startsWith("Grand Total"));
    }

    @Test
    void writesAnEmptyReportWhenNothingIsSelected() throws IOException {
        assertThat(run()).isEmpty();
    }

    @Test
    void abendsWhenTheCardIsNotInTheCrossReference() {
        assertThatThrownBy(() -> run(transaction("0000000000000001", "4999999999999999", "2022-07-01", "1.00")))
                .isInstanceOf(AbendException.class)
                .extracting(e -> ((AbendException) e).getAbendData())
                .satisfies(data -> {
                    assertThat(data.abendCode()).isEqualTo("0999");
                    assertThat(data.abendCulprit()).isEqualTo("CBTRN03C");
                    assertThat(data.abendReason()).isEqualTo("FILE STATUS IS: NNNN0023");
                    assertThat(data.abendMsg()).isEqualTo("INVALID CARD NUMBER : 4999999999999999");
                });
    }

    @Test
    void abendsWhenTheTransactionTypeIsNotFound() {
        assertThatThrownBy(() -> run(transaction("0000000000000001", CARD_A, "2022-07-01", "1.00", "09", 1000)))
                .isInstanceOf(AbendException.class)
                .extracting(e -> ((AbendException) e).getAbendData().abendMsg())
                .isEqualTo("INVALID TRANSACTION TYPE : 09");
    }

    @Test
    void abendsWhenTheTransactionCategoryIsNotFound() {
        assertThatThrownBy(() -> run(transaction("0000000000000001", CARD_A, "2022-07-01", "1.00", "01", 9999)))
                .isInstanceOf(AbendException.class)
                .extracting(e -> ((AbendException) e).getAbendData().abendMsg())
                .isEqualTo("INVALID TRAN CATG KEY : 019999");
    }

    /** An abending step leaves the partial report behind and adds no totals. */
    @Test
    void writesNoTotalsWhenTheStepFails() throws IOException {
        Path reportFile = work.resolve("TRANREPT");
        TransactionReportWriter writer = writer(reportFile);
        StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
        writer.beforeStep(stepExecution);
        writer.write(Chunk.of(transaction("0000000000000001", CARD_A, "2022-07-01", "10.00")));
        stepExecution.setStatus(BatchStatus.FAILED);
        writer.afterStep(stepExecution);

        assertThat(lines(reportFile)).hasSize(5).noneMatch(line -> line.startsWith("Page Total"));
    }

    private PostedTransaction[] fullPage() {
        PostedTransaction[] transactions = new PostedTransaction[25];
        for (int i = 0; i < transactions.length; i++) {
            transactions[i] = transaction(String.format("%016d", i + 1), CARD_A, "2022-07-01", "1.00");
        }
        return transactions;
    }

    private static PostedTransaction transaction(String id, String cardNum, String procDate, String amount) {
        return transaction(id, cardNum, procDate, amount, "01", 1000);
    }

    private static PostedTransaction transaction(String id, String cardNum, String procDate, String amount,
                                                 String typeCd, int catCd) {
        return new PostedTransaction(id, typeCd, catCd, "POS TERM  ", new BigDecimal(amount), cardNum, procDate);
    }

    private List<String> run(PostedTransaction... transactions) throws IOException {
        Path reportFile = work.resolve("TRANREPT");
        TransactionReportWriter writer = writer(reportFile);
        StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
        writer.beforeStep(stepExecution);
        try {
            for (PostedTransaction transaction : transactions) {
                writer.write(Chunk.of(transaction));
            }
        } catch (RuntimeException e) {
            stepExecution.setStatus(BatchStatus.FAILED);
            writer.afterStep(stepExecution);
            throw e;
        }
        writer.afterStep(stepExecution);
        return lines(reportFile);
    }

    private TransactionReportWriter writer(Path reportFile) {
        return new TransactionReportWriter(reportFile, START, END,
                cardXrefs, transactionTypes, transactionCategories, new AbendService());
    }

    private static List<String> lines(Path reportFile) throws IOException {
        return Files.readAllLines(reportFile, StandardCharsets.UTF_8);
    }

    private static String pad(String value, int width) {
        return value + " ".repeat(width - value.length());
    }
}
