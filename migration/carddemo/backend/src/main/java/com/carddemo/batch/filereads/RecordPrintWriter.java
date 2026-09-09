package com.carddemo.batch.filereads;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import java.nio.file.Path;
import java.util.function.Function;

/**
 * The print loop of CBACT02C, CBACT03C and CBCUS01C: start line, one record image per
 * record read, end line (FR-G5, FR-C2, FR-X2, FR-U2).
 *
 * <p>{@code copies} is how many times the program displayed each record — once for
 * CBACT02C, whose in-paragraph {@code DISPLAY} is commented out (`CBACT02C.cbl:96`),
 * twice for CBACT03C and CBCUS01C, which display in both the read paragraph and the
 * main loop. The end line is suppressed when the step failed, because the legacy
 * abend never reached the {@code DISPLAY 'END OF EXECUTION…'}.
 */
public class RecordPrintWriter<T> implements ItemWriter<T>, StepExecutionListener, AbendSink {

    private final String program;
    private final Path sysoutPath;
    private final Function<T, String> image;
    private final int copies;
    private final FileReadAbend abendSeam;

    private SysoutFile sysout;

    public RecordPrintWriter(String program, Path sysoutPath, Function<T, String> image, int copies,
                             FileReadAbend abendSeam) {
        this.program = program;
        this.sysoutPath = sysoutPath;
        this.image = image;
        this.copies = copies;
        this.abendSeam = abendSeam;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        sysout = SysoutFile.open(sysoutPath);
        sysout.display("START OF EXECUTION OF PROGRAM " + program);
    }

    @Override
    public void write(Chunk<? extends T> chunk) {
        for (T item : chunk) {
            String line = image.apply(item);
            for (int i = 0; i < copies; i++) {
                sysout.display(line);
            }
        }
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        if (sysout == null) {
            return null;
        }
        if (stepExecution.getStatus().isUnsuccessful()) {
            sysout.close();
            return null;
        }
        sysout.display("END OF EXECUTION OF PROGRAM " + program);
        sysout.close();
        return null;
    }

    @Override
    public RuntimeException abend(String message, String fileStatus) {
        return abendSeam.abend(sysout, program, message, fileStatus);
    }
}
