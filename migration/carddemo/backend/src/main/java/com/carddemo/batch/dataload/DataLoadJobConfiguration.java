package com.carddemo.batch.dataload;

import com.carddemo.common.batch.FixedWidthRecord;
import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.domain.CardRecord;
import com.carddemo.common.domain.CardXrefRecord;
import com.carddemo.common.domain.CustomerRecord;
import com.carddemo.common.domain.DailyTransactionRecord;
import com.carddemo.common.domain.DisclosureGroupId;
import com.carddemo.common.domain.DisclosureGroupRecord;
import com.carddemo.common.domain.TransactionCategoryBalanceId;
import com.carddemo.common.domain.TransactionCategoryBalanceRecord;
import com.carddemo.common.domain.TransactionCategoryId;
import com.carddemo.common.domain.TransactionCategoryRecord;
import com.carddemo.common.domain.TransactionTypeRecord;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.builder.JpaItemWriterBuilder;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.PassThroughLineMapper;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

/**
 * Job DATALOAD - loads the CardDemo master files from their ASCII unloads.
 *
 * <p>This replaces the S-18 DataLoadAndSetup IDCAMS jobs (DEFACTF/DEFCUSTF/
 * DEFCARDF/... DELETE + DEFINE + REPRO): step PREDEL clears the target tables,
 * then one step per input file streams it in. The steps are named after the DD
 * names the JCL used.
 *
 * <p>It is also the reference implementation every stream copies for its own JCL
 * job: one {@code @Configuration} class per JCL job, one {@link Step} per
 * {@code EXEC PGM=}, chunk-oriented readers and writers so a restart resumes at
 * the last committed chunk from the JobRepository (D-3).
 *
 * <pre>
 * java -jar carddemo.jar --spring.main.web-application-type=none \
 *      --spring.profiles.active=postgres --spring.batch.job.name=DATALOAD
 * </pre>
 *
 * <p>USRSEC is deliberately not loaded here: the legacy DUSRSECJ job built it from
 * in-stream SYSUT1 data rather than an unload, and those records are seeded by
 * the {@code R__seed_carddemo_data.sql} Flyway migration.
 */
@Configuration
public class DataLoadJobConfiguration {

    /** Table clear order used by PREDEL: children before parents. */
    private static final List<String> TABLES_TO_CLEAR = List.of(
            "transaction_categories", "transaction_types", "disclosure_groups",
            "transaction_category_balances", "daily_transactions", "card_xref",
            "cards", "customers", "accounts");

    private static final int CHUNK_SIZE = 100;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final EntityManagerFactory entityManagerFactory;
    private final JdbcTemplate jdbcTemplate;
    private final String dataDir;

