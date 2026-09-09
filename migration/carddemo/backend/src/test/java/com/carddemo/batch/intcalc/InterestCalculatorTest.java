package com.carddemo.batch.intcalc;

import com.carddemo.common.batch.AbendException;
import com.carddemo.common.batch.AbendService;
import com.carddemo.common.domain.AccountRecord;
import com.carddemo.common.domain.CardXrefRecord;
import com.carddemo.common.domain.DisclosureGroupId;
import com.carddemo.common.domain.DisclosureGroupRecord;
import com.carddemo.common.domain.TransactionCategoryBalanceId;
import com.carddemo.common.domain.TransactionCategoryBalanceRecord;
import com.carddemo.common.domain.TransactionRecord;
import com.carddemo.common.repository.AccountRepository;
import com.carddemo.common.repository.CardXrefRepository;
import com.carddemo.common.repository.DisclosureGroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The business rules of CBACT04C (FR-I5 … FR-I18, FR-I22).
 */
class InterestCalculatorTest {

    private static final long ACCT_ID = 12345678901L;
    private static final String CARD_NUM = "4111111111111111";
    private static final String GROUP_ID = "ZEROPCT";
    private static final String RUN_DATE = "2022071800";

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final CardXrefRepository cardXrefs = mock(CardXrefRepository.class);
    private final DisclosureGroupRepository disclosureGroups = mock(DisclosureGroupRepository.class);
    private final Map<DisclosureGroupId, BigDecimal> rates = new HashMap<>();

    private InterestCalculator calculator;
    private AccountRecord account;

    @BeforeEach
    void setUp() {
        account = account(ACCT_ID, GROUP_ID, "500.00", "120.00", "300.00");
        when(accounts.findById(ACCT_ID)).thenReturn(Optional.of(account));
        when(cardXrefs.findByAcctId(ACCT_ID)).thenReturn(Optional.of(cardXref(ACCT_ID, CARD_NUM)));
        when(disclosureGroups.findById(any())).thenAnswer(invocation -> {
            DisclosureGroupId id = invocation.getArgument(0);
            return Optional.ofNullable(rates.get(id)).map(rate -> disclosureGroup(id, rate));
        });

        TranIdSequence tranIds = new TranIdSequence(RUN_DATE);
        tranIds.open(new org.springframework.batch.item.ExecutionContext());
        calculator = new InterestCalculator(accounts, cardXrefs, disclosureGroups,
                new AbendService(), tranIds,
                Clock.fixed(Instant.parse("2022-07-18T10:04:05.670Z"), ZoneId.of("UTC")));
    }

    /** FR-I5: the rate keyed by the account's group and the balance's type/category. */
    @Test
    void usesTheExactDisclosureGroupRate() {
        rate(GROUP_ID, "01", 1, "12.00");
        rate("DEFAULT", "01", 1, "24.00");

        InterestPosting posting = calculator.post(group(balance("01", 1, "1000.00")));

        assertThat(posting.transactions()).singleElement()
                .extracting(TransactionRecord::getAmount)
                .isEqualTo(new BigDecimal("10.00"));
    }

    /** FR-I6: missing group row -> retry with DEFAULT, same type and category. */
    @Test
    void fallsBackToTheDefaultDisclosureGroup() {
        rate("DEFAULT", "01", 1, "24.00");

        InterestPosting posting = calculator.post(group(balance("01", 1, "1000.00")));

        assertThat(posting.transactions()).singleElement()
                .extracting(TransactionRecord::getAmount)
                .isEqualTo(new BigDecimal("20.00"));
        verify(disclosureGroups).findById(new DisclosureGroupId(GROUP_ID, "01", 1));
        verify(disclosureGroups).findById(new DisclosureGroupId("DEFAULT", "01", 1));
    }

    /** FR-I7: no DEFAULT row either -> ERROR READING DEFAULT DISCLOSURE GROUP, abend. */
    @Test
    void abendsWhenTheDefaultDisclosureGroupIsMissing() {
        assertThatThrownBy(() -> calculator.post(group(balance("01", 1, "1000.00"))))
                .isInstanceOf(AbendException.class)
                .hasMessageContaining("ERROR READING DEFAULT DISCLOSURE GROUP");
    }

