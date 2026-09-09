package com.carddemo.batch.filereads;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** FR-G8: the shared 9910-DISPLAY-IO-STATUS rendering. */
class IoStatusFormatterTest {

    @Test
    void printsANumericStatusAsFourDigits() {
        assertThat(IoStatusFormatter.line("35")).isEqualTo("FILE STATUS IS: NNNN0035");
        assertThat(IoStatusFormatter.line("10")).isEqualTo("FILE STATUS IS: NNNN0010");
        assertThat(IoStatusFormatter.line("00")).isEqualTo("FILE STATUS IS: NNNN0000");
    }

    @Test
    void printsTheBinaryValueOfTheSecondByteForA9xStatus() {
        assertThat(IoStatusFormatter.format("9\u0001")).isEqualTo("9001");
        assertThat(IoStatusFormatter.format("9P")).isEqualTo("9080");
    }

    @Test
    void printsTheBinaryValueOfTheSecondByteForANonNumericStatus() {
        assertThat(IoStatusFormatter.format("A0")).isEqualTo("A048");
    }
}
