package com.gokulsweets.restaurant.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class MethodTimingTest {
    private Logger logger;
    private Level previousLevel;
    private ListAppender<ILoggingEvent> events;

    @BeforeEach
    void setup() {
        logger = (Logger) LoggerFactory.getLogger(MethodTiming.class);
        previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
    }

    @AfterEach
    void cleanup() {
        logger.detachAppender(events);
        logger.setLevel(previousLevel);
        MethodTiming.configure(true, 1000);
    }

    private java.util.List<ILoggingEvent> capturedEvents() {
        return events.list.stream()
                .filter(
                        event ->
                                event.getArgumentArray() != null
                                        && event.getArgumentArray().length > 0
                                        && getClass().getName().equals(event.getArgumentArray()[0]))
                .toList();
    }

    @Test
    void disabledTimingDoesNotEmitEvents() {
        MethodTiming.configure(false, 0);
        long started = MethodTiming.start(getClass(), "disabled()");
        MethodTiming.finish(started, getClass(), "disabled()");
        assertThat(started).isZero();
        assertThat(capturedEvents()).isEmpty();
    }

    @Test
    void slowTimingUsesWarnAndOnlyMethodMetadata() {
        MethodTiming.configure(true, 0);
        long started = MethodTiming.start(getClass(), "checkout(String)");
        MethodTiming.finish(started, getClass(), "checkout(String)");
        assertThat(capturedEvents()).hasSize(1);
        var event = capturedEvents().getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage())
                .contains("method_slow", "checkout(String)", "durationMs=");
        assertThat(event.getArgumentArray()).hasSize(3);
        assertThat(event.getThrowableProxy()).isNull();
    }

    @Test
    void fastTimingUsesDebugWithoutRequiringWarn() {
        MethodTiming.configure(true, Long.MAX_VALUE);
        MethodTiming.finish(MethodTiming.start(getClass(), "lookup()"), getClass(), "lookup()");
        assertThat(capturedEvents()).hasSize(1);
        assertThat(capturedEvents().getFirst().getLevel()).isEqualTo(Level.DEBUG);
    }

    @Test
    void timingFinallyPreservesTheOriginalReturnAndException() {
        MethodTiming.configure(true, Long.MAX_VALUE);
        assertThat(returnValue()).isEqualTo(17);
        var expected = new IllegalStateException("test-only exception");
        try {
            throwValue(expected);
            throw new AssertionError("Expected exception");
        } catch (IllegalStateException actual) {
            assertThat(actual).isSameAs(expected);
        }
        assertThat(capturedEvents()).hasSize(2);
        assertThat(capturedEvents())
                .allSatisfy(
                        e ->
                                assertThat(e.getFormattedMessage())
                                        .doesNotContain(expected.getMessage()));
    }

    private int returnValue() {
        long started = MethodTiming.start(getClass(), "returnValue()");
        try {
            return 17;
        } finally {
            MethodTiming.finish(started, getClass(), "returnValue()");
        }
    }

    private void throwValue(IllegalStateException expected) {
        long started = MethodTiming.start(getClass(), "throwValue(IllegalStateException)");
        try {
            throw expected;
        } finally {
            MethodTiming.finish(started, getClass(), "throwValue(IllegalStateException)");
        }
    }
}
