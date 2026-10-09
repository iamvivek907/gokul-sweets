package com.gokulsweets.restaurant;

import com.gokulsweets.restaurant.observability.MethodTiming;

import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.boot.context.metrics.buffering.StartupTimeline.TimelineEvent;
import org.springframework.context.ApplicationListener;

import java.util.Comparator;
import java.util.List;

/** Opt-in measurements for slow cold starts; no public diagnostics endpoint or tag values. */
final class StartupDiagnostics {

    /** Creates a startup diagnostics instance. */
    private StartupDiagnostics() {}

    /**
     * When enabled, buffers startup steps and logs readiness plus the twenty slowest recorded steps
     * after the application becomes ready.
     *
     * @param application the application supplied to this method
     * @param enabled the enabled supplied to this method
     */
    static void configure(SpringApplication application, boolean enabled) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StartupDiagnostics.class, "configure(SpringApplication,boolean)");
        try {
            if (!enabled) return;
            var startup = new BufferingApplicationStartup(4096);
            application.setApplicationStartup(startup);
            application.addListeners(
                    (ApplicationListener<ApplicationReadyEvent>)
                            event -> {
                                var log = LoggerFactory.getLogger(StartupDiagnostics.class);
                                var events = startup.drainBufferedTimeline().getEvents();
                                log.info(
                                        "Startup diagnostics: readyMs={}, recordedSteps={}; nested"
                                                + " durations overlap",
                                        event.getTimeTaken() == null
                                                ? null
                                                : event.getTimeTaken().toMillis(),
                                        events.size());
                                for (var step : slowest(events)) {
                                    String bean = "";
                                    for (var tag : step.getStartupStep().getTags()) {
                                        if ("beanName".equals(tag.getKey())) bean = tag.getValue();
                                    }
                                    log.info(
                                            "Startup step: name={}, bean={}, durationMs={}",
                                            step.getStartupStep().getName(),
                                            bean,
                                            step.getDuration().toMillis());
                                }
                            });
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StartupDiagnostics.class,
                    "configure(SpringApplication,boolean)");
        }
    }

    /**
     * Returns at most twenty startup events ordered by descending elapsed duration; nested event
     * durations can overlap.
     *
     * @param events the events supplied to this method
     * @return the value of {@code
     *     events.stream().sorted(Comparator.comparing(TimelineEvent::getDuration).reversed()).limit(20).toList()}
     */
    static List<TimelineEvent> slowest(List<TimelineEvent> events) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StartupDiagnostics.class, "slowest(List<TimelineEvent>)");
        try {
            return events.stream()
                    .sorted(Comparator.comparing(TimelineEvent::getDuration).reversed())
                    .limit(20)
                    .toList();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StartupDiagnostics.class,
                    "slowest(List<TimelineEvent>)");
        }
    }
}
