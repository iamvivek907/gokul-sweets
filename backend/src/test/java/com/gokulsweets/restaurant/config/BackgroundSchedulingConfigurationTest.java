package com.gokulsweets.restaurant.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

class BackgroundSchedulingConfigurationTest {
    private final ApplicationContextRunner context =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            BackgroundSchedulingConfiguration.class,
                            SchedulingConfig.class,
                            com.gokulsweets.restaurant.inventory.config.InventoryConfiguration
                                    .class)
                    .withBean(
                            ApplicationClock.class,
                            () -> new ApplicationClock(new EnhancementProperties()));

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

    @Test
    void apiRoleDoesNotRegisterSchedulersFromLegacyConfigurations() {
        context.withPropertyValues("gokul.jobs.role=api")
                .run(
                        c ->
                                assertThat(c)
                                        .doesNotHaveBean(
                                                ScheduledAnnotationBeanPostProcessor.class));
    }

    @Test
    void workerAndCombinedRolesRegisterOneSchedulerAndRespectExplicitDisable() {
        for (String role : new String[] {"worker", "combined"}) {
            context.withPropertyValues("gokul.jobs.role=" + role)
                    .run(
                            c ->
                                    assertThat(c)
                                            .hasSingleBean(
                                                    ScheduledAnnotationBeanPostProcessor.class));
            context.withPropertyValues(
                            "gokul.jobs.role=" + role, "gokul.jobs.scheduling-enabled=false")
                    .run(
                            c ->
                                    assertThat(c)
                                            .doesNotHaveBean(
                                                    ScheduledAnnotationBeanPostProcessor.class));
        }
    }

    @Test
    void invalidActiveRoleFailsStartupRatherThanRunningUnintendedJobs() {
        context.withPropertyValues("gokul.jobs.role=wroker").run(c -> assertThat(c).hasFailed());
    }
}
