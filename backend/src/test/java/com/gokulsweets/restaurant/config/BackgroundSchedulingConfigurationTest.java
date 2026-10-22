package com.gokulsweets.restaurant.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

class BackgroundSchedulingConfigurationTest {
    private final ApplicationContextRunner context =
            new ApplicationContextRunner()
                    .withUserConfiguration(BackgroundSchedulingConfiguration.class);

    @Test
    void existingDeploymentsKeepAllSchedulingEnabledByDefault() {
        context.run(c -> assertThat(c).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
    }

    @Test
    void explicitDevIdleModeDoesNotRegisterAnyScheduledCallbacks() {
        context.withPropertyValues("gokul.jobs.scheduling-enabled=false")
                .run(
                        c ->
                                assertThat(c)
                                        .doesNotHaveBean(
                                                ScheduledAnnotationBeanPostProcessor.class));
    }
}
