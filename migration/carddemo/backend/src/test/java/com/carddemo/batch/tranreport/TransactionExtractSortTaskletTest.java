package com.carddemo.batch.tranreport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.batch.test.MetaDataInstanceFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-14-004 and FR-14-005: the INCLUDE condition and the sort key of TRANREPT's
 * SORT step.
 */
class TransactionExtractSortTaskletTest {

    @TempDir
    Path work;

    @Test
    void keepsTheProcessingDatesInsideTheRangeAtBothEnds() throws Exception {
        List<String> selected = sort(List.of(
                record("0000000000000001", "4111111111111111", "2022-06-30"),
                record("0000000000000002", "4111111111111112", "2022-07-01"),
                record("0000000000000003", "4111111111111113", "2022-07-04"),
                record("0000000000000004", "4111111111111114", "2022-07-06"),
                record("0000000000000005", "4111111111111115", "2022-07-07")),
                "2022-07-01", "2022-07-06");

        assertThat(selected).extracting(line -> line.substring(0, 16))
                .containsExactly("0000000000000002", "0000000000000003", "0000000000000004");
    }

    @Test
    void sortsByCardNumberAscending() throws Exception {
        List<String> selected = sort(List.of(
                record("0000000000000001", "4999999999999999", "2022-07-02"),
                record("0000000000000002", "4111111111111111", "2022-07-02"),
                record("0000000000000003", "4555555555555555", "2022-07-02")),
                "2022-07-01", "2022-07-06");

        assertThat(selected).extracting(line -> line.substring(262, 278))
                .containsExactly("4111111111111111", "4555555555555555", "4999999999999999");
    }

    /** Records of the same card keep the order the unload wrote them in. */
    @Test
    void keepsTheUnloadOrderWithinACard() throws Exception {
        List<String> selected = sort(List.of(
                record("0000000000000002", "4111111111111111", "2022-07-02"),
                record("0000000000000001", "4111111111111111", "2022-07-02")),
                "2022-07-01", "2022-07-06");

        assertThat(selected).extracting(line -> line.substring(0, 16))
                .containsExactly("0000000000000002", "0000000000000001");
    }

    @Test
    void selectsTheReportedRecordsOutOfTheWholeFixture() throws Exception {
        List<String> selected = sort(TransactFixture.images(), "2022-07-01", "2022-07-06");

        assertThat(selected).hasSize(180);
        assertThat(selected).isSortedAccordingTo((left, right) ->
                left.substring(262, 278).compareTo(right.substring(262, 278)));
    }

    private List<String> sort(List<String> input, String startDate, String endDate) throws Exception {
        Path in = work.resolve("backup.txt");
        Path out = work.resolve("extract.txt");
        Files.write(in, input, StandardCharsets.UTF_8);

        RepeatStatus status = new TransactionExtractSortTasklet(in, out, startDate, endDate)
                .execute(new StepContribution(MetaDataInstanceFactory.createStepExecution()), null);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        return Files.readAllLines(out, StandardCharsets.UTF_8);
    }

    private static String record(String id, String cardNum, String procDate) {
        StringBuilder image = new StringBuilder(" ".repeat(TransactionRecordImage.LENGTH));
        image.replace(0, 16, id);
        image.replace(262, 278, cardNum);
        image.replace(304, 314, procDate);
        return image.toString();
    }
}