    /** FR-I8: a zero rate produces neither interest nor a transaction. */
    @Test
    void skipsCategoriesWithAZeroRate() {
        rate(GROUP_ID, "01", 1, "0.00");

        InterestPosting posting = calculator.post(group(balance("01", 1, "1000.00")));

        assertThat(posting.transactions()).isEmpty();
        assertThat(posting.accountUpdate().getCurrBal()).isEqualTo(new BigDecimal("500.00"));
    }

    /** FR-I9: unrounded COMPUTE into PIC S9(09)V99 truncates, it does not round half up. */
    @Test
    void truncatesInterestTowardsZero() {
        rate(GROUP_ID, "01", 1, "19.99");

        InterestPosting posting = calculator.post(group(balance("01", 1, "1000.00")));

        // 1000.00 * 19.99 / 1200 = 16.65833...; HALF_UP would give 16.66
        assertThat(posting.transactions().get(0).getAmount()).isEqualTo(new BigDecimal("16.65"));
    }

    /** FR-I9: a negative balance truncates toward zero too. */
    @Test
    void negativeBalancesTruncateTowardsZero() {
        rate(GROUP_ID, "01", 1, "19.99");

        InterestPosting posting = calculator.post(group(balance("01", 1, "-1000.00")));

        assertThat(posting.transactions().get(0).getAmount()).isEqualTo(new BigDecimal("-16.65"));
    }

    /** FR-I10: a non-zero rate on a zero balance still writes a 0.00 transaction. */
    @Test
    void writesAZeroAmountTransactionForAZeroBalance() {
        rate(GROUP_ID, "01", 1, "15.00");

        InterestPosting posting = calculator.post(group(balance("01", 1, "0.00")));

        assertThat(posting.transactions()).singleElement()
                .extracting(TransactionRecord::getAmount)
                .isEqualTo(new BigDecimal("0.00"));
    }

    /** FR-I11: the account total is the sum of the already-truncated amounts. */
    @Test
    void totalInterestIsTheSumOfTheTruncatedAmounts() {
        rate(GROUP_ID, "01", 1, "19.99");
        rate(GROUP_ID, "01", 2, "19.99");

        InterestPosting posting = calculator.post(
                group(balance("01", 1, "1000.00"), balance("01", 2, "1000.00")));

        // 16.65 + 16.65, not the 33.32 a total-then-truncate would give
        assertThat(posting.accountUpdate().getCurrBal()).isEqualTo(new BigDecimal("533.30"));
    }

    /** FR-I12, FR-I13, FR-I14: the generated CVTRA05Y record. */
    @Test
    void buildsTheInterestTransaction() {
        rate(GROUP_ID, "01", 1, "12.00");

        TransactionRecord tran = calculator.post(group(balance("01", 1, "1000.00")))
                .transactions().get(0);

        assertThat(tran.getId()).isEqualTo("2022071800000001");
        assertThat(tran.getTypeCd()).isEqualTo("01");
        assertThat(tran.getCatCd()).isEqualTo(5);
        assertThat(tran.getSource()).isEqualTo("System");
        assertThat(tran.getDescription()).isEqualTo("Int. for a/c 12345678901");
        assertThat(tran.getAmount()).isEqualTo(new BigDecimal("10.00"));
        assertThat(tran.getMerchantId()).isEqualTo(0L);
        assertThat(tran.getMerchantName()).isEmpty();
        assertThat(tran.getMerchantCity()).isEmpty();
        assertThat(tran.getMerchantZip()).isEmpty();
        assertThat(tran.getCardNum()).isEqualTo(CARD_NUM);
        assertThat(tran.getOrigTs()).isEqualTo("2022-07-18-10.04.05.670000");
        assertThat(tran.getProcTs()).isEqualTo(tran.getOrigTs());
    }

    /** FR-I15: post the total, then zero both cycle totals. */
    @Test
    void postsTotalInterestAndResetsTheCycleTotals() {
        rate(GROUP_ID, "01", 1, "12.00");

        AccountRecord updated = calculator.post(group(balance("01", 1, "1000.00"))).accountUpdate();

        assertThat(updated.getCurrBal()).isEqualTo(new BigDecimal("510.00"));
        assertThat(updated.getCurrCycCredit()).isEqualByComparingTo("0.00");
        assertThat(updated.getCurrCycDebit()).isEqualByComparingTo("0.00");
    }

