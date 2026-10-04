package com.gokulsweets.restaurant.security;
import com.gokulsweets.restaurant.staff.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.hikari.maximum-pool-size=3","spring.datasource.hikari.minimum-idle=0"})
@Import(StaffSessionLifetimeIntegrationTest.TimeConfiguration.class)
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class StaffSessionLifetimeIntegrationTest {
 @Autowired StaffSessionService sessions;
 @Autowired JdbcTemplate jdbc;
 @Autowired MutableClock clock;
 @TestConfiguration static class TimeConfiguration {
  @Bean @Primary MutableClock sessionTestClock(){return new MutableClock();}
 }
 static final class MutableClock extends Clock {
  private Instant now=Instant.parse("2026-10-05T05:30:00Z");
  @Override public ZoneId getZone(){return ZoneId.of("Asia/Kolkata");}
  @Override public Clock withZone(ZoneId zone){return Clock.fixed(now,zone);}
  @Override public Instant instant(){return now;}
  void set(Instant value){now=value;}
 }
 Object originalKey;
 @BeforeEach void setup(){originalKey=ReflectionTestUtils.getField(sessions,"encryptionKey");ReflectionTestUtils.setField(sessions,"encryptionKey",Base64.getEncoder().encodeToString(new byte[32]));time(Instant.parse("2026-10-05T05:30:00Z"));}
 @AfterEach void restore(){ReflectionTestUtils.setField(sessions,"encryptionKey",originalKey);}
 void time(Instant now){clock.set(now);}
 StaffUser user(String roleName){
  long roleId=jdbc.queryForObject("SELECT id FROM roles WHERE name=?",Long.class,roleName);
  var user=new StaffUser();user.setId(jdbc.queryForObject("INSERT INTO staff_users(username,password_hash,full_name,role_id) VALUES (?,'test','Session test',?) RETURNING id",Long.class,"session-"+UUID.randomUUID(),roleId));
  var role=new Role();role.setName(roleName);user.setRole(role);
  user.setUpdatedAt(jdbc.queryForObject("SELECT updated_at FROM staff_users WHERE id=?",(r,n)->r.getTimestamp(1).toLocalDateTime(),user.getId()));return user;
 }
 @Test void staffSurvivesRefreshAndRenewsWithoutChangingPushSessionIdentity(){
  var user=user("KITCHEN_STAFF");var signIn=sessions.issue(user);var hash=StaffSessionService.hash(signIn.token());
  Instant expires=jdbc.queryForObject("SELECT expires_at FROM staff_sessions WHERE token_hash=?",(r,n)->r.getTimestamp(1).toInstant(),hash);
  assertThat(expires).isEqualTo(clock.instant().plus(Duration.ofDays(365)));
  time(clock.instant().plus(Duration.ofDays(364)));var verified=sessions.verify(signIn.token());assertThat(verified).isNotNull();
  assertThat(sessions.renewStaff(verified,signIn.token(),user)).isTrue();
  time(clock.instant().plus(Duration.ofDays(2)));assertThat(sessions.verify(signIn.token())).isNotNull();
  assertThat(jdbc.queryForObject("SELECT count(*) FROM staff_sessions WHERE token_hash=?",Long.class,hash)).isEqualTo(1);
  sessions.revoke(signIn.token());assertThat(sessions.renewStaff(verified,signIn.token(),user)).isFalse();assertThat(sessions.verify(signIn.token())).isNull();
 }
 @Test void administratorHasAnAbsoluteFifteenHourExpiry(){
  var user=user("OWNER_ADMIN");var signIn=sessions.issue(user);var start=clock.instant();
  time(start.plus(Duration.ofHours(14)));var verified=sessions.verify(signIn.token());assertThat(verified).isNotNull();assertThat(sessions.renewStaff(verified,signIn.token(),user)).isFalse();
  time(start.plus(Duration.ofHours(15)));assertThat(sessions.verify(signIn.token())).isNull();
 }
 @Test void disabledChangedAndExpiredStaffSessionsCannotBeResurrected(){
  var user=user("COUNTER_STAFF");var signIn=sessions.issue(user);var verified=sessions.verify(signIn.token());
  jdbc.update("UPDATE staff_users SET active=false WHERE id=?",user.getId());assertThat(sessions.verify(signIn.token())).isNull();assertThat(sessions.renewStaff(verified,signIn.token(),user)).isFalse();
  jdbc.update("UPDATE staff_users SET active=true,updated_at=updated_at+INTERVAL '1 second' WHERE id=?",user.getId());assertThat(sessions.verify(signIn.token())).isNull();assertThat(sessions.renewStaff(verified,signIn.token(),user)).isFalse();
  time(clock.instant().plus(Duration.ofDays(366)));assertThat(sessions.renewStaff(verified,signIn.token(),user)).isFalse();
 }
}
