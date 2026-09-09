package com.carddemo.batch.intcalc;

import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.domain.TransactionRecord;
import com.carddemo.common.repository.AccountRepository;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamWriter;
import org.springframework.batch.item.file.FlatFileItemWriter;

import java.util.ArrayList;
import java.util.List;

/**
 * The two outputs of CBACT04C's inner loop: the sequential SYSTRAN generation
 * ({@code WRITE FD-TRANFILE-REC}, CBACT04C.cbl:500) and the rewritten account rows
 * ({@code REWRITE FD-ACCTFILE-REC}, CBACT04C.cbl:356).
 *
 * <p>Both happen inside the chunk transaction, so an abend leaves neither the file
 * position nor the account rows of the failing chunk committed.
 */
class InterestPostingWriter implements ItemStreamWriter<InterestPosting> {

    private final FlatFileItemWriter<String> systranWriter;
    private final AccountRepository accounts;

    InterestPostingWriter(FlatFileItemWriter<String> systranWriter, AccountRepository accounts) {
        this.systranWriter = systranWriter;
        this.accounts = accounts;
    }

    @Override
    public void write(Chunk<? extends InterestPosting> chunk) throws Exception {
        List<String> lines = new ArrayList<>();
        for (InterestPosting posting : chunk) {
            for (TransactionRecord tran : posting.transactions()) {
                lines.add(TransactionRecordLine.format(tran));
            }
        }
        systranWriter.write(new Chunk<>(lines));

        for (InterestPosting posting : chunk) {
            AccountRecord update = posting.accountUpdate();
            if (update != null) {
                accounts.save(update);
            }
        }
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        systranWriter.open(executionContext);
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        systranWriter.update(executionContext);
    }

    @Override
    public void close() throws ItemStreamException {
        systranWriter.close();
    }
}
