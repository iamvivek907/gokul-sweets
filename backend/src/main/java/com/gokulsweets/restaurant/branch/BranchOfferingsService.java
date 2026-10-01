package com.gokulsweets.restaurant.branch;

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
import java.util.List;

@Service @RequiredArgsConstructor
public class BranchOfferingsService {
    private final JdbcTemplate jdbc;
    private final BranchRepository branches;
    private final StaffAuthorizationService staff;
    public record Offering(@NotBlank @Size(max=80) String title, @NotBlank @Size(max=240) String description) {}
    public record Input(@NotNull @Size(max=12) List<@Valid Offering> offerings) {}
    public record Snapshot(long version, List<Offering> draft, List<Offering> published) {}
    private void authorize(long id) {
        staff.requirePermission(PermissionName.BRANCH_MANAGE);
        staff.requireBranchAccess(id);
        if (!branches.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Branch not found.");
    }
    private List<Offering> list(long id,String scope) {
        return jdbc.query("SELECT title, description FROM branch_offerings WHERE branch_id=? AND scope=? ORDER BY position",
                (rs,n)->new Offering(rs.getString(1),rs.getString(2)),id,scope);
    }
    private long version(long id,boolean lock) {
        jdbc.update("INSERT INTO branch_offering_settings(branch_id) VALUES (?) ON CONFLICT DO NOTHING",id);
        return jdbc.queryForObject("SELECT edit_version FROM branch_offering_settings WHERE branch_id=?"+(lock?" FOR UPDATE":""),Long.class,id);
    }
    private Snapshot snapshot(long id) {return new Snapshot(version(id,false),list(id,"DRAFT"),list(id,"PUBLISHED"));}
    private void expected(long id,long expected) {
        if(version(id,true)!=expected)throw new ResponseStatusException(HttpStatus.CONFLICT,"Branch offerings changed. Reload before saving.");
    }
    private void replace(long id,String scope,List<Offering> items) {
        jdbc.update("DELETE FROM branch_offerings WHERE branch_id=? AND scope=?",id,scope);
        for(int i=0;i<items.size();i++) {
            var item=items.get(i);
            jdbc.update("INSERT INTO branch_offerings(branch_id,scope,position,title,description) VALUES (?,?,?,?,?)",
                    id,scope,i,item.title().trim(),item.description().trim());
        }
    }
    @Transactional public Snapshot admin(long id) {authorize(id);return snapshot(id);}
    @Transactional public Snapshot save(long id,Input input,long expected) {
        authorize(id);expected(id,expected);
        if(input.offerings()==null || input.offerings().size()>12 || input.offerings().stream().anyMatch(o->o==null||o.title()==null||o.title().isBlank()||o.title().length()>80||o.description()==null||o.description().isBlank()||o.description().length()>240))
            throw new IllegalArgumentException("Add up to twelve offerings with a title and description.");
        replace(id,"DRAFT",input.offerings());jdbc.update("UPDATE branch_offering_settings SET edit_version=edit_version+1 WHERE branch_id=?",id);return snapshot(id);
    }
    @Transactional public Snapshot publish(long id,long expected) {
        authorize(id);expected(id,expected);replace(id,"PUBLISHED",list(id,"DRAFT"));
        jdbc.update("UPDATE branch_offering_settings SET edit_version=edit_version+1 WHERE branch_id=?",id);return snapshot(id);
    }
    @Transactional(readOnly=true) public List<Offering> published(long id) {return list(id,"PUBLISHED");}
}
