package com.gokulsweets.restaurant.menu.workspace;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import com.gokulsweets.restaurant.storage.R2StorageService;
import tools.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.util.*;
@RestController @RequiredArgsConstructor
public class MenuAppearanceController {
 private final JdbcTemplate jdbc;private final StaffAuthorizationService staff;private final ObjectMapper mapper;private final R2StorageService storage;
 public record Frame(@Min(0) @Max(100) int x,@Min(0) @Max(100) int y,@Min(100) @Max(300) int zoom,@NotNull @Pattern(regexp="COVER|CONTAIN") String fit){}
 public record Banner(@NotBlank @Pattern(regexp="[A-Za-z0-9-]{1,40}") String key,@NotBlank @Size(max=120) String titleEn,@Size(max=120) String titleHi,@Size(max=240) String subtitleEn,@Size(max=240) String subtitleHi,@Size(max=60) String buttonLabel,@Positive Long categoryId,boolean visible,@Min(0) int order,Instant startAt,Instant endAt,@Size(max=1000) String mediaUrl,@Size(max=30) @Pattern(regexp="image/(jpeg|png|webp)|video/(mp4|webm)") String mediaType,@Size(max=1000) String posterUrl,@NotNull @Valid Frame frame){}
 public record Category(@Positive long id,@Min(0) int order,@Size(max=1000) String imageUrl){}
 public record Config(@NotNull @Size(max=12) List<@Valid Banner> banners,@NotNull @Size(max=200) List<@Valid Category> categories){}
 public record Input(@Min(0) long version,@NotNull @Valid Config config,boolean publish){}
 public record Snapshot(long version,Config draft,Config live,String publishedAt){}
 private void authorize(long branch){staff.requirePermission(PermissionName.MENU_MANAGE);staff.requireBranchAccess(branch);}
 private Config empty(){return new Config(List.of(),List.of());}
 private Config decode(Object value){try{return mapper.readValue(String.valueOf(value),Config.class);}catch(Exception e){throw new IllegalStateException("Unable to read menu appearance.",e);}}
 private String encode(Config config){try{return mapper.writeValueAsString(config);}catch(Exception e){throw new IllegalArgumentException("Invalid menu appearance.",e);}}
 private Snapshot read(long branch){var rows=jdbc.queryForList("SELECT * FROM branch_menu_appearance WHERE branch_id=?",branch);if(rows.isEmpty())return new Snapshot(0,empty(),empty(),null);var r=rows.getFirst();return new Snapshot(((Number)r.get("version")).longValue(),decode(r.get("draft")),decode(r.get("live")),r.get("published_at")==null?null:r.get("published_at").toString());}
 @GetMapping("/api/admin/branches/{branch}/menu/workspace/appearance") @Transactional(readOnly=true) public Snapshot admin(@PathVariable long branch){authorize(branch);return read(branch);}
 @GetMapping("/api/menu/appearance") @Transactional(readOnly=true) public Config live(@RequestParam long branchId){if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM branches WHERE id=? AND active=true)",Boolean.class,branchId)))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Branch unavailable.");var c=read(branchId).live();Instant now=Instant.now();return new Config(c.banners().stream().filter(b->b.visible()&&(b.startAt()==null||!now.isBefore(b.startAt()))&&(b.endAt()==null||now.isBefore(b.endAt()))).sorted(Comparator.comparingInt(Banner::order)).toList(),c.categories());}
 private void managed(String url){if(url==null||url.isBlank())return;storage.validateManagedCampaignUrl(url);}
 @PutMapping("/api/admin/branches/{branch}/menu/workspace/appearance") @Transactional public Snapshot save(@PathVariable long branch,@Valid @RequestBody Input input){
  authorize(branch);Set<String> keys=new HashSet<>();Set<Long> categoryIds=new HashSet<>();
  for(var b:input.config().banners()){
   if(!keys.add(b.key()))throw new IllegalArgumentException("Duplicate banner key.");managed(b.mediaUrl());managed(b.posterUrl());
   if(b.startAt()!=null&&b.endAt()!=null&&!b.endAt().isAfter(b.startAt()))throw new IllegalArgumentException("End must follow start.");
   if(b.categoryId()!=null)category(branch,b.categoryId());
   if(b.buttonLabel()!=null&&!b.buttonLabel().isBlank()&&b.categoryId()==null)throw new IllegalArgumentException("Choose a destination category for the button.");
   if(input.publish()&&b.visible()&&b.mediaType()!=null&&b.mediaType().startsWith("video/")&&(b.posterUrl()==null||b.posterUrl().isBlank()))throw new IllegalArgumentException("Video banners need a static poster for reduced motion.");
  }
  for(var c:input.config().categories()){if(!categoryIds.add(c.id()))throw new IllegalArgumentException("Duplicate category.");category(branch,c.id());managed(c.imageUrl());}
  jdbc.update("INSERT INTO branch_menu_appearance(branch_id) VALUES (?) ON CONFLICT DO NOTHING",branch);
  var old=jdbc.queryForMap("SELECT * FROM branch_menu_appearance WHERE branch_id=? FOR UPDATE",branch);if(((Number)old.get("version")).longValue()!=input.version())throw new ResponseStatusException(HttpStatus.CONFLICT,"Appearance changed. Reload before saving; draft retained.");
  String config=encode(input.config());jdbc.update("UPDATE branch_menu_appearance SET draft=?::jsonb,live=CASE WHEN ? THEN ?::jsonb ELSE live END,published_at=CASE WHEN ? THEN now() ELSE published_at END,version=version+1 WHERE branch_id=?",config,input.publish(),config,input.publish(),branch);
  jdbc.update("INSERT INTO menu_workspace_audit(actor,branch_id,action,before_state,after_state) VALUES (?, ?, ?, ?, ?)",staff.getCurrentStaff().getUsername(),branch,input.publish()?"APPEARANCE_PUBLISH":"APPEARANCE_DRAFT",String.valueOf(old.get("draft")),config);return read(branch);
 }
 private void category(long branch,long id){if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM branch_products bp JOIN products p ON p.id=bp.product_id WHERE bp.branch_id=? AND p.category_id=?)",Boolean.class,branch,id)))throw new IllegalArgumentException("Choose a category in this branch menu.");}
 @PostMapping(value="/api/admin/branches/{branch}/menu/workspace/appearance/media",consumes="multipart/form-data") public R2StorageService.CampaignMedia media(@PathVariable long branch,@RequestParam MultipartFile file,@RequestParam(defaultValue="false") boolean poster){authorize(branch);if(file.getContentType()==null||!Set.of("image/jpeg","image/png","image/webp","video/mp4","video/webm").contains(file.getContentType()))throw new IllegalArgumentException("Choose a JPEG, PNG, WebP, MP4 or WebM file.");return storage.uploadCampaignMedia(branch,file,poster);}
}
