package com.carddemo.batch.operations;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B-15: the CICS file quiescing jobs are removed, not migrated. This is the
 * executable half of that decision - nothing in the target may reintroduce an
 * OPENFIL/CLOSEFIL equivalent.
 *
 * <p>Covers FR-OC-20.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:carddemo-waitstep;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
class FileControlJobsRemovedTest {

    @Autowired
    private List<Job> jobs;

    @Test
    void noFileOpenOrCloseJobExistsInTheTarget() {
        assertThat(jobs)
                .extracting(Job::getName)
                .doesNotContain("OPENFIL", "CLOSEFIL", "CLOSEFIL1", "CLOSEFIL2");
    }
}
