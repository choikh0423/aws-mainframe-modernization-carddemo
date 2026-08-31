package com.carddemo.batch.config;

import com.carddemo.batch.model.Account;
import com.carddemo.batch.model.Card;
import com.carddemo.batch.model.CardXref;
import com.carddemo.batch.model.Customer;
import com.carddemo.batch.model.Transaction;
import com.carddemo.batch.repository.AccountRepository;
import com.carddemo.batch.repository.CardRepository;
import com.carddemo.batch.repository.CardXrefRepository;
import com.carddemo.batch.repository.CustomerRepository;
import com.carddemo.batch.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;

/**
 * Spring Batch job configuration for CBIMPORT.
 * Migrated from CBIMPORT.cbl and CBIMPORT.jcl.
 *
 * Reads multi-record export file, splits by record type,
 * validates data integrity, and writes to appropriate tables.
 * Errors are written to an error output file.
 */
@Configuration
public class ImportJobConfig {

    private static final Logger log = LoggerFactory.getLogger(ImportJobConfig.class);

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final CardXrefRepository cardXrefRepository;
    private final TransactionRepository transactionRepository;
    private final CardRepository cardRepository;

    public ImportJobConfig(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            CustomerRepository customerRepository,
            AccountRepository accountRepository,
            CardXrefRepository cardXrefRepository,
            TransactionRepository transactionRepository,
            CardRepository cardRepository) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.cardXrefRepository = cardXrefRepository;
        this.transactionRepository = transactionRepository;
        this.cardRepository = cardRepository;
    }

    @Bean
    public Job importJob() {
        return new JobBuilder("importJob", jobRepository)
                .start(importStep(null, null))
                .build();
    }

    @Bean
    @StepScope
    public Step importStep(
            @Value("#{jobParameters['importFile'] ?: '${carddemo.files.import-file:input/import.dat}'}") String importFile,
            @Value("#{jobParameters['errorFile'] ?: 'output/import-errors.dat'}") String errorFile) {
        return new StepBuilder("importStep", jobRepository)
                .tasklet(importTasklet(importFile, errorFile), transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet importTasklet(
            @Value("#{jobParameters['importFile'] ?: '${carddemo.files.import-file:input/import.dat}'}") String importFile,
            @Value("#{jobParameters['errorFile'] ?: 'output/import-errors.dat'}") String errorFile) {
        return (StepContribution contribution, ChunkContext chunkContext) -> {
            log.info("CBIMPORT: Starting Customer Data Import from {}", importFile);

            int totalRead = 0, customerCount = 0, accountCount = 0;
            int xrefCount = 0, tranCount = 0, cardCount = 0, errorCount = 0, unknownCount = 0;

            try (BufferedReader reader = new BufferedReader(new FileReader(importFile));
                 PrintWriter errorWriter = new PrintWriter(new BufferedWriter(new FileWriter(errorFile)))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) continue;
                    totalRead++;
                    char recType = line.charAt(0);

                    try {
                        switch (recType) {
                            case 'C' -> {
                                processCustomerRecord(line);
                                customerCount++;
                            }
                            case 'A' -> {
                                processAccountRecord(line);
                                accountCount++;
                            }
                            case 'X' -> {
                                processXrefRecord(line);
                                xrefCount++;
                            }
                            case 'T' -> {
                                processTransactionRecord(line);
                                tranCount++;
                            }
                            case 'D' -> {
                                processCardRecord(line);
                                cardCount++;
                            }
                            default -> {
                                unknownCount++;
                                errorWriter.printf("UNKNOWN|%c|%07d|Unknown record type%n",
                                        recType, totalRead);
                                errorCount++;
                            }
                        }
                    } catch (Exception e) {
                        errorWriter.printf("ERROR|%c|%07d|%s%n", recType, totalRead, e.getMessage());
                        errorCount++;
                        log.warn("Error processing record {}: {}", totalRead, e.getMessage());
                    }
                }
            }

            log.info("CBIMPORT: Total records read:       {}", totalRead);
            log.info("CBIMPORT: Customers imported:       {}", customerCount);
            log.info("CBIMPORT: Accounts imported:        {}", accountCount);
            log.info("CBIMPORT: Cross-references imported: {}", xrefCount);
            log.info("CBIMPORT: Transactions imported:     {}", tranCount);
            log.info("CBIMPORT: Cards imported:            {}", cardCount);
            log.info("CBIMPORT: Unknown record types:      {}", unknownCount);
            log.info("CBIMPORT: Error records:             {}", errorCount);

            return RepeatStatus.FINISHED;
        };
    }

    private void processCustomerRecord(String line) {
        Customer cust = new Customer();
        // Skip record type (1), timestamp (26), sequence (9), branch (5), region (6)
        // Simplified parsing: extract key fields from the export format
        int pos = 47; // after common header fields
        cust.setCustId(parseLong(line, pos, pos + 9));
        pos += 9;
        cust.setFirstName(parseString(line, pos, pos + 25));
        pos += 25;
        cust.setMiddleName(parseString(line, pos, pos + 25));
        pos += 25;
        cust.setLastName(parseString(line, pos, pos + 25));
        pos += 25;
        cust.setAddrLine1(parseString(line, pos, pos + 50));
        pos += 50;
        cust.setAddrLine2(parseString(line, pos, pos + 50));
        pos += 50;
        cust.setAddrLine3(parseString(line, pos, pos + 50));
        pos += 50;
        cust.setStateCd(parseString(line, pos, pos + 2));
        pos += 2;
        cust.setCountryCd(parseString(line, pos, pos + 3));
        pos += 3;
        cust.setZip(parseString(line, pos, pos + 10));
        customerRepository.save(cust);
    }

    private void processAccountRecord(String line) {
        Account acct = new Account();
        int pos = 47;
        acct.setAcctId(parseLong(line, pos, pos + 11));
        pos += 11;
        acct.setActiveStatus(parseString(line, pos, pos + 1));
        pos += 1;
        acct.setCurrentBalance(parseDecimal(line, pos, pos + 13));
        pos += 13;
        acct.setCreditLimit(parseDecimal(line, pos, pos + 13));
        pos += 13;
        acct.setCashCreditLimit(parseDecimal(line, pos, pos + 13));
        accountRepository.save(acct);
    }

    private void processXrefRecord(String line) {
        CardXref xref = new CardXref();
        int pos = 47;
        xref.setCardNum(parseString(line, pos, pos + 16));
        pos += 16;
        xref.setCustId(parseLong(line, pos, pos + 9));
        pos += 9;
        xref.setAcctId(parseLong(line, pos, pos + 11));
        cardXrefRepository.save(xref);
    }

    private void processTransactionRecord(String line) {
        Transaction tran = new Transaction();
        int pos = 47;
        tran.setTranId(parseString(line, pos, pos + 16));
        pos += 16;
        tran.setTranTypeCd(parseString(line, pos, pos + 2));
        pos += 2;
        tran.setTranCatCd(parseInt(line, pos, pos + 4));
        pos += 4;
        tran.setTranSource(parseString(line, pos, pos + 10));
        pos += 10;
        tran.setTranDesc(parseString(line, pos, pos + 100));
        pos += 100;
        tran.setTranAmt(parseDecimal(line, pos, pos + 12));
        pos += 12;
        tran.setMerchantId(parseLong(line, pos, pos + 9));
        pos += 9;
        tran.setMerchantName(parseString(line, pos, pos + 50));
        pos += 50;
        tran.setMerchantCity(parseString(line, pos, pos + 50));
        pos += 50;
        tran.setMerchantZip(parseString(line, pos, pos + 10));
        pos += 10;
        tran.setCardNum(parseString(line, pos, pos + 16));
        pos += 16;
        tran.setOrigTimestamp(parseString(line, pos, pos + 26));
        pos += 26;
        tran.setProcTimestamp(parseString(line, pos, pos + 26));
        transactionRepository.save(tran);
    }

    private void processCardRecord(String line) {
        Card card = new Card();
        int pos = 47;
        card.setCardNum(parseString(line, pos, pos + 16));
        pos += 16;
        card.setCardAcctId(parseLong(line, pos, pos + 11));
        pos += 11;
        card.setCardCvvCd(parseInt(line, pos, pos + 3));
        pos += 3;
        card.setCardEmbossedName(parseString(line, pos, pos + 50));
        pos += 50;
        card.setCardExpirationDate(parseString(line, pos, pos + 10));
        pos += 10;
        card.setCardActiveStatus(parseString(line, pos, pos + 1));
        cardRepository.save(card);
    }

    private static String parseString(String line, int start, int end) {
        if (line.length() < end) {
            end = line.length();
        }
        if (start >= end) return "";
        return line.substring(start, end).trim();
    }

    private static Long parseLong(String line, int start, int end) {
        String s = parseString(line, start, end);
        if (s.isEmpty()) return 0L;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static Integer parseInt(String line, int start, int end) {
        String s = parseString(line, start, end);
        if (s.isEmpty()) return 0;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static BigDecimal parseDecimal(String line, int start, int end) {
        String s = parseString(line, start, end);
        if (s.isEmpty()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
