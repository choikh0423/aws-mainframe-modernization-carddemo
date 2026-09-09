package com.carddemo.batch.filereads;

import com.carddemo.common.batch.AbendException;
import com.carddemo.common.batch.AbendService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FR-G6, FR-G7, FR-G8: the abend sequence of the S-15 programs and the OPEN / READ /
 * CLOSE error paths that trigger it.
 */
class FileReadAbendTest {

    @TempDir
    Path outputDir;

    private final FileReadAbend abendSeam = new FileReadAbend(new AbendService());

    @Test
    void printsTheThreeAbendLinesAndAbendsWithCode0999() throws IOException {
        Path sysoutPath = outputDir.resolve("READCARD.SYSOUT.txt");
        SysoutFile sysout = SysoutFile.open(sysoutPath);

        assertThatThrownBy(() -> {
            throw abendSeam.abend(sysout, "CBACT02C", "ERROR OPENING CARDFILE", "35");
        })
                .isInstanceOf(AbendException.class)
                .hasMessageContaining("0999")
                .hasMessageContaining("CBACT02C");
        sysout.close();

        assertThat(Files.readAllLines(sysoutPath)).containsExactly(
                "ERROR OPENING CARDFILE",
                "FILE STATUS IS: NNNN0035",
                "ABENDING PROGRAM");
    }

    @Test
    void abendsWithTheProgramsOpenLiteralWhenTheBrowseCannotBeOpened() {
        RecordingSink sink = new RecordingSink();
        AbendingItemReader<String> reader = new AbendingItemReader<>(
                new FailingReader<>(true, false), sink,
                "ERROR OPENING CARDFILE", "ERROR READING CARDFILE", "ERROR CLOSING CARDFILE");

        assertThatThrownBy(() -> reader.open(new ExecutionContext()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(sink.messages).containsExactly("ERROR OPENING CARDFILE");
        assertThat(sink.statuses).containsExactly(FileReadAbend.PERMANENT_ERROR);
    }

    @Test
    void abendsWithTheProgramsReadLiteralWhenTheBrowseFails() {
        RecordingSink sink = new RecordingSink();
        AbendingItemReader<String> reader = new AbendingItemReader<>(
                new FailingReader<>(false, true), sink,
                "ERROR OPENING CARDFILE", "ERROR READING CARDFILE", "ERROR CLOSING CARDFILE");

        assertThatThrownBy(reader::read).isInstanceOf(IllegalStateException.class);
        assertThat(sink.messages).containsExactly("ERROR READING CARDFILE");
    }

    @Test
    void abendsWithTheProgramsCloseLiteralWhenTheBrowseCannotBeClosed() {
        RecordingSink sink = new RecordingSink();
        AbendingItemReader<String> reader = new AbendingItemReader<>(
                new FailingReader<>(false, false), sink,
                "ERROR OPENING CARDFILE", "ERROR READING CARDFILE", "ERROR CLOSING CARDFILE");

        assertThatThrownBy(reader::close).isInstanceOf(IllegalStateException.class);
        assertThat(sink.messages).containsExactly("ERROR CLOSING CARDFILE");
    }

    /** An abend raised inside the delegate is not re-reported by the wrapper. */
    @Test
    void doesNotWrapAnAbendRaisedFurtherDown() {
        RecordingSink sink = new RecordingSink();
        AbendingItemReader<String> reader = new AbendingItemReader<>(new AbendingDelegate<>(), sink,
                "ERROR OPENING CARDFILE", "ERROR READING CARDFILE", "ERROR CLOSING CARDFILE");

        assertThatThrownBy(reader::read).isInstanceOf(AbendException.class);
        assertThat(sink.messages).isEmpty();
    }

    private static final class RecordingSink implements AbendSink {
        private final List<String> messages = new java.util.ArrayList<>();
        private final List<String> statuses = new java.util.ArrayList<>();

        @Override
        public RuntimeException abend(String message, String fileStatus) {
            messages.add(message);
            statuses.add(fileStatus);
            return new IllegalStateException(message);
        }
    }

    private static final class FailingReader<T> implements ItemStreamReader<T> {
        private final boolean failOnOpen;
        private final boolean failOnRead;

        private FailingReader(boolean failOnOpen, boolean failOnRead) {
            this.failOnOpen = failOnOpen;
            this.failOnRead = failOnRead;
        }

        @Override
        public T read() {
            if (failOnRead) {
                throw new RuntimeException("query failed");
            }
            return null;
        }

        @Override
        public void open(ExecutionContext executionContext) {
            if (failOnOpen) {
                throw new RuntimeException("cannot open");
            }
        }

        @Override
        public void close() {
            if (!failOnOpen && !failOnRead) {
                throw new RuntimeException("cannot close");
            }
        }
    }

    private static final class AbendingDelegate<T> implements ItemStreamReader<T> {
        @Override
        public T read() {
            throw new AbendService().abend("0999", "CBACT02C", "reason", "msg");
        }
    }
}
