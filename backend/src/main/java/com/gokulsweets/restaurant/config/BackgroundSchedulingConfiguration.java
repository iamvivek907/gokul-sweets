package com.gokulsweets.restaurant.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Explicit whole-process scheduling control for idle DEV deployments. Production defaults ON. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
        name = "gokul.jobs.scheduling-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class BackgroundSchedulingConfiguration {}
