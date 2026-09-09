package com.carddemo.reporting.adapter;

import com.carddemo.reporting.port.TransactionReportJobLauncher;
import com.carddemo.reporting.port.TransactionReportJobRequest;
import com.carddemo.reporting.service.TranReportJclDeck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The standing binding of the S-07 → S-14 seam while stream S-14
 * TransactionReporting has not yet delivered its {@code TRANREPT} Spring Batch
 * job in {@code com.carddemo.batch.tranreport}.
 *
 * <p>It performs no reporting work. It records the request and logs the deck
 * CORPT00C would have written to the internal reader, then returns normally, so
 * CR00 shows the same green {@code ... report submitted for printing ...} line
 * it will show once the real job is wired in (FR-R38). The moment S-14
 * contributes a {@link TransactionReportJobLauncher} bean, this one drops out:
 * the {@code @Bean} is {@link ConditionalOnMissingBean}, so no code in this
 * package changes at the handover.
 *
 * <p>The recorded requests are the audit trail an operator would otherwise read
 * off the JOBS queue, and what the integration tests assert against.
 */
@Configuration
public class NoOpTransactionReportJobLauncher {

    @Bean
    @ConditionalOnMissingBean(TransactionReportJobLauncher.class)
    public TransactionReportJobLauncher recordingTransactionReportJobLauncher() {
        return new RecordingLauncher();
    }

    /** The no-op implementation; package-visible for its test. */
    static final class RecordingLauncher implements TransactionReportJobLauncher {

        private static final Logger log = LoggerFactory.getLogger(RecordingLauncher.class);

        private final List<TransactionReportJobRequest> submitted =
                Collections.synchronizedList(new ArrayList<>());

        @Override
        public void launch(TransactionReportJobRequest request) {
            submitted.add(request);
            log.info("TRANREPT not yet migrated (stream S-14); CR00 requested {}. "
                    + "Internal-reader deck that would have been submitted:\n{}",
                    request, String.join("\n", TranReportJclDeck.build(request)));
        }

        List<TransactionReportJobRequest> getSubmitted() {
            return List.copyOf(submitted);
        }
    }
}