    public DataLoadJobConfiguration(JobRepository jobRepository,
                                    PlatformTransactionManager transactionManager,
                                    EntityManagerFactory entityManagerFactory,
                                    JdbcTemplate jdbcTemplate,
                                    @Value("${carddemo.batch.data-dir}") String dataDir) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.entityManagerFactory = entityManagerFactory;
        this.jdbcTemplate = jdbcTemplate;
        this.dataDir = dataDir;
    }

    @Bean
    public Job dataLoadJob() {
        return new JobBuilder("DATALOAD", jobRepository)
                .start(predeleteStep())
                .next(loadStep("LOADACCT", "acctdata.txt", 300, this::toAccount))
                .next(loadStep("LOADCUST", "custdata.txt", 500, this::toCustomer))
                .next(loadStep("LOADCARD", "carddata.txt", 150, this::toCard))
                .next(loadStep("LOADXREF", "cardxref.txt", 50, this::toCardXref))
                .next(loadStep("LOADDLTR", "dailytran.txt", 350, this::toDailyTransaction))
                .next(loadStep("LOADTCAT", "tcatbal.txt", 50, this::toCategoryBalance))
                .next(loadStep("LOADDISC", "discgrp.txt", 50, this::toDisclosureGroup))
                .next(loadStep("LOADTRTP", "trantype.txt", 60, this::toTransactionType))
                .next(loadStep("LOADTRCT", "trancatg.txt", 60, this::toTransactionCategory))
                .build();
    }

    /** PREDEL - the IDCAMS DELETE half of the legacy define-and-load jobs. */
    private Step predeleteStep() {
        Tasklet tasklet = (contribution, chunkContext) -> {
            TABLES_TO_CLEAR.forEach(table -> jdbcTemplate.execute("DELETE FROM " + table));
            return RepeatStatus.FINISHED;
        };
        return new StepBuilder("PREDEL", jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }

    private <T> Step loadStep(String name, String fileName, int recordLength,
                              RecordMapper<T> mapper) {
        ItemReader<String> reader = new FlatFileItemReaderBuilder<String>()
                .name(name + "Reader")
                .resource(new FileSystemResource(dataDir + "/" + fileName))
                .lineMapper(new PassThroughLineMapper())
                .build();
        ItemProcessor<String, T> processor = line -> mapper.map(new FixedWidthRecord(line, recordLength));
        ItemWriter<T> writer = new JpaItemWriterBuilder<T>()
                .entityManagerFactory(entityManagerFactory)
                .build();
        return new StepBuilder(name, jobRepository)
                .<String, T>chunk(CHUNK_SIZE, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    /** Maps one fixed-width record to the entity a load step writes. */
    @FunctionalInterface
    private interface RecordMapper<T> {
        T map(FixedWidthRecord record);
    }

    // ACCTDAT / CVACT01Y ACCOUNT-RECORD
    private AccountRecord toAccount(FixedWidthRecord r) {
        AccountRecord account = new AccountRecord();
        account.setAcctId(r.number(0, 11));
        account.setActiveStatus(r.text(11, 1));
        account.setCurrBal(r.signed(12, 12, 2));
        account.setCreditLimit(r.signed(24, 12, 2));
        account.setCashCreditLimit(r.signed(36, 12, 2));
        account.setOpenDate(r.text(48, 10));
        account.setExpiraionDate(r.text(58, 10));
        account.setReissueDate(r.text(68, 10));
        account.setCurrCycCredit(r.signed(78, 12, 2));
        account.setCurrCycDebit(r.signed(90, 12, 2));
        account.setAddrZip(r.text(102, 10));
        account.setGroupId(r.text(112, 10));
        return account;
    }

    // CUSTDAT / CVCUS01Y CUSTOMER-RECORD
    private CustomerRecord toCustomer(FixedWidthRecord r) {
        CustomerRecord customer = new CustomerRecord();
        customer.setCustId(r.number(0, 9));
        customer.setFirstName(r.text(9, 25));
        customer.setMiddleName(r.text(34, 25));
        customer.setLastName(r.text(59, 25));
        customer.setAddrLine1(r.text(84, 50));
        customer.setAddrLine2(r.text(134, 50));
        customer.setAddrLine3(r.text(184, 50));
        customer.setAddrStateCd(r.text(234, 2));
        customer.setAddrCountryCd(r.text(236, 3));
        customer.setAddrZip(r.text(239, 10));
        customer.setPhoneNum1(r.text(249, 15));
        customer.setPhoneNum2(r.text(264, 15));
        customer.setSsn(r.number(279, 9));
        customer.setGovtIssuedId(r.text(288, 20));
        customer.setDobYyyyMmDd(r.text(308, 10));
        customer.setEftAccountId(r.text(318, 10));
        customer.setPriCardHolderInd(r.text(328, 1));
        customer.setFicoCreditScore((int) r.number(329, 3));
        return customer;
    }

    // CARDDAT / CVACT02Y CARD-RECORD
    private CardRecord toCard(FixedWidthRecord r) {
        CardRecord card = new CardRecord();
        card.setCardNum(r.text(0, 16));
        card.setAcctId(r.number(16, 11));
        card.setCvvCd((int) r.number(27, 3));
        card.setEmbossedName(r.text(30, 50));
        card.setExpiraionDate(r.text(80, 10));
        card.setActiveStatus(r.text(90, 1));
        return card;
    }

    // CCXREF / CVACT03Y CARD-XREF-RECORD
    private CardXrefRecord toCardXref(FixedWidthRecord r) {
        CardXrefRecord xref = new CardXrefRecord();
        xref.setCardNum(r.text(0, 16));
        xref.setCustId(r.number(16, 9));
        xref.setAcctId(r.number(25, 11));
        return xref;
    }

    // DALYTRAN / CVTRA06Y DALYTRAN-RECORD
    private DailyTransactionRecord toDailyTransaction(FixedWidthRecord r) {
        DailyTransactionRecord tran = new DailyTransactionRecord();
        tran.setId(r.text(0, 16));
        tran.setTypeCd(r.text(16, 2));
        tran.setCatCd((int) r.number(18, 4));
        tran.setSource(r.text(22, 10));
        tran.setDescription(r.text(32, 100));
        tran.setAmount(r.signed(132, 11, 2));
        tran.setMerchantId(r.number(143, 9));
        tran.setMerchantName(r.text(152, 50));
        tran.setMerchantCity(r.text(202, 50));
        tran.setMerchantZip(r.text(252, 10));
        tran.setCardNum(r.text(262, 16));
        tran.setOrigTs(r.text(278, 26));
        tran.setProcTs(r.text(304, 26));
        return tran;
    }

    // TCATBALF / CVTRA01Y TRAN-CAT-BAL-RECORD
    private TransactionCategoryBalanceRecord toCategoryBalance(FixedWidthRecord r) {
        TransactionCategoryBalanceRecord balance = new TransactionCategoryBalanceRecord();
        balance.setId(new TransactionCategoryBalanceId(
                r.number(0, 11), r.text(11, 2), (int) r.number(13, 4)));
        balance.setBal(r.signed(17, 11, 2));
        return balance;
    }

    // DISCGRP / CVTRA02Y DIS-GROUP-RECORD
    private DisclosureGroupRecord toDisclosureGroup(FixedWidthRecord r) {
        DisclosureGroupRecord group = new DisclosureGroupRecord();
        group.setId(new DisclosureGroupId(
                r.text(0, 10), r.text(10, 2), (int) r.number(12, 4)));
        group.setIntRate(r.signed(16, 6, 2));
        return group;
    }

    // TRANTYPE / CVTRA03Y TRAN-TYPE-RECORD
    private TransactionTypeRecord toTransactionType(FixedWidthRecord r) {
        TransactionTypeRecord type = new TransactionTypeRecord();
        type.setTranType(r.text(0, 2));
        type.setTranTypeDesc(r.text(2, 50));
        return type;
    }

    // TRANCATG / CVTRA04Y TRAN-CAT-RECORD
    private TransactionCategoryRecord toTransactionCategory(FixedWidthRecord r) {
        TransactionCategoryRecord category = new TransactionCategoryRecord();
        category.setId(new TransactionCategoryId(r.text(0, 2), (int) r.number(2, 4)));
        category.setTranCatTypeDesc(r.text(6, 50));
        return category;
    }
}
