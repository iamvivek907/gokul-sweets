package com.gokulsweets.restaurant.menu;

import com.gokulsweets.restaurant.branch.BranchRepository;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service @RequiredArgsConstructor
public class MobileMenuOptionsService {
 private final JdbcTemplate jdbc;
 private final BranchRepository branches;
 private final StaffAuthorizationService staff;
 public record Choice(@NotNull Long productId,@NotBlank @Size(max=30) String label) {}
 public record Group(@NotBlank @Pattern(regexp="[A-Za-z0-9-]{1,40}") String key,@NotBlank @Size(max=100) String title,
                     @NotNull @Size(min=2,max=6) List<@Valid Choice> choices) {}
 public record Input(@Min(0) long version,@NotNull @Size(max=40) List<@Valid Group> groups) {}
 public record Snapshot(long version,List<Group> groups) {}
 private void authorize(long id){staff.requirePermission(PermissionName.MENU_MANAGE);staff.requireBranchAccess(id);}
 @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
 public Snapshot publicRead(long id){
  var branch=branches.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Branch not found."));
  if(!branch.isActive())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Branch not available.");
  return read(id);
 }
 @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
 public Snapshot adminRead(long id){authorize(id);return read(id);}
 private Snapshot read(long id){
  var versions=jdbc.queryForList("SELECT version FROM mobile_menu_config WHERE branch_id=?",Long.class,id);
  var groups=jdbc.query("SELECT group_key,title FROM mobile_menu_groups WHERE branch_id=? ORDER BY position",(rs,n)->new Group(rs.getString(1),rs.getString(2),List.of()),id);
  Map<String,List<Choice>> choices=new HashMap<>();
  jdbc.query("SELECT group_key,product_id,label FROM mobile_menu_choices WHERE branch_id=? ORDER BY group_key,position",(org.springframework.jdbc.core.RowCallbackHandler) rs->choices.computeIfAbsent(rs.getString(1),key->new ArrayList<>()).add(new Choice(rs.getLong(2),rs.getString(3))),id);
  var complete=groups.stream().map(g->new Group(g.key(),g.title(),List.copyOf(choices.getOrDefault(g.key(),List.of())))).toList();
  return new Snapshot(versions.isEmpty()?0:versions.getFirst(),complete);
 }
 @Transactional
 public Snapshot save(long id,Input input){
  authorize(id);
  if(!branches.existsById(id))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Branch not found.");
  // Validation precedes any replacement. Never infer a portion from its product name.
  Set<Long> products=new HashSet<>();Set<String> keys=new HashSet<>();
  for(var group:input.groups()){
   if(!keys.add(group.key()))throw new IllegalArgumentException("Each portion group needs a unique key.");
   Set<String> labels=new HashSet<>();Long category=null;
   for(var choice:group.choices()){
    if(!products.add(choice.productId())||!labels.add(choice.label().trim().toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("A product or portion label is repeated.");
    var categories=jdbc.queryForList("SELECT p.category_id FROM branch_products bp JOIN products p ON p.id=bp.product_id WHERE bp.branch_id=? AND p.id=? AND p.sale_mode='UNIT'",Long.class,id,choice.productId());
    if(categories.size()!=1)throw new IllegalArgumentException("Choose a piece-based item from this branch menu for every portion.");
    if(category!=null&&!category.equals(categories.getFirst()))throw new IllegalArgumentException("Portions must belong to the same category.");
    category=categories.getFirst();
   }
  }
  jdbc.update("INSERT INTO mobile_menu_config(branch_id) VALUES (?) ON CONFLICT DO NOTHING",id);
  long current=jdbc.queryForObject("SELECT version FROM mobile_menu_config WHERE branch_id=? FOR UPDATE",Long.class,id);
  if(current!=input.version())throw new ResponseStatusException(HttpStatus.CONFLICT,"Portion options changed. Reload before saving.");
  jdbc.update("DELETE FROM mobile_menu_groups WHERE branch_id=?",id);
  for(int i=0;i<input.groups().size();i++){
   var group=input.groups().get(i);jdbc.update("INSERT INTO mobile_menu_groups(branch_id,group_key,title,position) VALUES (?,?,?,?)",id,group.key(),group.title().trim(),i);
   for(int j=0;j<group.choices().size();j++){var c=group.choices().get(j);jdbc.update("INSERT INTO mobile_menu_choices(branch_id,group_key,product_id,label,position) VALUES (?,?,?,?,?)",id,group.key(),c.productId(),c.label().trim(),j);}
  }
  jdbc.update("UPDATE mobile_menu_config SET version=version+1 WHERE branch_id=?",id);
  return read(id);
 }
}
