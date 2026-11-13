package com.gokulsweets.restaurant.inventory.rolling;

import com.gokulsweets.restaurant.observability.MethodTiming;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Isolates long rolling coverage runs from shared scheduled callbacks. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "inventory.rolling",
        name = "scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true)
class RollingScheduleExecutionConfiguration {

    /** Runs one daily batch at a time and rejects submissions while a batch is in progress. */
    @Bean(defaultCandidate = false)
    ThreadPoolTaskExecutor rollingScheduleExecutor() {
        final long started =
                MethodTiming.start(
                        RollingScheduleExecutionConfiguration.class, "rollingScheduleExecutor()");
        try {
            var executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(0);
            executor.setThreadNamePrefix("rolling-automation-");
            executor.setStrictEarlyShutdown(true);
            return executor;
        } finally {
            MethodTiming.finish(
                    started,
                    RollingScheduleExecutionConfiguration.class,
                    "rollingScheduleExecutor()");
        }
    }
}
