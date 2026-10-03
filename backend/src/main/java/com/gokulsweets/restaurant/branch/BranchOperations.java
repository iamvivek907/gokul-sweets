package com.gokulsweets.restaurant.branch;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor
public class BranchOperations {
 private final JdbcTemplate jdbc;
 private final StaffAuthorizationService authorization;
 public record Status(boolean operational) {}
 private void authorize(long id){authorization.requirePermission(PermissionName.BRANCH_MANAGE);authorization.requireBranchAccess(id);}
 @Transactional(readOnly=true) public Status get(long id){authorize(id);return status(id);}
 @Transactional public Status set(long id,Status input){
  authorize(id);if(input==null)throw new IllegalArgumentException("Choose an operational status.");
  jdbc.queryForObject("SELECT id FROM branches WHERE id=? FOR UPDATE",Long.class,id);
  jdbc.update("UPDATE branches SET operational=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",input.operational(),id);
  return status(id);
 }
 private Status status(long id){return jdbc.queryForObject("SELECT operational FROM branches WHERE id=?",(r,n)->new Status(r.getBoolean(1)),id);}
}
