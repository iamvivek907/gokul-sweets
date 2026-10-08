package com.gokulsweets.restaurant.security;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class OwnerAccountService {
    private final JdbcTemplate jdbc;
    private final Environment environment;
    private final EnhancementProperties flags;
    private final PasswordEncoder passwords;
    private final StaffMfaService mfa;
    private final TransactionTemplate tx;
    private final SecureRandom random=new SecureRandom();
    public OwnerAccountService(JdbcTemplate jdbc,Environment environment,EnhancementProperties flags,
            PasswordEncoder passwords,StaffMfaService mfa,PlatformTransactionManager transactions) {
        this.jdbc=jdbc;this.environment=environment;this.flags=flags;this.passwords=passwords;this.mfa=mfa;
        tx=new TransactionTemplate(transactions);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);tx.setTimeout(30);
    }
    public record Status(boolean available) {}
    public record Created(String username,String recoveryKey) {}
    public record Account(String username,boolean hasRecoveryKey) {}
    private boolean secure() {return flags.isSecureStaffSessions()&&List.of("DEV","PROD").contains(environment.getProperty("gokul.environment-isolation.environment",""));}
    private boolean enabled() {return secure()&&environment.getProperty("staff.owner-setup.enabled",Boolean.class,false)
        &&environment.getProperty("staff.owner-setup.key-hash","").matches("[0-9a-f]{64}");}
    private void requireSecure() {if(!secure())throw new ResponseStatusException(HttpStatus.NOT_FOUND);}
    public Status status() {
        if(!enabled())return new Status(false);
        return tx.execute(status->{deadlines();return new Status(!closed());});
    }
    // Called with the latch locked. Existing owners close it permanently, even when inactive.
    private boolean closed() {
        Boolean done=jdbc.queryForObject("SELECT completed_at IS NOT NULL FROM staff_owner_setup WHERE singleton=TRUE FOR UPDATE",Boolean.class);
        if(Boolean.TRUE.equals(done))return true;
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM staff_users u JOIN roles r ON r.id=u.role_id WHERE r.name='OWNER_ADMIN')",Boolean.class))) {
            jdbc.update("UPDATE staff_owner_setup SET completed_at=CURRENT_TIMESTAMP WHERE singleton=TRUE");return true;
        }
        return false;
    }
    /** Independently committed attempts survive invalid credentials and transaction rollback. */
    public void limit(String action,String source) {
        requireSecure();
        String bucket="owner:"+StaffSessionService.hash(action+":"+source);
        Integer attempts=tx.execute(status->{
            deadlines();
            // Only this feature’s stale buckets; never alter existing login throttles.
            jdbc.update("DELETE FROM staff_login_limits WHERE username IN (SELECT username FROM staff_login_limits WHERE username LIKE 'owner:%' AND updated_at<CURRENT_TIMESTAMP-INTERVAL '1 day' LIMIT 200)");
        return jdbc.queryForObject("""
            INSERT INTO staff_login_limits(username,failures,updated_at) VALUES (?,1,CURRENT_TIMESTAMP)
            ON CONFLICT(username) DO UPDATE SET failures=CASE WHEN staff_login_limits.updated_at<CURRENT_TIMESTAMP-INTERVAL '10 minutes'
              THEN 1 ELSE staff_login_limits.failures+1 END,updated_at=CURRENT_TIMESTAMP RETURNING failures
            """,Integer.class,bucket);
        });
        if(attempts!=null&&attempts>10)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Too many account attempts. Wait ten minutes before trying again.");
    }
    public Created setup(String key,String username,String password,String fullName) {
        if(!enabled())throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if(key==null||!key.matches("[A-Za-z0-9_-]{43}")||!MessageDigest.isEqual(
            StaffSessionService.hash(key).getBytes(StandardCharsets.US_ASCII),environment.getProperty("staff.owner-setup.key-hash","").getBytes(StandardCharsets.US_ASCII)))denied();
        String name=username(username);password(password);
        if(fullName==null||fullName.trim().isEmpty()||fullName.trim().length()>150)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter an owner name of up to 150 characters.");
        Created result=write(()->{
            if(closed())return null;
            unique(name,0);
            Long id=jdbc.queryForObject("""
                INSERT INTO staff_users(username,password_hash,full_name,role_id,active,created_at,updated_at)
                SELECT ?,?,?,id,TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP FROM roles WHERE name='OWNER_ADMIN' RETURNING id
                """,Long.class,name,passwords.encode(password),fullName.trim());
            String recovery=rotate(id);
            jdbc.update("UPDATE staff_owner_setup SET completed_at=CURRENT_TIMESTAMP,first_owner_id=? WHERE singleton=TRUE",id);
            audit(id,"FIRST_OWNER_CREATED");return new Created(name,recovery);
        });
        if(result==null)throw new ResponseStatusException(HttpStatus.CONFLICT,"Owner setup is already completed. Use admin login.");
        return result;
    }
    public Created recover(String key,String username,String password) {
        requireSecure();String name=username(username);password(password);
        if(key==null||!key.matches("[A-Za-z0-9_-]{43}"))denied();
        String hash=StaffSessionService.hash(key);
        var owners=jdbc.queryForList("SELECT staff_id FROM staff_owner_recovery WHERE key_hash=?",Long.class,hash);
        if(owners.isEmpty()) {denied();return null;}
        long id=owners.getFirst();
        return write(()->{
            owner(id);
            if(jdbc.queryForList("SELECT staff_id FROM staff_owner_recovery WHERE staff_id=? AND key_hash=? FOR UPDATE",Long.class,id,hash).isEmpty())denied();
            unique(name,id);
            jdbc.update("UPDATE staff_users SET username=?,password_hash=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",name,passwords.encode(password),id);
            invalidate(id);String replacement=rotate(id);audit(id,"OWNER_CREDENTIALS_RECOVERED");
            return new Created(name,replacement);
        });
    }
    public Account account(long id) {
        return write(()->{var user=owner(id);return new Account((String)user.get("username"),Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM staff_owner_recovery WHERE staff_id=?)",Boolean.class,id)));});
    }
    public Created recoveryKey(long id,String password,String code) {
        return write(()->{reauthenticate(id,password,code);String key=rotate(id);audit(id,"OWNER_RECOVERY_KEY_ROTATED");return new Created((String)owner(id).get("username"),key);});
    }
    public void rename(long id,String name,String password,String code) {
        String normalized=username(name);
        write(()->{reauthenticate(id,password,code);unique(normalized,id);
            jdbc.update("UPDATE staff_users SET username=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",normalized,id);
            invalidate(id);audit(id,"OWNER_USERNAME_CHANGED");return true;});
    }
    private Map<String,Object> owner(long id) {
        requireSecure();
        var rows=jdbc.queryForList("SELECT u.username,u.password_hash FROM staff_users u JOIN roles r ON r.id=u.role_id WHERE u.id=? AND u.active AND r.name='OWNER_ADMIN' FOR UPDATE OF u",id);
        if(rows.isEmpty()) {denied();return Map.of();}return rows.getFirst();
    }
    private void reauthenticate(long id,String password,String code) {
        var user=owner(id);
        if(password==null||password.length()>128||!passwords.matches(password,(String)user.get("password_hash"))||!mfa.verify(id,code))denied();
    }
    private String rotate(long id) {
        byte[] bytes=new byte[32];random.nextBytes(bytes);String key=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("INSERT INTO staff_owner_recovery(staff_id,key_hash) VALUES (?,?) ON CONFLICT(staff_id) DO UPDATE SET key_hash=EXCLUDED.key_hash,created_at=CURRENT_TIMESTAMP",id,StaffSessionService.hash(key));
        return key;
    }
    private void invalidate(long id) {
        jdbc.update("UPDATE staff_sessions SET revoked_at=CURRENT_TIMESTAMP WHERE staff_id=? AND revoked_at IS NULL",id);
        jdbc.update("DELETE FROM staff_mfa_enrollments WHERE staff_id=?",id);
    }
    private void audit(long id,String event) {jdbc.update("INSERT INTO staff_auth_audit(staff_id,event) VALUES (?,?)",id,event);}
    private void unique(String name,long id) {
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM staff_users WHERE LOWER(username)=? AND id<>?)",Boolean.class,name,id)))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"That username is already in use.");
    }
    private static String username(String name) {
        if(name==null||!name.trim().matches("[A-Za-z0-9._@-]{2,100}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use 2–100 letters, numbers, dots, underscores, @ or hyphens for the username.");
        return name.trim().toLowerCase(Locale.ROOT);
    }
    private static void password(String password) {
        if(password==null||password.length()<12||password.getBytes(StandardCharsets.UTF_8).length>72)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use at least 12 characters and a shorter password if it exceeds the supported length.");
    }
    private static void denied() {throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Account verification failed.");}
    private void deadlines() {jdbc.execute("SET LOCAL statement_timeout='10s'");jdbc.execute("SET LOCAL lock_timeout='2s'");}
    private <T>T write(java.util.function.Supplier<T> action) {
        try {return tx.execute(status->{deadlines();return action.get();});}
        catch(DataIntegrityViolationException duplicate) {throw new ResponseStatusException(HttpStatus.CONFLICT,"Account details changed. Refresh and try again.");}
    }
}
