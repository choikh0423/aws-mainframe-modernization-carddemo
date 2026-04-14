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

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Spring Batch job configuration for CBEXPORT.
 * Migrated from CBEXPORT.cbl and CBEXPORT.jcl.
 *
 * Reads Customer, Account, CardXref, Transaction, Card tables
 * and writes a multi-record export file with different record types.
 * Each record is 500 characters with a record-type indicator.
 */
@Configuration
public class ExportJobConfig {

    private static final Logger log = LoggerFactory.getLogger(ExportJobConfig.class);

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final CardXrefRepository cardXrefRepository;
    private final TransactionRepository transactionRepository;
    private final CardRepository cardRepository;

    public ExportJobConfig(
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
    public Job exportJob() {
        return new JobBuilder("exportJob", jobRepository)
                .start(exportStep(null))
                .build();
    }

    @Bean
    @StepScope
    public Step exportStep(
            @Value("#{jobParameters['exportFile'] ?: '${carddemo.files.export-file:output/export.dat}'}") String exportFile) {
        return new StepBuilder("exportStep", jobRepository)
                .tasklet(exportTasklet(exportFile), transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public Tasklet exportTasklet(
            @Value("#{jobParameters['exportFile'] ?: '${carddemo.files.export-file:output/export.dat}'}") String exportFile) {
        return (StepContribution contribution, ChunkContext chunkContext) -> {
            log.info("CBEXPORT: Starting Customer Data Export");
            String timestamp = LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SS"));
            int sequenceCounter = 0;

            int customerCount = 0, accountCount = 0, xrefCount = 0, tranCount = 0, cardCount = 0;

            try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(exportFile)))) {

                // Export customers (record type 'C')
                List<Customer> customers = customerRepository.findAll();
                for (Customer cust : customers) {
                    sequenceCounter++;
                    writer.printf("C%-26s%09d0001 NORTH %09d%-25s%-25s%-25s%-50s%-50s%-50s%-2s%-3s%-10s%-15s%-15s%09d%-20s%-10s%-10s%-1s%03d%n",
                            timestamp, sequenceCounter,
                            cust.getCustId() != null ? cust.getCustId() : 0L,
                            safe(cust.getFirstName(), 25),
                            safe(cust.getMiddleName(), 25),
                            safe(cust.getLastName(), 25),
                            safe(cust.getAddrLine1(), 50),
                            safe(cust.getAddrLine2(), 50),
                            safe(cust.getAddrLine3(), 50),
                            safe(cust.getStateCd(), 2),
                            safe(cust.getCountryCd(), 3),
                            safe(cust.getZip(), 10),
                            safe(cust.getPhone1(), 15),
                            safe(cust.getPhone2(), 15),
                            cust.getSsn() != null ? cust.getSsn() : 0L,
                            safe(cust.getGovtIssuedId(), 20),
                            safe(cust.getDob(), 10),
                            safe(cust.getEftAccountId(), 10),
                            safe(cust.getPrimaryCardHolderInd(), 1),
                            cust.getFicoCreditScore() != null ? cust.getFicoCreditScore() : 0);
                    customerCount++;
                }

                // Export accounts (record type 'A')
                List<Account> accounts = accountRepository.findAll();
                for (Account acct : accounts) {
                    sequenceCounter++;
                    writer.printf("A%-26s%09d0001 NORTH %011d%-1s%+013.2f%+013.2f%+013.2f%-10s%-10s%-10s%+013.2f%+013.2f%-10s%-10s%n",
                            timestamp, sequenceCounter,
                            acct.getAcctId() != null ? acct.getAcctId() : 0L,
                            safe(acct.getActiveStatus(), 1),
                            acct.getCurrentBalance() != null ? acct.getCurrentBalance().doubleValue() : 0.0,
                            acct.getCreditLimit() != null ? acct.getCreditLimit().doubleValue() : 0.0,
                            acct.getCashCreditLimit() != null ? acct.getCashCreditLimit().doubleValue() : 0.0,
                            safe(acct.getOpenDate(), 10),
                            safe(acct.getExpirationDate(), 10),
                            safe(acct.getReissueDate(), 10),
                            acct.getCurrentCycleCredit() != null ? acct.getCurrentCycleCredit().doubleValue() : 0.0,
                            acct.getCurrentCycleDebit() != null ? acct.getCurrentCycleDebit().doubleValue() : 0.0,
                            safe(acct.getAddressZip(), 10),
                            safe(acct.getGroupId(), 10));
                    accountCount++;
                }

                // Export cross-references (record type 'X')
                List<CardXref> xrefs = cardXrefRepository.findAll();
                for (CardXref xref : xrefs) {
                    sequenceCounter++;
                    writer.printf("X%-26s%09d0001 NORTH %-16s%09d%011d%n",
                            timestamp, sequenceCounter,
                            safe(xref.getCardNum(), 16),
                            xref.getCustId() != null ? xref.getCustId() : 0L,
                            xref.getAcctId() != null ? xref.getAcctId() : 0L);
                    xrefCount++;
                }

                // Export transactions (record type 'T')
                List<Transaction> transactions = transactionRepository.findAll();
                for (Transaction tran : transactions) {
                    sequenceCounter++;
                    writer.printf("T%-26s%09d0001 NORTH %-16s%-2s%04d%-10s%-100s%+012.2f%09d%-50s%-50s%-10s%-16s%-26s%-26s%n",
                            timestamp, sequenceCounter,
                            safe(tran.getTranId(), 16),
                            safe(tran.getTranTypeCd(), 2),
                            tran.getTranCatCd() != null ? tran.getTranCatCd() : 0,
                            safe(tran.getTranSource(), 10),
                            safe(tran.getTranDesc(), 100),
                            tran.getTranAmt() != null ? tran.getTranAmt().doubleValue() : 0.0,
                            tran.getMerchantId() != null ? tran.getMerchantId() : 0L,
                            safe(tran.getMerchantName(), 50),
                            safe(tran.getMerchantCity(), 50),
                            safe(tran.getMerchantZip(), 10),
                            safe(tran.getCardNum(), 16),
                            safe(tran.getOrigTimestamp(), 26),
                            safe(tran.getProcTimestamp(), 26));
                    tranCount++;
                }

                // Export cards (record type 'D')
                List<Card> cards = cardRepository.findAll();
                for (Card card : cards) {
                    sequenceCounter++;
                    writer.printf("D%-26s%09d0001 NORTH %-16s%011d%03d%-50s%-10s%-1s%n",
                            timestamp, sequenceCounter,
                            safe(card.getCardNum(), 16),
                            card.getCardAcctId() != null ? card.getCardAcctId() : 0L,
                            card.getCardCvvCd() != null ? card.getCardCvvCd() : 0,
                            safe(card.getCardEmbossedName(), 50),
                            safe(card.getCardExpirationDate(), 10),
                            safe(card.getCardActiveStatus(), 1));
                    cardCount++;
                }
            }

            log.info("CBEXPORT: Customers exported: {}", customerCount);
            log.info("CBEXPORT: Accounts exported: {}", accountCount);
            log.info("CBEXPORT: Cross-references exported: {}", xrefCount);
            log.info("CBEXPORT: Transactions exported: {}", tranCount);
            log.info("CBEXPORT: Cards exported: {}", cardCount);
            log.info("CBEXPORT: Total records exported: {}",
                    customerCount + accountCount + xrefCount + tranCount + cardCount);

            return RepeatStatus.FINISHED;
        };
    }

    private static String safe(String value, int maxLen) {
        if (value == null) return "";
        return value.length() > maxLen ? value.substring(0, maxLen) : value;
    }
}
