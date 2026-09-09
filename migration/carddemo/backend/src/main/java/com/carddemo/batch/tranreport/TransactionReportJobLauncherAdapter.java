package com.carddemo.batch.tranreport;

import com.carddemo.reporting.port.TransactionReportJobLauncher;
import com.carddemo.reporting.port.TransactionReportJobRequest;
import org.springframework.stereotype.Component;

/**
 * Binds the S-07 seam to this stream's job: CR00's replacement submits a report
 * through {@link TransactionReportJobLauncher}, and the request it hands over -
 * report name plus the inclusive date range CORPT00C put in
 * {@code PARM-START-DATE} / {@code PARM-END-DATE} (app/cbl/CORPT00C.cbl:104-121)
 * - is exactly what TRANREPT takes as job parameters.
 *
 * <p>Registering it displaces the recording no-op S-07 bound while this stream
 * was outstanding - that bean is {@code @ConditionalOnMissingBean} - so no code
 * in the S-07 package changes at the handover.
 *
 * <p>The legacy screen only ever learned that the deck reached the internal
 * reader, so a submission failure surfaces the same way it did there: as a
 * runtime failure CR00 turns into {@code Unable to Write TDQ (JOBS)...}.
 */
@Component
public class TransactionReportJobLauncherAdapter implements TransactionReportJobLauncher {

    private final TransactionReportLauncher launcher;

    public TransactionReportJobLauncherAdapter(TransactionReportLauncher launcher) {
        this.launcher = launcher;
    }

    @Override
    public void launch(TransactionReportJobRequest request) {
        try {
            launcher.submit(request.getReportName(),
                    request.getStartDateText(),
                    request.getEndDateText());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("TRANREPT could not be submitted for " + request, e);
        }
    }
}
