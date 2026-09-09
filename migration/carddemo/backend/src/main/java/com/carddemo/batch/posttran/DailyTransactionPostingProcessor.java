package com.carddemo.batch.posttran;

import com.carddemo.common.domain.DailyTransactionRecord;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * The body of CBTRN02C's main loop (CBTRN02C.cbl:206-216) for one DALYTRAN
 * record: validate, then either post it or turn it into a reject line.
 *
 * <p>A posted record returns {@code null} so Spring Batch filters it out of the
 * chunk; only rejects reach the DALYREJS writer, which makes the step's write
 * count {@code WS-REJECT-COUNT} and its read count
 * {@code WS-TRANSACTION-COUNT}.
 */
@Component
public class DailyTransactionPostingProcessor implements ItemProcessor<DailyTransactionRecord, String> {

    private final TransactionValidationService validationService;
    private final TransactionPostingService postingService;

    public DailyTransactionPostingProcessor(TransactionValidationService validationService,
                                            TransactionPostingService postingService) {
        this.validationService = validationService;
        this.postingService = postingService;
    }

    @Override
    public String process(DailyTransactionRecord daily) {
        ValidationResult validation = validationService.validate(daily);
        if (validation.isRejected()) {
            return RejectRecord.render(daily, validation);
        }
        postingService.post(daily, validation);
        return null;
    }
}
