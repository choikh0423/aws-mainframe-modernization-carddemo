package com.carddemo.batch;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchJobLauncherTest {

    @Test
    void optionAndPlainKeyValueArgumentsBecomeJobParameters() throws Exception {
        Job job = mock(Job.class);
        when(job.getName()).thenReturn("CREASTMT");
        JobLauncher launcher = mock(JobLauncher.class);
        JobExecution execution = new JobExecution(1L);
        execution.setStatus(BatchStatus.COMPLETED);
        when(launcher.run(eq(job), any())).thenReturn(execution);

        BatchJobLauncher runner = new BatchJobLauncher(launcher, List.of(job), "CREASTMT");
        runner.run(new DefaultApplicationArguments(
                "--spring.batch.job.name=CREASTMT", "--spring.main.web-application-type=none",
                "cycle=2026-10", "--run=7", "not-a-parameter"));

        ArgumentCaptor<JobParameters> parameters = ArgumentCaptor.forClass(JobParameters.class);
        verify(launcher).run(eq(job), parameters.capture());
        assertThat(parameters.getValue().getParameters()).containsOnlyKeys("cycle", "run");
        assertThat(parameters.getValue().getString("cycle")).isEqualTo("2026-10");
        assertThat(parameters.getValue().getString("run")).isEqualTo("7");
        assertThat(runner.getExitCode()).isZero();
    }

    @Test
    void jobWithAnIncrementerGetsTheNextRunIdButNotThePreviousArguments() throws Exception {
        Job job = mock(Job.class);
        when(job.getName()).thenReturn("CREASTMT");
        when(job.getJobParametersIncrementer()).thenReturn(new RunIdIncrementer());
        JobInstance lastInstance = new JobInstance(4L, "CREASTMT");
        JobExplorer explorer = mock(JobExplorer.class);
        when(explorer.getJobInstances("CREASTMT", 0, 1)).thenReturn(List.of(lastInstance));
        when(explorer.getLastJobExecution(lastInstance)).thenReturn(new JobExecution(lastInstance, 9L,
                new JobParametersBuilder().addLong("run.id", 4L).addString("cycle", "2026-09")
                        .toJobParameters()));
        JobLauncher launcher = mock(JobLauncher.class);
        JobExecution execution = new JobExecution(10L);
        execution.setStatus(BatchStatus.COMPLETED);
        when(launcher.run(eq(job), any())).thenReturn(execution);

        new BatchJobLauncher(launcher, List.of(job), "CREASTMT", explorer)
                .run(new DefaultApplicationArguments("--spring.batch.job.name=CREASTMT"));

        ArgumentCaptor<JobParameters> parameters = ArgumentCaptor.forClass(JobParameters.class);
        verify(launcher).run(eq(job), parameters.capture());
        assertThat(parameters.getValue().getParameters()).containsOnlyKeys("run.id");
        assertThat(parameters.getValue().getLong("run.id")).isEqualTo(5L);
    }
}
