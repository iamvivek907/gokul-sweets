package com.gokulsweets.restaurant.brand;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.*;
import com.gokulsweets.restaurant.storage.R2StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@SpringBootTest @Transactional
class BrandCareerIntegrationTest {
 @Autowired BrandService brand; @Autowired CareerService careers; @Autowired JdbcTemplate jdbc;
 @MockitoBean StaffAuthorizationService staff; @MockitoBean R2StorageService storage;
 long branch;
 @BeforeEach void fixture(){String key=UUID.randomUUID().toString().substring(0,8);Long role=jdbc.queryForObject("INSERT INTO roles(name) VALUES(?) RETURNING id",Long.class,"CAREERS-"+key);Long id=jdbc.queryForObject("INSERT INTO staff_users(username,password_hash,full_name,role_id) VALUES(?,'test','Test',?) RETURNING id",Long.class,"careers-"+key,role);var user=new StaffUser();user.setId(id);var owner=new Role();owner.setName("OWNER_ADMIN");user.setRole(owner);when(staff.getCurrentStaff()).thenReturn(user);branch=jdbc.queryForObject("INSERT INTO branches(code,name,active) VALUES(?,?,true) RETURNING id",Long.class,"CAREERS-"+key,"Careers test");}
 CareerService.ApplicationInput application(Long job,String phone){return new CareerService.ApplicationInput(UUID.randomUUID(),job,branch,"Test applicant",phone,null,"Test role",new BigDecimal("2"),"Test qualifications",true,"");}
 @Test void draftsStayPrivateAndVersionConflictCannotOverwrite(){var before=brand.adminContent().story();brand.save(new BrandService.Copy("Draft private","Intro","Story","Body",false),before.version());var person=brand.create(new BrandService.PersonInput("TEAM","Private name","Role","Biography",0,false));assertThat(brand.publicContent().story()).isNull();assertThat(brand.publicContent().people()).noneMatch(p->p.id()==person.id());assertThatThrownBy(()->brand.save(new BrandService.Copy("Stale","Intro","Story","Body",true),before.version())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);}
 @Test void closedJobsRejectAndRepeatedRequestIsIdempotent(){var job=careers.create(new CareerService.JobInput(branch,"Test role","Test","Required skills",BigDecimal.ZERO,true));var input=application(job.id(),"9876543210");var receipt=careers.apply(input,"test-client");assertThat(careers.apply(input,"test-client").reference()).isEqualTo(receipt.reference());careers.save(job.id(),new CareerService.JobInput(branch,"Test role","Test","Required skills",BigDecimal.ZERO,false),job.version());assertThatThrownBy(()->careers.apply(application(job.id(),"9876543211"),"test-client")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);}
 @Test void contactDeduplicationPaginationAndAccessAreEnforced(){careers.apply(application(null,"9876543210"),"test-client");assertThatThrownBy(()->careers.apply(application(null,"9876543210"),"test-client")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);careers.apply(application(null,"9876543211"),"test-client");var first=careers.applicants(branch,null,null,null,null,null,0,1);var second=careers.applicants(branch,null,null,null,null,null,1,1);assertThat(first.total()).isEqualTo(2);assertThat(first.items()).hasSize(1);assertThat(second.items().getFirst().id()).isNotEqualTo(first.items().getFirst().id());assertThat(first.counts().get("NEW")).isEqualTo(2);assertThatThrownBy(()->careers.applicants(branch,null,null,null,null,null,0,101)).isInstanceOf(IllegalArgumentException.class);doThrow(new AccessDeniedException("Denied")).when(staff).requirePermission(PermissionName.CAREERS_MANAGE);assertThatThrownBy(()->careers.applicants(branch,null,null,null,null,null,0,25)).isInstanceOf(AccessDeniedException.class);}
}
