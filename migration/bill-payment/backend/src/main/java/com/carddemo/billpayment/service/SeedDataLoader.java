package com.carddemo.billpayment.service;

import com.carddemo.billpayment.repository.AccountRepository;
import com.carddemo.billpayment.repository.CardXrefRepository;
import com.carddemo.billpayment.repository.TransactionRepository;
import com.carddemo.billpayment.util.CobolRecords;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Loads ACCTDAT, CCXREF and TRANSACT from the app/data/ASCII extracts (classpath:seed/). */
@Component
public class SeedDataLoader implements ApplicationRunner {

    private final AccountRepository accounts;
    private final CardXrefRepository xrefs;
    private final TransactionRepository transactions;

    public SeedDataLoader(AccountRepository accounts, CardXrefRepository xrefs,
                          TransactionRepository transactions) {
        this.accounts = accounts;
        this.xrefs = xrefs;
        this.transactions = transactions;
    }

    @Override
    public void run(ApplicationArguments args) {
        reload();
    }

    @Transactional
    public void reload() {
        transactions.deleteAllInBatch();
        xrefs.deleteAllInBatch();
        accounts.deleteAllInBatch();
        accounts.saveAll(read("acctdata.txt", 300, CobolRecords::parseAccount));
        xrefs.saveAll(read("cardxref.txt", 50, CobolRecords::parseXref));
        transactions.saveAll(read("dailytran.txt", 350, CobolRecords::parseTransaction));
    }

    private static <T> List<T> read(String file, int reclen, Function<String, T> parser) {
        try {
            String text = new String(new ClassPathResource("seed/" + file).getInputStream().readAllBytes(),
                    StandardCharsets.ISO_8859_1);
            return text.lines().filter(l -> !l.isBlank())
                    .map(l -> parser.apply(CobolRecords.pad(l, reclen))).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
