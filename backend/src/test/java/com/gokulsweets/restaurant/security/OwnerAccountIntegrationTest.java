package com.gokulsweets.restaurant.security;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Real PostgreSQL with real BCrypt/MFA, in a disposable schema only. */
@EnabledIfEnvironmentVariable(named="DATABASE_URL",matches="jdbc:postgresql:.*")
class OwnerAccountIntegrationTest {
    private JdbcTemplate jdbc,root;
    private DriverManagerDataSource dataSource;
    private OwnerAccountService service;
    private MockEnvironment env;
    private EnhancementProperties flags;
    private String schema;
    private final String setupKey="S".repeat(43),password="Owner-password-2026";
    private final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder(4);
    @BeforeEach void setup() throws Exception {
        String url=System.getenv("DATABASE_URL"),user=System.getenv("DATABASE_USERNAME"),pass=System.getenv("DATABASE_PASSWORD");
        root=new JdbcTemplate(new DriverManagerDataSource(url,user,pass));
        schema="owner_test_"+UUID.randomUUID().toString().replace("-","");root.execute("CREATE SCHEMA "+schema);
        dataSource=new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,user,pass);
        jdbc=new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE branches(id bigint PRIMARY KEY)");jdbc.execute("INSERT INTO branches VALUES (1)");
        migration("V18__create_staff_security.sql");migration("V75__staff_sessions_and_mfa.sql");
        jdbc.execute("INSERT INTO roles(name) VALUES ('OWNER_ADMIN'),('BRANCH_MANAGER')");
        migration("V121__first_owner_setup.sql");
        flags=new EnhancementProperties();flags.setSecureStaffSessions(true);
        env=new MockEnvironment().withProperty("gokul.environment-isolation.environment","DEV")
            .withProperty("staff.owner-setup.enabled","true").withProperty("staff.owner-setup.key-hash",StaffSessionService.hash(setupKey));
        service=createService();
    }
    @AfterEach void tearDown(){if(root!=null&&schema!=null)root.execute("DROP SCHEMA "+schema+" CASCADE");}
    private void migration(String name) throws Exception {jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/"+name)));}
    private StaffMfaService mfa() {
        var mfa=new StaffMfaService(flags,jdbc,Clock.fixed(Instant.ofEpochSecond(59),ZoneOffset.UTC));
        ReflectionTestUtils.setField(mfa,"encryptionKey",Base64.getEncoder().encodeToString(new byte[32]));return mfa;
    }
    private OwnerAccountService createService(){return new OwnerAccountService(jdbc,env,flags,passwords,mfa(),new DataSourceTransactionManager(dataSource));}
    private long user(String name,String role,boolean active) {
        return jdbc.queryForObject("INSERT INTO staff_users(username,password_hash,full_name,role_id,active) SELECT ?,?,'Existing staff',id,? FROM roles WHERE name=? RETURNING id",Long.class,name,passwords.encode(password),active,role);
    }
    private long id(String name){return jdbc.queryForObject("SELECT id FROM staff_users WHERE username=?",Long.class,name);}
    private void enroll(long id) {
        String encrypted=ReflectionTestUtils.invokeMethod(mfa(),"encrypt","12345678901234567890".getBytes(StandardCharsets.US_ASCII));
        jdbc.update("INSERT INTO staff_mfa(staff_id,secret_ciphertext) VALUES (?,?)",id,encrypted);
        jdbc.update("INSERT INTO staff_mfa_recovery(staff_id,code_hash) VALUES (?,?)",id,StaffSessionService.hash("saved-mfa-code"));
    }
    private void session(long id) {
        jdbc.update("INSERT INTO staff_sessions(token_hash,staff_id,csrf_hash,staff_updated_at,expires_at) SELECT ?,id,?,updated_at,CURRENT_TIMESTAMP+INTERVAL '1 hour' FROM staff_users WHERE id=?",StaffSessionService.hash("session"+id),StaffSessionService.hash("csrf"),id);
        jdbc.update("INSERT INTO staff_mfa_enrollments(token_hash,staff_id,expires_at) VALUES (?,?,CURRENT_TIMESTAMP+INTERVAL '1 hour')",StaffSessionService.hash("pending"+id),id);
    }
    private void rejected(int status,Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(ResponseStatusException.class)
            .satisfies(error->assertThat(((ResponseStatusException)error).getStatusCode().value()).isEqualTo(status));
    }
    @Test void disabledMisconfiguredAndLegacySessionsCannotEnableSetup() {
        env.setProperty("staff.owner-setup.enabled","false");assertThat(service.status().available()).isFalse();
        rejected(404,()->service.setup(setupKey,"first",password,"Owner"));
        env.setProperty("staff.owner-setup.enabled","true");env.setProperty("staff.owner-setup.key-hash","invalid");
        assertThat(service.status().available()).isFalse();
        env.setProperty("staff.owner-setup.key-hash",StaffSessionService.hash(setupKey));flags.setSecureStaffSessions(false);
        assertThat(service.status().available()).isFalse();rejected(404,()->service.recover(setupKey,"first",password));
        flags.setSecureStaffSessions(true);env.setProperty("gokul.environment-isolation.environment","UNKNOWN");
        assertThat(service.status().available()).isFalse();rejected(404,()->service.account(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM staff_users",Long.class)).isZero();
    }
    @Test void firstSetupStoresOnlyHashesAndClosesPermanentlyEvenAfterDeletion() {
        long staff=user("cashier","BRANCH_MANAGER",true);
        var before=jdbc.queryForMap("SELECT * FROM staff_users WHERE id=?",staff);
        rejected(403,()->service.setup("W".repeat(43),"first",password,"Owner"));
        rejected(400,()->service.setup(setupKey,"first","😀".repeat(20),"Owner"));
        assertThat(service.status().available()).isTrue();
        var result=service.setup(setupKey," First.Owner ",password,"First Owner");long id=id("first.owner");
        assertThat(result.username()).isEqualTo("first.owner");assertThat(result.recoveryKey()).matches("[A-Za-z0-9_-]{43}");
        assertThat(jdbc.queryForObject("SELECT key_hash FROM staff_owner_recovery WHERE staff_id=?",String.class,id)).isEqualTo(StaffSessionService.hash(result.recoveryKey()));
        assertThat(passwords.matches(password,jdbc.queryForObject("SELECT password_hash FROM staff_users WHERE id=?",String.class,id))).isTrue();
        assertThat(jdbc.queryForMap("SELECT * FROM staff_users WHERE id=?",staff)).isEqualTo(before);
        assertThat(service.status().available()).isFalse();rejected(409,()->service.setup(setupKey,"second",password,"Second"));
        jdbc.update("DELETE FROM staff_users WHERE id=?",id);
        assertThat(service.status().available()).isFalse();rejected(409,()->service.setup(setupKey,"second",password,"Second"));
    }
    @Test void anExistingInactiveOwnerClosesLatchWithoutChangingAnyAccount() {
        long owner=user("existing-owner","OWNER_ADMIN",false);var before=jdbc.queryForMap("SELECT * FROM staff_users WHERE id=?",owner);
        // The POST itself must commit closure even when it returns 409.
        rejected(409,()->service.setup(setupKey,"first",password,"Owner"));
        assertThat(jdbc.queryForMap("SELECT * FROM staff_users WHERE id=?",owner)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM staff_owner_recovery",Long.class)).isZero();
        jdbc.update("DELETE FROM staff_users WHERE id=?",owner);assertThat(service.status().available()).isFalse();
    }
    @Test void migrationClosesSetupForPreexistingOwner() throws Exception {
        user("preexisting","OWNER_ADMIN",false);
        jdbc.execute("DROP TABLE staff_owner_recovery");jdbc.execute("DROP TABLE staff_owner_setup");migration("V121__first_owner_setup.sql");
        assertThat(service.status().available()).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM staff_owner_recovery",Long.class)).isZero();
    }
    private List<Object> race(Callable<Object> action) throws Exception {
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Object> attempt=()->{start.await();try{return action.call();}catch(ResponseStatusException failure){return failure;}};
            var one=pool.submit(attempt);var two=pool.submit(attempt);start.countDown();return List.of(one.get(10,TimeUnit.SECONDS),two.get(10,TimeUnit.SECONDS));
        } finally {pool.shutdownNow();pool.awaitTermination(10,TimeUnit.SECONDS);}
    }
    @Test void simultaneousSetupCreatesExactlyOneOwner() throws Exception {
        var results=race(()->service.setup(setupKey,"first",password,"Owner"));
        assertThat(results.stream().filter(OwnerAccountService.Created.class::isInstance).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM staff_users",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM staff_owner_recovery",Long.class)).isEqualTo(1);
    }
    @Test void recoveryIsSingleUsePreservesMfaAndAccessAndRevokesSessions() throws Exception {
        var first=service.setup(setupKey,"first",password,"Owner");long owner=id("first");enroll(owner);session(owner);
        jdbc.update("INSERT INTO staff_branch_access VALUES (?,1)",owner);
        var mfaBefore=jdbc.queryForMap("SELECT * FROM staff_mfa WHERE staff_id=?",owner);
        var results=race(()->service.recover(first.recoveryKey(),"recovered","Replacement-password-2026"));
        var successes=results.stream().filter(OwnerAccountService.Created.class::isInstance).map(OwnerAccountService.Created.class::cast).toList();
        assertThat(successes).hasSize(1);assertThat(successes.getFirst().recoveryKey()).isNotEqualTo(first.recoveryKey());
        rejected(403,()->service.recover(first.recoveryKey(),"replayed",password));
        assertThat(id("recovered")).isEqualTo(owner);assertThat(passwords.matches("Replacement-password-2026",jdbc.queryForObject("SELECT password_hash FROM staff_users WHERE id=?",String.class,owner))).isTrue();
        assertThat(jdbc.queryForMap("SELECT * FROM staff_mfa WHERE staff_id=?",owner)).isEqualTo(mfaBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM staff_mfa_recovery WHERE staff_id=?",Long.class,owner)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM staff_branch_access WHERE staff_user_id=?",Long.class,owner)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT r.name FROM staff_users u JOIN roles r ON r.id=u.role_id WHERE u.id=?",String.class,owner)).isEqualTo("OWNER_ADMIN");
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL FROM staff_sessions WHERE staff_id=?",Boolean.class,owner)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM staff_mfa_enrollments WHERE staff_id=?",Long.class,owner)).isZero();
        env.setProperty("staff.owner-setup.enabled","false"); // recovery works after setup has been disabled
        assertThat(service.recover(successes.getFirst().recoveryKey(),"recovered-again",password).username()).isEqualTo("recovered-again");
    }
    @Test void existingOwnerCanRotateKeyOnlyWithPasswordAndMfa() {
        long owner=user("existing","OWNER_ADMIN",true);enroll(owner);
        assertThat(service.account(owner).hasRecoveryKey()).isFalse();
        rejected(403,()->service.recoveryKey(owner,"wrong-password","287082"));
        rejected(403,()->service.recoveryKey(owner,password,"000000"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM staff_owner_recovery",Long.class)).isZero();
        var first=service.recoveryKey(owner,password,"287082");
        rejected(403,()->service.recoveryKey(owner,password,"287082")); // same TOTP cannot be replayed
        var second=service.recoveryKey(owner,password,"saved-mfa-code");
        rejected(403,()->service.recover(first.recoveryKey(),"stale",password));
        assertThat(service.recover(second.recoveryKey(),"valid",password).username()).isEqualTo("valid");
    }
    @Test void renameRequiresOwnerAndReauthPreservesPasswordAndRollsBackMfaOnConflict() {
        long owner=user("owner","OWNER_ADMIN",true),other=user("existing-name","BRANCH_MANAGER",true);enroll(owner);session(owner);
        var otherBefore=jdbc.queryForMap("SELECT * FROM staff_users WHERE id=?",other);
        String oldHash=jdbc.queryForObject("SELECT password_hash FROM staff_users WHERE id=?",String.class,owner);
        rejected(403,()->service.rename(owner,"new-name","wrong-password","287082"));
        rejected(403,()->service.rename(owner,"new-name",password,"000000"));
        rejected(409,()->service.rename(owner,"EXISTING-NAME",password,"saved-mfa-code"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM staff_mfa_recovery WHERE staff_id=?",Long.class,owner)).isEqualTo(1);
        service.rename(owner,"NEW-NAME",password,"saved-mfa-code");
        assertThat(id("new-name")).isEqualTo(owner);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM staff_users WHERE id=?",String.class,owner)).isEqualTo(oldHash);
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL FROM staff_sessions WHERE staff_id=?",Boolean.class,owner)).isTrue();
        assertThat(jdbc.queryForMap("SELECT * FROM staff_users WHERE id=?",other)).isEqualTo(otherBefore);
        rejected(403,()->service.account(other));rejected(403,()->service.recoveryKey(other,password,"287082"));
        jdbc.update("UPDATE staff_users SET active=FALSE WHERE id=?",owner);rejected(403,()->service.account(owner));
    }
    @Test void recoveryConflictAndInactiveOwnerDoNotConsumeKeyOrChangeCredentials() {
        var first=service.setup(setupKey,"first",password,"Owner");long owner=id("first");user("taken","BRANCH_MANAGER",true);
        rejected(409,()->service.recover(first.recoveryKey(),"TAKEN",password));
        assertThat(id("first")).isEqualTo(owner);
        jdbc.update("UPDATE staff_users SET active=FALSE WHERE id=?",owner);
        rejected(403,()->service.recover(first.recoveryKey(),"new-name",password));
        jdbc.update("UPDATE staff_users SET active=TRUE WHERE id=?",owner);
        assertThat(service.recover(first.recoveryKey(),"new-name",password).username()).isEqualTo("new-name");
    }
    @Test void failedAttemptLimitsSurviveOuterRollbackAndExpireWithoutAlteringLoginLimits() {
        var outer=new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        outer.executeWithoutResult(status->{for(int i=0;i<10;i++)service.limit("setup","client");status.setRollbackOnly();});
        rejected(429,()->service.limit("setup","client"));
        String bucket="owner:"+StaffSessionService.hash("setup:client");
        assertThat(jdbc.queryForObject("SELECT failures FROM staff_login_limits WHERE username=?",Integer.class,bucket)).isEqualTo(11);
        jdbc.update("UPDATE staff_login_limits SET updated_at=CURRENT_TIMESTAMP-INTERVAL '11 minutes' WHERE username=?",bucket);service.limit("setup","client");
        assertThat(jdbc.queryForObject("SELECT failures FROM staff_login_limits WHERE username=?",Integer.class,bucket)).isEqualTo(1);
        jdbc.update("INSERT INTO staff_login_limits(username,failures,updated_at) VALUES (?,99,CURRENT_TIMESTAMP-INTERVAL '2 days'),('owner:stale',99,CURRENT_TIMESTAMP-INTERVAL '2 days')",StaffSessionService.hash("legacy-login"));
        service.limit("recovery","other");
        assertThat(jdbc.queryForObject("SELECT failures FROM staff_login_limits WHERE username=?",Integer.class,StaffSessionService.hash("legacy-login"))).isEqualTo(99);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM staff_login_limits WHERE username='owner:stale'",Long.class)).isZero();
    }
}
