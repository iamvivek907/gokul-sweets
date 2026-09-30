package com.gokulsweets.restaurant.brand;

import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import com.gokulsweets.restaurant.storage.R2StorageService;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.List;

@Service @RequiredArgsConstructor
public class BrandService {
 private final JdbcTemplate jdbc;
 private final StaffAuthorizationService staff;
 private final R2StorageService storage;
 public record Story(String title,String subtitle,String storyTitle,String storyBody,String imageUrl,boolean published,long version) {}
 public record Copy(@NotBlank @Size(max=120) String title,@NotBlank @Size(max=400) String subtitle,@NotBlank @Size(max=120) String storyTitle,@NotBlank @Size(max=6000) String storyBody,boolean published) {}
 public record Person(long id,String section,String name,String role,String bio,String photoUrl,int displayOrder,boolean published,long version) {}
 public record PersonInput(@Pattern(regexp="FOUNDER|TEAM|DEVELOPER") @NotNull String section,@NotBlank @Size(max=100) String name,@NotBlank @Size(max=120) String role,@NotNull @Size(max=1500) String bio,@Min(0) @Max(10000) int displayOrder,boolean published) {}
 public record Content(Story story,List<Person> people) {}
 private Story story(){return jdbc.queryForObject("SELECT * FROM brand_story WHERE id=1",(r,n)->new Story(r.getString("title"),r.getString("subtitle"),r.getString("story_title"),r.getString("story_body"),r.getString("image_url"),r.getBoolean("published"),r.getLong("version")));}
 private List<Person> people(boolean published){return jdbc.query("SELECT * FROM brand_people"+(published?" WHERE published":"")+" ORDER BY section,display_order,id",(r,n)->new Person(r.getLong("id"),r.getString("section"),r.getString("name"),r.getString("role"),r.getString("bio"),r.getString("photo_url"),r.getInt("display_order"),r.getBoolean("published"),r.getLong("version")));}
 private void check(){staff.requirePermission(PermissionName.ABOUT_MANAGE);}
 private void changed(int rows){if(rows!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Content changed. Reload before saving.");}
 private void audit(String action,long id){jdbc.update("INSERT INTO brand_career_audit(staff_id,action,entity_id) VALUES(?,?,?)",staff.getCurrentStaff().getId(),action,String.valueOf(id));}
 @Transactional(readOnly=true) public Content publicContent(){var story=story();return new Content(story.published()?story:null,people(true));}
 @Transactional(readOnly=true) public Content adminContent(){check();return new Content(story(),people(false));}
 @Transactional public Story save(Copy copy,long version){check();changed(jdbc.update("UPDATE brand_story SET title=?,subtitle=?,story_title=?,story_body=?,published=?,version=version+1 WHERE id=1 AND version=?",copy.title().trim(),copy.subtitle().trim(),copy.storyTitle().trim(),copy.storyBody().trim(),copy.published(),version));audit("ABOUT_COPY",1);return story();}
 @Transactional public Person create(PersonInput input){check();Long id=jdbc.queryForObject("INSERT INTO brand_people(section,name,role,bio,display_order,published) VALUES(?,?,?,?,?,?) RETURNING id",Long.class,input.section(),input.name().trim(),input.role().trim(),input.bio().trim(),input.displayOrder(),input.published());audit("PERSON_CREATE",id);return person(id);}
 private Person person(long id){return people(false).stream().filter(p->p.id()==id).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Profile not found."));}
 @Transactional public Person savePerson(long id,PersonInput input,long version){check();changed(jdbc.update("UPDATE brand_people SET section=?,name=?,role=?,bio=?,display_order=?,published=?,version=version+1 WHERE id=? AND version=?",input.section(),input.name().trim(),input.role().trim(),input.bio().trim(),input.displayOrder(),input.published(),id,version));audit("PERSON_SAVE",id);return person(id);}
 @Transactional public void removePerson(long id,long version){check();changed(jdbc.update("DELETE FROM brand_people WHERE id=? AND version=?",id,version));audit("PERSON_DELETE",id);}
 private String upload(long id,MultipartFile file){var media=storage.uploadCampaignMedia(id,file,true);TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)storage.deleteCampaignMedia(media.url());}});return media.url();}
 @Transactional public Story storyPhoto(MultipartFile file,long version){check();String url=upload(0,file);changed(jdbc.update("UPDATE brand_story SET image_url=?,version=version+1 WHERE id=1 AND version=?",url,version));audit("ABOUT_PHOTO",1);return story();}
 @Transactional public Person personPhoto(long id,MultipartFile file,long version){check();person(id);String url=upload(id,file);changed(jdbc.update("UPDATE brand_people SET photo_url=?,version=version+1 WHERE id=? AND version=?",url,id,version));audit("PERSON_PHOTO",id);return person(id);}
 @Transactional public Story removeStoryPhoto(long version){check();changed(jdbc.update("UPDATE brand_story SET image_url=NULL,version=version+1 WHERE id=1 AND version=?",version));audit("ABOUT_PHOTO_REMOVE",1);return story();}
 @Transactional public Person removePersonPhoto(long id,long version){check();changed(jdbc.update("UPDATE brand_people SET photo_url=NULL,version=version+1 WHERE id=? AND version=?",id,version));audit("PERSON_PHOTO_REMOVE",id);return person(id);}
}
