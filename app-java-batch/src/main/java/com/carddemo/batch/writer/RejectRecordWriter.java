package com.carddemo.batch.writer;

import com.carddemo.batch.model.RejectRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Writes rejected transactions to a flat file.
 * Ported from CBTRN02C.cbl paragraph 2500-WRITE-REJECT-REC (lines 446-465).
 * Output matches the JCL DALYREJS file: RECFM=F, LRECL=430.
 */
@Component
public class RejectRecordWriter {

    private static final Logger log = LoggerFactory.getLogger(RejectRecordWriter.class);

    @Value("${carddemo.files.daily-rejects:output/dailyrejects.dat}")
    private String rejectFilePath;

    private final AtomicInteger rejectCount = new AtomicInteger(0);

    public void writeReject(RejectRecord reject) {
        try (PrintWriter writer = new PrintWriter(
                new BufferedWriter(new FileWriter(rejectFilePath, true)))) {
            writer.println(reject.toFixedLengthString());
            rejectCount.incrementAndGet();
            log.debug("Wrote reject record, reason: {} - {}", reject.getReasonCode(),
                    reject.getReasonDescription());
        } catch (IOException e) {
            log.error("Error writing reject record to {}: {}", rejectFilePath, e.getMessage());
        }
    }

    public int getRejectCount() {
        return rejectCount.get();
    }

    public void resetCount() {
        rejectCount.set(0);
    }
}
