package com.carddemo.batch.statement;

import com.carddemo.common.repository.CardXrefRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** FR-B1 to FR-B11: the CBSTM03B file-access subroutine. */
@SpringBootTest
class StatementFileAccessTest {

    @Autowired
    private StatementFileAccess files;
    @Autowired
    private CardXrefRepository cardXrefs;

    @Test
    void readsXreffileSequentiallyInCardNumberOrderAndReportsEndOfFile() {
        assertThat(call(StatementFileArea.DD_XREFFILE, StatementFileArea.OPER_OPEN).getRc())
                .isEqualTo(StatementFileArea.RC_OK);

        String previous = "";
        long read = 0;
        StatementFileArea area;
        while ((area = call(StatementFileArea.DD_XREFFILE, StatementFileArea.OPER_READ))
                .getRc().equals(StatementFileArea.RC_OK)) {
            assertThat(area.getData()).hasSize(XrefLayout.LENGTH);
            assertThat(XrefLayout.cardNumber(area.getData())).isGreaterThan(previous);
            previous = XrefLayout.cardNumber(area.getData());
            read++;
        }

        assertThat(area.getRc()).isEqualTo(StatementFileArea.RC_END_OF_FILE);
        assertThat(read).isEqualTo(cardXrefs.count());
        assertThat(call(StatementFileArea.DD_XREFFILE, StatementFileArea.OPER_CLOSE).getRc())
                .isEqualTo(StatementFileArea.RC_OK);
    }

    @Test
    void readsCustfileAndAcctfileByKey() {
        StatementFileArea customer = keyed(StatementFileArea.DD_CUSTFILE, "000000001", 9);
        assertThat(customer.getRc()).isEqualTo(StatementFileArea.RC_OK);
        assertThat(customer.getData()).hasSize(CustomerLayout.LENGTH);
        assertThat(CustomerLayout.firstName(customer.getData()).trim()).isEqualTo("Immanuel");

        StatementFileArea account = keyed(StatementFileArea.DD_ACCTFILE, "00000000001", 11);
        assertThat(account.getRc()).isEqualTo(StatementFileArea.RC_OK);
        assertThat(account.getData()).hasSize(AccountLayout.LENGTH);
        assertThat(AccountLayout.accountId(account.getData())).isEqualTo("00000000001");
    }

    @Test
    void reportsNotFoundForAKeyThatIsNotOnFile() {
        assertThat(keyed(StatementFileArea.DD_CUSTFILE, "999999999", 9).getRc())
                .isEqualTo(StatementFileArea.RC_NOT_FOUND);
        assertThat(keyed(StatementFileArea.DD_ACCTFILE, "         ", 9).getRc())
                .isEqualTo(StatementFileArea.RC_NOT_FOUND);
    }

    @Test
    void reportsAnErrorWhenAFileIsReadBeforeItIsOpened() {
        StatementFileAccess unopened = freshAdapter();
        StatementFileArea area = new StatementFileArea();
        area.setDd(StatementFileArea.DD_TRNXFILE);
        area.setOper(StatementFileArea.OPER_READ);

        unopened.call(area);

        assertThat(area.getRc()).isEqualTo(StatementFileArea.RC_ERROR);
    }

    @Test
    void leavesTheReturnCodeUntouchedForUnknownDdNamesAndUnimplementedOperations() {
        StatementFileArea unknownDd = new StatementFileArea();
        unknownDd.setDd("NOSUCHDD");
        unknownDd.setOper(StatementFileArea.OPER_READ);
        unknownDd.setRc("77");
        files.call(unknownDd);
        assertThat(unknownDd.getRc()).isEqualTo("77");

        StatementFileArea write = new StatementFileArea();
        write.setDd(StatementFileArea.DD_XREFFILE);
        write.setOper(StatementFileArea.OPER_WRITE);
        write.setRc("77");
        files.call(write);
        assertThat(write.getRc()).isEqualTo("77");

        StatementFileArea rewrite = new StatementFileArea();
        rewrite.setDd(StatementFileArea.DD_CUSTFILE);
        rewrite.setOper(StatementFileArea.OPER_REWRITE);
        rewrite.setRc("77");
        files.call(rewrite);
        assertThat(rewrite.getRc()).isEqualTo("77");
    }

    private StatementFileAccess freshAdapter() {
        return new StatementFileAccess(null, null, null, null);
    }

    private StatementFileArea call(String dd, char oper) {
        StatementFileArea area = new StatementFileArea();
        area.setDd(dd);
        area.setOper(oper);
        files.call(area);
        return area;
    }

    private StatementFileArea keyed(String dd, String key, int keyLength) {
        StatementFileArea area = new StatementFileArea();
        area.setDd(dd);
        area.setOper(StatementFileArea.OPER_READ_KEY);
        area.setKey(key);
        area.setKeyLength(keyLength);
        files.call(area);
        return area;
    }
}