    /** FR-I16 (quirk): the last account of the run keeps its balance and cycle totals. */
    @Test
    void lastAccountOfTheRunIsNeverUpdated() {
        rate(GROUP_ID, "01", 1, "12.00");

        InterestPosting posting = calculator.post(new AccountBalanceGroup(
                ACCT_ID, List.of(balance("01", 1, "1000.00")), true));

        assertThat(posting.accountUpdate()).isNull();
        assertThat(posting.transactions()).hasSize(1);
        assertThat(account.getCurrBal()).isEqualTo(new BigDecimal("500.00"));
        assertThat(account.getCurrCycCredit()).isEqualTo(new BigDecimal("120.00"));
        assertThat(account.getCurrCycDebit()).isEqualTo(new BigDecimal("300.00"));
    }

    /** FR-I4: one account read and one XREF read per account, whatever the category count. */
    @Test
    void usesOneAccountAndXrefPerGroup() {
        rate(GROUP_ID, "01", 1, "12.00");
        rate(GROUP_ID, "01", 2, "12.00");
        rate(GROUP_ID, "02", 1, "12.00");

        calculator.post(group(balance("01", 1, "10.00"),
                balance("01", 2, "10.00"),
                balance("02", 1, "10.00")));

        verify(accounts, times(1)).findById(ACCT_ID);
        verify(cardXrefs, times(1)).findByAcctId(ACCT_ID);
    }

    /** FR-I17: no account row -> ACCOUNT NOT FOUND / ERROR READING ACCOUNT FILE, abend. */
    @Test
    void abendsWhenTheAccountIsMissing() {
        when(accounts.findById(ACCT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> calculator.post(group(balance("01", 1, "10.00"))))
                .isInstanceOf(AbendException.class)
                .hasMessageContaining("ERROR READING ACCOUNT FILE");
    }

    /** FR-I18: no XREF row -> ACCOUNT NOT FOUND / ERROR READING XREF FILE, abend. */
    @Test
    void abendsWhenTheCardXrefIsMissing() {
        when(cardXrefs.findByAcctId(ACCT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> calculator.post(group(balance("01", 1, "10.00"))))
                .isInstanceOf(AbendException.class)
                .hasMessageContaining("ERROR READING XREF FILE");
    }

    /**
     * FR-I22: 1400-COMPUTE-FEES is empty in CBACT04C, so the run produces interest
     * transactions only — one per non-zero-rate balance, none of any other kind.
     */
    @Test
    void computesNoFees() {
        rate(GROUP_ID, "01", 1, "12.00");
        rate(GROUP_ID, "01", 2, "0.00");

        InterestPosting posting = calculator.post(
                group(balance("01", 1, "1000.00"), balance("01", 2, "1000.00")));

        assertThat(posting.transactions()).hasSize(1);
        assertThat(posting.transactions()).allSatisfy(tran -> {
            assertThat(tran.getTypeCd()).isEqualTo("01");
            assertThat(tran.getCatCd()).isEqualTo(5);
            assertThat(tran.getDescription()).startsWith("Int. for a/c ");
        });
    }

    private void rate(String groupId, String typeCd, int catCd, String rate) {
        rates.put(new DisclosureGroupId(groupId, typeCd, catCd), new BigDecimal(rate));
    }

    private AccountBalanceGroup group(TransactionCategoryBalanceRecord... balances) {
        return new AccountBalanceGroup(ACCT_ID, List.of(balances), false);
    }

    private static TransactionCategoryBalanceRecord balance(String typeCd, int catCd, String bal) {
        TransactionCategoryBalanceRecord record = new TransactionCategoryBalanceRecord();
        record.setId(new TransactionCategoryBalanceId(ACCT_ID, typeCd, catCd));
        record.setBal(new BigDecimal(bal));
        return record;
    }

    private static DisclosureGroupRecord disclosureGroup(DisclosureGroupId id, BigDecimal rate) {
        DisclosureGroupRecord record = new DisclosureGroupRecord();
        record.setId(id);
        record.setIntRate(rate);
        return record;
    }

    private static AccountRecord account(long acctId, String groupId,
                                         String currBal, String cycCredit, String cycDebit) {
        AccountRecord record = new AccountRecord();
        record.setAcctId(acctId);
        record.setGroupId(groupId);
        record.setCurrBal(new BigDecimal(currBal));
        record.setCurrCycCredit(new BigDecimal(cycCredit));
        record.setCurrCycDebit(new BigDecimal(cycDebit));
        return record;
    }

    private static CardXrefRecord cardXref(long acctId, String cardNum) {
        CardXrefRecord record = new CardXrefRecord();
        record.setCardNum(cardNum);
        record.setAcctId(acctId);
        return record;
    }
}
