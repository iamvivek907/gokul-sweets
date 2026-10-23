package com.gokulsweets.restaurant.config;

import com.gokulsweets.restaurant.observability.MethodTiming;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Explicit whole-process scheduling control for idle DEV deployments. Production defaults ON. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
        name = "gokul.jobs.scheduling-enabled",
        havingValue = "true",
        matchIfMissing = true)
@Conditional(BackgroundSchedulingConfiguration.JobRoleCondition.class)
public class BackgroundSchedulingConfiguration {
    /** Combined is backward compatible; API and worker processes use separate resources. */
    public enum Role {
        COMBINED,
        API,
        WORKER
    }

    /** One registration point for every scheduled callback, with validated process roles. */
    public static class JobRoleCondition extends SpringBootCondition {
        /** Disables scheduling only on explicit API processes; invalid roles fail startup. */
        @Override
        public ConditionOutcome getMatchOutcome(
                ConditionContext context, AnnotatedTypeMetadata metadata) {
            long started =
                    MethodTiming.start(
                            BackgroundSchedulingConfiguration.JobRoleCondition.class,
                            "getMatchOutcome(ConditionContext,AnnotatedTypeMetadata)");
            try {
                Role role =
                        Binder.get(context.getEnvironment())
                                .bind("gokul.jobs.role", Role.class)
                                .orElse(Role.COMBINED);
                return role == Role.API
                        ? ConditionOutcome.noMatch(
                                "API process delegates scheduled callbacks to worker")
                        : ConditionOutcome.match(
                                "Combined or worker process owns scheduled callbacks");
            } finally {
                MethodTiming.finish(
                        started,
                        BackgroundSchedulingConfiguration.JobRoleCondition.class,
                        "getMatchOutcome(ConditionContext,AnnotatedTypeMetadata)");
            }
        }
    }
}
