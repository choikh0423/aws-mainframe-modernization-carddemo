package com.carddemo.reporting.adapter;

import com.carddemo.reporting.port.TransactionReportJobLauncher;
import com.carddemo.reporting.port.TransactionReportJobRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-R38 — the documented no-op binding of the S-07 → S-14 seam: it accepts the
 * request, records it, reports success, and steps aside the moment stream S-14
 * contributes a real launcher.
 */
class NoOpTransactionReportJobLauncherTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(NoOpTransactionReportJobLauncher.class));

    @Test
    @DisplayName("the port is bound and a launch succeeds while TRANREPT is not migrated")
    void bindsThePortAndSucceeds() {
        contextRunner.run(context -> {
            TransactionReportJobLauncher launcher =
                    context.getBean(TransactionReportJobLauncher.class);

            assertThat(launcher)
                    .isInstanceOf(NoOpTransactionReportJobLauncher.RecordingLauncher.class);

            launcher.launch(new TransactionReportJobRequest("Monthly",
                    LocalDate.of(2023, 7, 1), LocalDate.of(2023, 7, 31)));

            assertThat(((NoOpTransactionReportJobLauncher.RecordingLauncher) launcher).getSubmitted())
                    .singleElement()
                    .satisfies(request ->
                            assertThat(request.getStartDateText()).isEqualTo("2023-07-01"));
        });
    }

    @Test
    @DisplayName("stream S-14's launcher replaces it with no change to this package")
    void realLauncherWins() {
        contextRunner.withUserConfiguration(RealLauncherConfiguration.class).run(context -> {
            assertThat(context.getBeansOfType(TransactionReportJobLauncher.class)).hasSize(1);
            assertThat(context.getBean(TransactionReportJobLauncher.class))
                    .isNotInstanceOf(NoOpTransactionReportJobLauncher.RecordingLauncher.class);
        });
    }

    /** Stands in for the S-14 job launcher in {@code com.carddemo.batch.tranreport}. */
    @Configuration
    static class RealLauncherConfiguration {

        @Bean
        TransactionReportJobLauncher tranReportJobLauncher() {
            return request -> { };
        }
    }
}
