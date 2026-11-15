package com.gokulsweets.restaurant.security;

import static org.assertj.core.api.Assertions.*;

import com.gokulsweets.restaurant.staff.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.*;
import java.util.*;

@SpringBootTest(
        properties = {
            "spring.datasource.hikari.maximum-pool-size=3",
            "spring.datasource.hikari.minimum-idle=0"
        })
@Import(StaffSessionLifetimeIntegrationTest.TimeConfiguration.class)
@org.springframework.test.annotation.DirtiesContext(
        classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class StaffSessionLifetimeIntegrationTest {
    @Autowired StaffSessionService sessions;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;

    @TestConfiguration
    static class TimeConfiguration {
        @Bean
        @Primary
        MutableClock sessionTestClock() {
            return new MutableClock();
        }
    }

    static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-05T05:30:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Kolkata");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }

        void set(Instant value) {
            now = value;
        }
    }

    Object originalKey;

    @BeforeEach
    void setup() {
        originalKey = ReflectionTestUtils.getField(sessions, "encryptionKey");
        ReflectionTestUtils.setField(
                sessions, "encryptionKey", Base64.getEncoder().encodeToString(new byte[32]));
        time(Instant.parse("2026-10-05T05:30:00Z"));
    }

    @AfterEach
    void restore() {
        ReflectionTestUtils.setField(sessions, "encryptionKey", originalKey);
    }

    void time(Instant now) {
        clock.set(now);
    }

    StaffUser user(String roleName) {
        long roleId =
                jdbc.queryForObject("SELECT id FROM roles WHERE name=?", Long.class, roleName);
        var user = new StaffUser();
        user.setId(
                jdbc.queryForObject(
                        "INSERT INTO staff_users(username,password_hash,full_name,role_id) VALUES"
                                + " (?,'test','Session test',?) RETURNING id",
                        Long.class,
                        "session-" + UUID.randomUUID(),
                        roleId));
        var role = new Role();
        role.setName(roleName);
        user.setRole(role);
        user.setUpdatedAt(
                jdbc.queryForObject(
                        "SELECT updated_at FROM staff_users WHERE id=?",
                        (r, n) -> r.getTimestamp(1).toLocalDateTime(),
                        user.getId()));
        return user;
    }

    @Test
    void staffAndAdminRemainSignedInAndRefreshCookiesAtMostDaily() {
        for (String role : java.util.List.of("KITCHEN_STAFF", "OWNER_ADMIN")) {
            var user = user(role);
            var login = sessions.issue(user);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT expires_at = TIMESTAMPTZ 'infinity' FROM staff_sessions"
                                            + " WHERE token_hash=?",
                                    Boolean.class,
                                    StaffSessionService.hash(login.token())))
                    .isTrue();
            time(clock.instant().plus(Duration.ofDays(500)));
            var verified = sessions.verify(login.token());
            assertThat(verified).isNotNull();
            assertThat(sessions.refreshCookie(verified, login.token())).isTrue();
            assertThat(sessions.refreshCookie(verified, login.token())).isFalse();
            sessions.revoke(login.token());
            assertThat(sessions.refreshCookie(verified, login.token())).isFalse();
            assertThat(sessions.verify(login.token())).isNull();
        }
    }

    @Test
    void profileChangesKeepAllDevicesSignedInButPasswordResetAndDeactivationRevokeThem() {
        var user = user("COUNTER_STAFF");
        var first = sessions.issue(user);
        var second = sessions.issue(user);
        jdbc.update(
                "UPDATE staff_users SET full_name='Updated',updated_at=updated_at+INTERVAL '1"
                        + " second' WHERE id=?",
                user.getId());
        assertThat(sessions.verify(first.token())).isNotNull();
        assertThat(sessions.verify(second.token())).isNotNull();
        sessions.revoke(first.token());
        assertThat(sessions.verify(first.token())).isNull();
        assertThat(sessions.verify(second.token())).isNotNull();
        jdbc.update("UPDATE staff_users SET password_hash='updated' WHERE id=?", user.getId());
        assertThat(sessions.verify(second.token())).isNull();
        user.setUpdatedAt(
                jdbc.queryForObject(
                        "SELECT updated_at FROM staff_users WHERE id=?",
                        java.time.LocalDateTime.class,
                        user.getId()));
        var third = sessions.issue(user);
        jdbc.update("UPDATE staff_users SET active=false WHERE id=?", user.getId());
        jdbc.update("UPDATE staff_users SET active=true WHERE id=?", user.getId());
        assertThat(sessions.verify(third.token())).isNull();
    }
}
