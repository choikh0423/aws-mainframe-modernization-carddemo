package com.carddemo.pendingauth.service;

import com.carddemo.common.domain.AuthFraudId;
import com.carddemo.common.domain.AuthFraudRecord;
import com.carddemo.common.repository.AuthFraudRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * auth_fraud carries every value CARDDEMO.AUTHFRDS can hold
 * (app/app-authorization-ims-db2-mq/ddl/AUTHFRDS.ddl and its DCLGEN):
 * DECIMAL(12,2) amounts, a real DATE report date, a SMALLINT POS entry mode and
 * a VARCHAR(22) merchant name.
 */
@SpringBootTest
@Transactional
class AuthFraudColumnFidelityTest {

    /** The largest value {@code DECIMAL(12,2)} / {@code PIC S9(10)V9(2)} holds. */
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");

    @Autowired
    private AuthFraudRepository authFraudRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void anAmountAtTheTopOfTheDecimal12Domain() {
        AuthFraudId id = save(row -> {
            row.setTransactionAmt(MAX_AMOUNT);
            row.setApprovedAmt(MAX_AMOUNT.negate());
        });

        AuthFraudRecord read = reload(id);
        assertThat(read.getTransactionAmt()).isEqualByComparingTo(MAX_AMOUNT);
        assertThat(read.getApprovedAmt()).isEqualByComparingTo(MAX_AMOUNT.negate());
    }

    @Test
    void theCenturyOfTheReportDateSurvivesTheRoundTrip() {
        AuthFraudId id = save(row -> row.setFraudRptDate(LocalDate.of(1999, 12, 31)));

        assertThat(reload(id).getFraudRptDate()).isEqualTo(LocalDate.of(1999, 12, 31));
    }

    @Test
    void reportDatesOrderChronologicallyAcrossTheCentury() {
        save("9680294154603691", row -> row.setFraudRptDate(LocalDate.of(2001, 1, 1)));
        save("9680294154603692", row -> row.setFraudRptDate(LocalDate.of(1999, 12, 31)));
        entityManager.flush();
        entityManager.clear();

        List<LocalDate> dates = entityManager.createQuery(
                        "select r.fraudRptDate from AuthFraudRecord r "
                                + "where r.id.cardNum in :cards order by r.fraudRptDate",
                        LocalDate.class)
                .setParameter("cards", List.of("9680294154603691", "9680294154603692"))
                .getResultList();

        assertThat(dates).containsExactly(LocalDate.of(1999, 12, 31), LocalDate.of(2001, 1, 1));
    }

    @Test
    void thePosEntryModeIsComparedNumerically() {
        AuthFraudId id = save(row -> row.setPosEntryMode(812));

        assertThat(reload(id).getPosEntryMode()).isEqualTo(812);
        assertThat(entityManager.createQuery(
                        "select count(r) from AuthFraudRecord r "
                                + "where r.posEntryMode > :mode and r.id.cardNum = :card",
                        Long.class)
                .setParameter("mode", 90)
                .setParameter("card", id.getCardNum())
                .getSingleResult()).isEqualTo(1L);
    }

    @Test
    void theMerchantNameReadsBackWithoutPadding() {
        AuthFraudId id = save(row -> row.setMerchantName("ACME SUPERMARKET"));

        assertThat(reload(id).getMerchantName()).isEqualTo("ACME SUPERMARKET");
    }

    private AuthFraudId save(java.util.function.Consumer<AuthFraudRecord> customise) {
        return save("9680294154603697", customise);
    }

    private AuthFraudId save(String cardNum, java.util.function.Consumer<AuthFraudRecord> customise) {
        AuthFraudId id = new AuthFraudId(cardNum, LocalDateTime.of(2025, 1, 15, 15, 0, 0));
        AuthFraudRecord row = new AuthFraudRecord();
        row.setId(id);
        row.setAuthFraud("F");
        row.setAcctId(1L);
        row.setCustId(1L);
        customise.accept(row);
        authFraudRepository.save(row);
        return id;
    }

    private AuthFraudRecord reload(AuthFraudId id) {
        entityManager.flush();
        entityManager.clear();
        return authFraudRepository.findById(id).orElseThrow();
    }
}
