package com.carddemo.batch.statement;

import com.carddemo.common.batch.AbendException;
import com.carddemo.common.batch.AbendService;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ExecutionContext;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** FR-10, FR-15, FR-38 to FR-42: the CBSTM03A mainline and its abend paths. */
class StatementItemReaderTest {

    private static final String CUSTOMER = StatementFixtures.customer("John", "Q", "Public",
            "410 Terry Ave N", "Suite 100", "Seattle", "WA", "USA", "98109", "789");
    private static final String ACCOUNT =
            StatementFixtures.account("00000000011", new BigDecimal("100.00"));

    @Test
    void producesOneStatementPerXrefRecordInCardNumberOrder() {
        StatementFileAccessStub files = files()
                .withCustomer("000000001", CUSTOMER)
                .withAccount("00000000011", ACCOUNT);
        StatementItemReader reader = reader(files);

        reader.open(new ExecutionContext());
        Statement first = reader.read();
        Statement second = reader.read();

        assertThat(first.cardNumber()).isEqualTo("4111111111111111");
        assertThat(first.transactionRecords()).hasSize(1);
        assertThat(second.cardNumber()).isEqualTo("4222222222222222");
        assertThat(second.transactionRecords()).isEmpty();
        assertThat(reader.read()).isNull();
        reader.close();
    }

    @Test
    void closesEveryFileInTheLegacyOrder() {
        StatementFileAccessStub files = files()
                .withCustomer("000000001", CUSTOMER)
                .withAccount("00000000011", ACCOUNT);
        StatementItemReader reader = reader(files);

        reader.open(new ExecutionContext());
        reader.close();

        assertThat(files.calls()).endsWith("TRNXFILE:C", "XREFFILE:C", "CUSTFILE:C", "ACCTFILE:C");
    }

    @Test
    void abendsWhenTheCustomerIsNotFound() {
        StatementItemReader reader = reader(files().withAccount("00000000011", ACCOUNT));
        reader.open(new ExecutionContext());

        assertThatThrownBy(reader::read)
                .isInstanceOf(AbendException.class)
                .extracting(failure -> ((AbendException) failure).getAbendData())
                .satisfies(data -> {
                    assertThat(data.abendMsg()).isEqualTo("ERROR READING CUSTFILE");
                    assertThat(data.abendReason()).isEqualTo(StatementFileArea.RC_NOT_FOUND);
                });
    }

    @Test
    void abendsWhenTheAccountIsNotFound() {
        StatementItemReader reader = reader(files().withCustomer("000000001", CUSTOMER));
        reader.open(new ExecutionContext());

        assertThatThrownBy(reader::read)
                .isInstanceOf(AbendException.class)
                .extracting(failure -> ((AbendException) failure).getAbendData())
                .satisfies(data -> assertThat(data.abendMsg()).isEqualTo("ERROR READING ACCTFILE"));
    }

    @Test
    void abendsWhenAFileCannotBeOpened() {
        StatementItemReader reader = reader(files().failingToOpen(StatementFileArea.DD_XREFFILE));

        assertThatThrownBy(() -> reader.open(new ExecutionContext()))
                .isInstanceOf(AbendException.class)
                .extracting(failure -> ((AbendException) failure).getAbendData())
                .satisfies(data -> {
                    assertThat(data.abendMsg()).isEqualTo("ERROR OPENING XREFFILE");
                    assertThat(data.abendReason()).isEqualTo(StatementFileArea.RC_OPEN_FAILED);
                });
    }

    private static StatementFileAccessStub files() {
        return new StatementFileAccessStub(
                List.of(StatementFixtures.work("4111111111111111", "0000000000000042",
                        "Coffee shop", new BigDecimal("13.75"))),
                List.of(StatementFixtures.xref("4111111111111111", 1L, 11L),
                        StatementFixtures.xref("4222222222222222", 1L, 11L)));
    }

    private static StatementItemReader reader(StatementFileAccessStub files) {
        return new StatementItemReader(files, new StatementAbend(new AbendService()));
    }
}
