package com.carddemo.statements;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/** Without {@code carddemo.statements.seed-on-startup=true} no seed bean exists and nothing is archived. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=jdbc:h2:mem:carddemo-statement-seed-off;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
class StatementDemoSeedDefaultOffTest {

    @Autowired
    private ApplicationContext context;
    @Autowired
    private StatementArchiveRepository archive;

    @Test
    void isOffUnlessTheProfileTurnsItOn() {
        assertThat(context.getBeansOfType(StatementDemoSeed.class)).isEmpty();
        assertThat(archive.count()).isZero();
    }
}
