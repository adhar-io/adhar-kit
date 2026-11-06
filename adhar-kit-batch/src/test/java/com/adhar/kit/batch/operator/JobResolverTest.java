package com.adhar.kit.batch.operator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JobResolver#of}: by bean name first, then by job name.
 */
class JobResolverTest {

    private final DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
    private final JobResolver resolver = JobResolver.of(beans);

    private Job job(String jobName) {
        Job job = mock(Job.class);
        when(job.getName()).thenReturn(jobName);
        return job;
    }

    @Test
    @DisplayName("resolves a job by its bean name")
    void byBeanName() {
        Job job = mock(Job.class);
        beans.registerSingleton("importJobBean", job);

        assertThat(resolver.resolve("importJobBean")).isSameAs(job);
    }

    @Test
    @DisplayName("resolves a job by its job name when no bean has that name")
    void byJobName() {
        Job job = job("importJob");
        beans.registerSingleton("importJobBean", job);

        assertThat(resolver.resolve("importJob")).isSameAs(job);
    }

    @Test
    @DisplayName("the bean name wins over another job's job name")
    void beanNameFirst() {
        Job byBean = mock(Job.class);
        Job byName = mock(Job.class);
        beans.registerSingleton("nightly", byBean);
        beans.registerSingleton("other", byName);

        assertThat(resolver.resolve("nightly")).isSameAs(byBean);
    }

    @Test
    @DisplayName("a bean of the same name that is not a job does not hide the job")
    void beanOfAnotherType() {
        Job job = job("nightly");
        beans.registerSingleton("nightly", "not a job");
        beans.registerSingleton("nightlyJobBean", job);

        assertThat(resolver.resolve("nightly")).isSameAs(job);
    }

    @Test
    @DisplayName("an unknown name is refused, listing the application's jobs sorted")
    void unknownName() {
        beans.registerSingleton("zJob", job("z"));
        beans.registerSingleton("aJob", job("a"));

        assertThatThrownBy(() -> resolver.resolve("ghost"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No Job named [ghost]; jobs in this application: [aJob, zJob]");
    }

    @Test
    @DisplayName("an application without jobs says so")
    void noJobs() {
        assertThatThrownBy(() -> resolver.resolve("ghost"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No Job named [ghost]; jobs in this application: []");
    }
}
