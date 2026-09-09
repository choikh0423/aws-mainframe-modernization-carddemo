package com.carddemo.common;

import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.domain.CustomerRecord;
import com.carddemo.common.domain.DisclosureGroupId;
import com.carddemo.common.domain.DisclosureGroupRecord;
import com.carddemo.common.repository.AccountRepository;
import com.carddemo.common.repository.CardRepository;
import com.carddemo.common.repository.CustomerRepository;
import com.carddemo.common.repository.DailyTransactionRepository;
import com.carddemo.common.repository.Db2TransactionTypeCategoryRepository;
import com.carddemo.common.repository.Db2TransactionTypeRepository;
import com.carddemo.common.repository.DisclosureGroupRepository;
import com.carddemo.common.repository.SecUserRepository;
import com.carddemo.common.repository.TransactionCategoryBalanceRepository;
import com.carddemo.common.repository.TransactionCategoryRepository;
import com.carddemo.common.repository.TransactionTypeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The V1 baseline plus the ASCII-derived seed: every estate table exists, the
 * record counts match the unloads in app/data/ASCII, and the tricky decodings
 * (signed overpunch, implied decimals) survived the round trip into JPA.
 */
@SpringBootTest
class SchemaAndSeedTest {

    /** Every table V1__baseline_carddemo_estate.sql creates. */
    private static final List<String> ESTATE_TABLES = List.of(
            "accounts", "customers", "cards", "card_xref", "transactions",
            "daily_transactions", "transaction_category_balances", "disclosure_groups",
            "transaction_types", "transaction_categories", "sec_users",
            "db2_transaction_type", "db2_transaction_type_category", "auth_fraud",
            "pending_auth_summary", "pending_auth_detail");

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private AccountRepository accounts;
    @Autowired
    private CustomerRepository customers;
    @Autowired
    private CardRepository cards;
    @Autowired
    private DailyTransactionRepository dailyTransactions;
    @Autowired
    private TransactionCategoryBalanceRepository categoryBalances;
    @Autowired
    private DisclosureGroupRepository disclosureGroups;
    @Autowired
    private TransactionTypeRepository transactionTypes;
    @Autowired
    private TransactionCategoryRepository transactionCategories;
    @Autowired
    private SecUserRepository secUsers;
    @Autowired
    private Db2TransactionTypeRepository db2TransactionTypes;
    @Autowired
    private Db2TransactionTypeCategoryRepository db2TransactionTypeCategories;

    @Test
    void everyEstateTableIsQueryable() {
        for (String table : ESTATE_TABLES) {
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class))
                    .as("table %s", table)
                    .isNotNull();
        }
    }

    @Test
    void seedRowCountsMatchTheAsciiUnloads() {
        assertThat(accounts.count()).isEqualTo(50);
        assertThat(customers.count()).isEqualTo(50);
        assertThat(cards.count()).isEqualTo(50);
        assertThat(dailyTransactions.count()).isEqualTo(300);
        assertThat(categoryBalances.count()).isEqualTo(50);
        assertThat(disclosureGroups.count()).isEqualTo(51);
        assertThat(transactionTypes.count()).isEqualTo(7);
        assertThat(transactionCategories.count()).isEqualTo(18);
        assertThat(db2TransactionTypes.count()).isEqualTo(7);
        assertThat(db2TransactionTypeCategories.count()).isEqualTo(18);
        assertThat(secUsers.count()).isEqualTo(10);
    }

    @Test
    void signedAccountAmountsKeepTheirImpliedDecimals() {
        AccountRecord account = accounts.findById(1L).orElseThrow();
        assertThat(account.getActiveStatus()).isEqualTo("Y");
        assertThat(account.getCurrBal()).isEqualByComparingTo(new BigDecimal("194.00"));
        assertThat(account.getCreditLimit()).isEqualByComparingTo(new BigDecimal("2020.00"));
        assertThat(account.getOpenDate()).isEqualTo("2014-11-20");
    }

    @Test
    void disclosureGroupInterestRateKeepsTwoDecimals() {
        DisclosureGroupRecord group = disclosureGroups
                .findById(new DisclosureGroupId("A000000000", "01", 1)).orElseThrow();
        assertThat(group.getIntRate()).isEqualByComparingTo(new BigDecimal("15.00"));
    }

    @Test
    void customerTextFieldsAreTrimmedNotPadded() {
        CustomerRecord customer = customers.findById(1L).orElseThrow();
        assertThat(customer.getFirstName()).isEqualTo("Immanuel");
        assertThat(customer.getLastName()).isEqualTo("Kessler");
        assertThat(customer.getFicoCreditScore()).isEqualTo(274);
    }

    @Test
    void everyCardBelongsToASeededAccount() {
        cards.findAll().forEach(card ->
                assertThat(accounts.existsById(card.getAcctId()))
                        .as("card %s account %s", card.getCardNum(), card.getAcctId())
                        .isTrue());
    }
}
