package com.carddemo.mqinquiry.service;

import com.carddemo.mqinquiry.message.InquiryRequestMessage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parity: the reply the migrated CDRA builds from the seeded ACCTDAT rows
 * carries the same bytes as the ACCTDAT unload the legacy program read
 * (app/data/ASCII/acctdata.txt, layout CVACT01Y).
 */
@SpringBootTest
class AccountInquiryParityTest {

    private static final Path ACCTDAT = Path.of("../../../app/data/ASCII/acctdata.txt");

    private static final int ID = 0;
    private static final int STATUS = 11;
    private static final int CURR_BAL = 12;
    private static final int CREDIT_LIMIT = 24;
    private static final int CASH_LIMIT = 36;
    private static final int OPEN_DATE = 48;
    private static final int EXPIRATION_DATE = 58;
    private static final int REISSUE_DATE = 68;
    private static final int CYCLE_CREDIT = 78;
    private static final int CYCLE_DEBIT = 90;
    private static final int GROUP_ID = 112;

    @Autowired
    private AccountInquiryService service;

    @Test
    void everyFieldOfTheReplyMatchesTheAcctdatUnload() throws IOException {
        List<String> unload = Files.readAllLines(ACCTDAT, StandardCharsets.ISO_8859_1);
        assertThat(unload).isNotEmpty();

        for (String record : unload.subList(0, 10)) {
            long accountId = Long.parseLong(record.substring(ID, ID + 11));
            String reply = service.inquire(
                    InquiryRequestMessage.parse(InquiryRequestMessage.render("INQA", accountId)),
                    "CARDDEMO.REQUEST.QUEUE").payload();

            assertThat(reply.substring(13, 24)).isEqualTo(record.substring(ID, ID + 11));
            assertThat(reply.substring(41, 42)).isEqualTo(record.substring(STATUS, STATUS + 1));
            assertThat(reply.substring(52, 64)).isEqualTo(record.substring(CURR_BAL, CURR_BAL + 12));
            assertThat(reply.substring(79, 91)).isEqualTo(record.substring(CREDIT_LIMIT, CREDIT_LIMIT + 12));
            assertThat(reply.substring(104, 116)).isEqualTo(record.substring(CASH_LIMIT, CASH_LIMIT + 12));
            assertThat(reply.substring(128, 138)).isEqualTo(record.substring(OPEN_DATE, OPEN_DATE + 10));
            assertThat(reply.substring(150, 160))
                    .isEqualTo(record.substring(EXPIRATION_DATE, EXPIRATION_DATE + 10));
            assertThat(reply.substring(172, 182)).isEqualTo(record.substring(REISSUE_DATE, REISSUE_DATE + 10));
            assertThat(reply.substring(195, 207)).isEqualTo(record.substring(CYCLE_CREDIT, CYCLE_CREDIT + 12));
            assertThat(reply.substring(219, 231)).isEqualTo(record.substring(CYCLE_DEBIT, CYCLE_DEBIT + 12));
            assertThat(reply.substring(242, 252)).isEqualTo(record.substring(GROUP_ID, GROUP_ID + 10));
        }
    }
}
