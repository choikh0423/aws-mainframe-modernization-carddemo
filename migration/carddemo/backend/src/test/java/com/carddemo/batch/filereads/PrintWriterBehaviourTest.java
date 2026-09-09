package com.carddemo.batch.filereads;

import com.carddemo.common.batch.AbendException;
import com.carddemo.common.batch.AbendService;
import com.carddemo.common.domain.AccountRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.Chunk;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FR-G4, FR-G5, FR-A12: what the print writers put on SYSOUT when the file is empty,
 * when the step abends, and when an output record cannot be written.
 */
class PrintWriterBehaviourTest {

    @TempDir
    Path outputDir;

    private final FileReadAbend abendSeam = new FileReadAbend(new AbendService());

    @Test
    void printsOnlyTheExecutionMessagesForAnEmptyFile() throws IOException {
        Path sysoutPath = outputDir.resolve("READCARD.SYSOUT.txt");
        RecordPrintWriter<String> writer = new RecordPrintWriter<>(
                "CBACT02C", sysoutPath, image -> image, 1, abendSeam);
        StepExecution stepExecution = stepExecution();

        writer.beforeStep(stepExecution);
        writer.write(Chunk.of());
        stepExecution.setStatus(BatchStatus.COMPLETED);
        writer.afterStep(stepExecution);

        assertThat(Files.readAllLines(sysoutPath)).containsExactly(
                "START OF EXECUTION OF PROGRAM CBACT02C",
                "END OF EXECUTION OF PROGRAM CBACT02C");
    }

    @Test
    void omitsTheEndMessageWhenTheStepAbended() throws IOException {
        Path sysoutPath = outputDir.resolve("READXREF.SYSOUT.txt");
        RecordPrintWriter<String> writer = new RecordPrintWriter<>(
                "CBACT03C", sysoutPath, image -> image, 2, abendSeam);
        StepExecution stepExecution = stepExecution();

        writer.beforeStep(stepExecution);
        writer.write(Chunk.of("A RECORD"));
        stepExecution.setStatus(BatchStatus.FAILED);
        writer.afterStep(stepExecution);

        assertThat(Files.readAllLines(sysoutPath)).containsExactly(
                "START OF EXECUTION OF PROGRAM CBACT03C",
                "A RECORD",
                "A RECORD");
    }

    @Test
    void abendsWhenAnAccountOutputRecordCannotBeWritten() throws IOException {
        Path sysoutPath = outputDir.resolve("READACCT.SYSOUT.txt");
        AccountPrintWriter writer = new AccountPrintWriter(sysoutPath, new FailingExtractWriter(), abendSeam);
        StepExecution stepExecution = stepExecution();
        writer.beforeStep(stepExecution);

        assertThatThrownBy(() -> writer.write(Chunk.of(account())))
                .isInstanceOf(AbendException.class);
        stepExecution.setStatus(BatchStatus.FAILED);
        writer.afterStep(stepExecution);

        assertThat(Files.readAllLines(sysoutPath))
                .contains("ACCOUNT FILE WRITE STATUS IS:30",
                        "FILE STATUS IS: NNNN0030",
                        "ABENDING PROGRAM")
                .doesNotContain("END OF EXECUTION OF PROGRAM CBACT01C");
    }

    private StepExecution stepExecution() {
        return new StepExecution("STEP05", new JobExecution(1L));
    }

    private AccountRecord account() {
        AccountRecord account = new AccountRecord();
        account.setAcctId(1L);
        account.setActiveStatus("Y");
        account.setCurrBal(new BigDecimal("194.00"));
        account.setCreditLimit(new BigDecimal("2020.00"));
        account.setCashCreditLimit(new BigDecimal("1020.00"));
        account.setOpenDate("2014-11-20");
        account.setExpiraionDate("2025-05-20");
        account.setReissueDate("2025-05-20");
        account.setCurrCycCredit(BigDecimal.ZERO);
        account.setCurrCycDebit(BigDecimal.ZERO);
        account.setAddrZip("A000000000");
        account.setGroupId("");
        return account;
    }

    /** An output dataset whose writes always fail, as a full volume would. */
    private final class FailingExtractWriter extends AccountExtractWriter {
        private FailingExtractWriter() {
            super(new FileReadOutputs(outputDir.toString()));
        }

        @Override
        public void writeAccountRecord(byte[] record) throws IOException {
            throw new IOException("volume full");
        }
    }
}
