package com.carddemo.batch.exportimport;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.carddemo.common.batch.AbendException;
import com.carddemo.common.batch.AbendService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamReader;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The SYSOUT contract of the two jobs: the file-status and abend lines an
 * operator greps a failed run for (CBEXPORT.cbl:200-238, 263, 304, 578;
 * CBIMPORT.cbl:198-244, 264, 315-442, 483). Audit finding A-09.
 */
class ExportImportSysoutTest {

    private final AbendService abendService = new AbendService();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger root;

    @BeforeEach
    void captureTheJobLog() {
        root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        appender.start();
        root.addAppender(appender);
    }

    @AfterEach
    void releaseTheJobLog() {
        root.detachAppender(appender);
        appender.stop();
    }

    @Test
    void openAndReadFailuresAreNamedAfterTheFdTheProgramNamesThem() {
        assertThat(ExportImportSysout.cannotOpen("CUSTOMER-INPUT", ExportImportSysout.STATUS_OPEN_FAILED))
                .isEqualTo("ERROR: Cannot open CUSTOMER-INPUT, Status: 35");
        assertThat(ExportImportSysout.reading("CUSTOMER-INPUT", ExportImportSysout.STATUS_READ_FAILED))
                .isEqualTo("ERROR: Reading CUSTOMER-INPUT, Status: 30");
        assertThat(ExportImportSysout.cannotOpen("EXPORT-OUTPUT", ExportImportSysout.STATUS_OPEN_FAILED))
                .isEqualTo("ERROR: Cannot open EXPORT-OUTPUT, Status: 35");
    }

    @Test
    void everyImportOutputKeepsItsCobolFdNameAndWriteFailureText() {
        assertThat(ImportTarget.CUSTOMER.fdName()).isEqualTo("CUSTOMER-OUTPUT");
        assertThat(ImportTarget.ERROR.fdName()).isEqualTo("ERROR-OUTPUT");
        assertThat(ExportImportSysout.cannotOpen(ImportTarget.CUSTOMER.fdName(),
                ExportImportSysout.STATUS_OPEN_FAILED))
                .isEqualTo("ERROR: Cannot open CUSTOMER-OUTPUT, Status: 35");
        assertThat(ImportTarget.CUSTOMER.writeErrorMessage(ExportImportSysout.STATUS_WRITE_FAILED))
                .isEqualTo("ERROR: Writing customer record, Status: 34");
        assertThat(ImportTarget.ERROR.writeErrorMessage(ExportImportSysout.STATUS_WRITE_FAILED))
                .isEqualTo("ERROR: Writing error record, Status: 34");
        assertThat(ExportFileItemWriter.WRITE_ERROR_MESSAGE)
                .isEqualTo("ERROR: Writing export record, Status: 34");
    }

    @Test
    void anAbendDisplaysTheFailureThenTheAbendingProgramLine() {
        assertThatThrownBy(() -> ExportImportSysout.abend(abendService, "CBEXPORT", "disk full",
                "ERROR: Writing export record, Status: 34"))
                .isInstanceOf(AbendException.class)
                .extracting(e -> ((AbendException) e).getAbendData().abendMsg())
                .isEqualTo("ERROR: Writing export record, Status: 34");

        assertThat(loggedLines()).containsSubsequence(
                "ERROR: Writing export record, Status: 34",
                "CBEXPORT: ABENDING PROGRAM");
    }

    @Test
    void aSourceThatCannotBeOpenedNamesItsFileAndAbends() {
        ExportSourceItemReader reader = new ExportSourceItemReader(
                List.of(new ExportSourceItemReader.Group(ExportRecordCodec.TYPE_CUSTOMER, "CUSTOMER-INPUT",
                        failingReader())),
                new ExportStatistics(), abendService);

        assertThatThrownBy(() -> reader.open(new ExecutionContext()))
                .isInstanceOf(AbendException.class);

        assertThat(loggedLines()).containsSubsequence(
                "ERROR: Cannot open CUSTOMER-INPUT, Status: 35",
                "CBEXPORT: ABENDING PROGRAM");
    }

    @Test
    void aSourceThatCannotBeReadNamesItsFileAndAbends() {
        ExportSourceItemReader reader = new ExportSourceItemReader(
                List.of(new ExportSourceItemReader.Group(ExportRecordCodec.TYPE_TRANSACTION,
                        "TRANSACTION-INPUT", readFailingReader())),
                new ExportStatistics(), abendService);
        reader.open(new ExecutionContext());

        assertThatThrownBy(reader::read).isInstanceOf(AbendException.class);

        assertThat(loggedLines()).containsSubsequence(
                "CBEXPORT: Processing transaction records",
                "ERROR: Reading TRANSACTION-INPUT, Status: 30",
                "CBEXPORT: ABENDING PROGRAM");
    }

    private List<String> loggedLines() {
        return appender.list.stream()
                .filter(event -> event.getLevel().isGreaterOrEqual(Level.INFO))
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private static ItemStreamReader<Object> failingReader() {
        return new ItemStreamReader<>() {
            @Override
            public void open(ExecutionContext executionContext) {
                throw new IllegalStateException("CUSTFILE is unavailable");
            }

            @Override
            public Object read() {
                return null;
            }
        };
    }

    private static ItemStreamReader<Object> readFailingReader() {
        return new ItemStreamReader<>() {
            @Override
            public Object read() {
                throw new IllegalStateException("TRANSACT is unavailable");
            }
        };
    }
}
