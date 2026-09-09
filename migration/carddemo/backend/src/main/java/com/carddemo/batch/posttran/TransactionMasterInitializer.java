package com.carddemo.batch.posttran;

import com.carddemo.common.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.stereotype.Component;

/**
 * {@code OPEN OUTPUT TRANSACT-FILE} (CBTRN02C.cbl:256).
 *
 * <p>CBTRN02C loads the transaction master rather than adding to it: TRANBKP
 * copies the cluster to {@code TRANSACT.BKUP(+1)} and re-defines it empty
 * (TRANBKP.jcl:68-113) and the program then opens it OUTPUT, so a POSTTRAN run
 * leaves TRANSACT holding exactly the transactions it posted.
 *
 * <p>The emptying happens once per job instance: the marker lives in the job
 * execution context, which Spring Batch copies into a restarted execution, so a
 * restart resumes instead of discarding the transactions the failed run
 * committed.
 */
@Component
public class TransactionMasterInitializer implements StepExecutionListener {

    static final String OPENED_KEY = "posttran.tranfile.opened-output";

    private static final Logger log = LoggerFactory.getLogger(TransactionMasterInitializer.class);

    private final TransactionRepository transactionRepository;

    public TransactionMasterInitializer(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        var jobContext = stepExecution.getJobExecution().getExecutionContext();
        if (jobContext.containsKey(OPENED_KEY)) {
            return;
        }
        long emptied = transactionRepository.count();
        transactionRepository.deleteAllInBatch();
        jobContext.putString(OPENED_KEY, "Y");
        log.info("TRANFILE opened OUTPUT: {} transaction records cleared before posting", emptied);
    }
}
