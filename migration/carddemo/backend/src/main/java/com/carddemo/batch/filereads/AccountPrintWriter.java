package com.carddemo.batch.filereads;

import com.carddemo.common.domain.AccountRecord;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * CBACT01C: prints each account and derives the three sequential output datasets
 * (FR-A3 to FR-A12).
 *
 * <p>Per account it reproduces the paragraph order of {@code 1000-ACCTFILE-GET-NEXT}
 * (`CBACT01C.cbl:165-178`): the labelled field block, the OUTFILE record, the ARRYFILE
 * record, the two {@code VBRC-REC} lines and their records, and finally the raw record
 * image the main loop displayed.
 */
public class AccountPrintWriter implements ItemWriter<AccountRecord>, StepExecutionListener, AbendSink {

    static final String PROGRAM = "CBACT01C";

    /** {@code DISPLAY 'ACCOUNT FILE WRITE STATUS IS:'} — used for all three outputs. */
    static final String WRITE_ERROR = "ACCOUNT FILE WRITE STATUS IS:";

    static final String OPEN_ACCTFILE_ERROR = "ERROR OPENING ACCTFILE";
    static final String READ_ERROR = "ERROR READING ACCOUNT FILE";
    static final String CLOSE_ERROR = "ERROR CLOSING ACCOUNT FILE";
    static final String OPEN_OUTFILE_ERROR = "ERROR OPENING OUTFILE";
    static final String OPEN_ARRAYFILE_ERROR = "ERROR OPENING ARRAYFILE";
    static final String OPEN_VBRCFILE_ERROR = "ERROR OPENING VBRC FILE";

    private static final List<String> LABELS = List.of(
            "ACCT-ID                 :",
            "ACCT-ACTIVE-STATUS      :",
            "ACCT-CURR-BAL           :",
            "ACCT-CREDIT-LIMIT       :",
            "ACCT-CASH-CREDIT-LIMIT  :",
            "ACCT-OPEN-DATE          :",
            "ACCT-EXPIRAION-DATE     :",
            "ACCT-REISSUE-DATE       :",
            "ACCT-CURR-CYC-CREDIT    :",
            "ACCT-CURR-CYC-DEBIT     :",
            "ACCT-GROUP-ID           :");

    private static final String SEPARATOR = "-".repeat(49);

    private final Path sysoutPath;
    private final AccountExtractWriter extracts;
    private final FileReadAbend abendSeam;

    private SysoutFile sysout;
    private AccountExtractBuilder builder;

    public AccountPrintWriter(Path sysoutPath, AccountExtractWriter extracts, FileReadAbend abendSeam) {
        this.sysoutPath = sysoutPath;
        this.extracts = extracts;
        this.abendSeam = abendSeam;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        sysout = SysoutFile.open(sysoutPath);
        builder = new AccountExtractBuilder();
        sysout.display("START OF EXECUTION OF PROGRAM " + PROGRAM);
        openExtract(extracts::openAccountFile, OPEN_OUTFILE_ERROR);
        openExtract(extracts::openArrayFile, OPEN_ARRAYFILE_ERROR);
        openExtract(extracts::openVariableFile, OPEN_VBRCFILE_ERROR);
    }

    @Override
    public void write(Chunk<? extends AccountRecord> chunk) {
        for (AccountRecord account : chunk) {
            displayFields(account);
            AccountExtract extract = builder.build(account);
            write(() -> extracts.writeAccountRecord(extract.outRecord()));
            write(() -> extracts.writeArrayRecord(extract.arrayRecord()));
            sysout.display("VBRC-REC1:" + extract.vbrcRec1());
            sysout.display("VBRC-REC2:" + extract.vbrcRec2());
            write(() -> extracts.writeVariableRecord(extract.vbrcRec1()));
            write(() -> extracts.writeVariableRecord(extract.vbrcRec2()));
            sysout.display(RecordImages.account(account));
        }
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        if (sysout == null) {
            return null;
        }
        try {
            extracts.close();
        } catch (IOException e) {
            // The program never closes its output files; a failure here cannot be
            // reported the way the COBOL reported one, so it only fails the step.
            throw new IllegalStateException("Cannot close the CBACT01C output datasets", e);
        } finally {
            if (!stepExecution.getStatus().isUnsuccessful()) {
                sysout.display("END OF EXECUTION OF PROGRAM " + PROGRAM);
            }
            sysout.close();
        }
        return null;
    }

    @Override
    public RuntimeException abend(String message, String fileStatus) {
        return abendSeam.abend(sysout, PROGRAM, message, fileStatus);
    }

    /** 1100-DISPLAY-ACCT-RECORD. */
    private void displayFields(AccountRecord account) {
        String image = RecordImages.account(account);
        List<String> values = List.of(
                image.substring(0, 11),
                image.substring(11, 12),
                image.substring(12, 24),
                image.substring(24, 36),
                image.substring(36, 48),
                image.substring(48, 58),
                image.substring(58, 68),
                image.substring(68, 78),
                image.substring(78, 90),
                image.substring(90, 102),
                image.substring(112, 122));
        for (int i = 0; i < LABELS.size(); i++) {
            sysout.display(LABELS.get(i) + values.get(i));
        }
        sysout.display(SEPARATOR);
    }

    private void openExtract(IoAction action, String message) {
        try {
            action.run();
        } catch (IOException e) {
            throw abend(message + FileReadAbend.PERMANENT_ERROR, FileReadAbend.PERMANENT_ERROR);
        }
    }

    private void write(IoAction action) {
        try {
            action.run();
        } catch (IOException e) {
            throw abend(WRITE_ERROR + FileReadAbend.PERMANENT_ERROR, FileReadAbend.PERMANENT_ERROR);
        }
    }

    @FunctionalInterface
    private interface IoAction {
        void run() throws IOException;
    }
}
