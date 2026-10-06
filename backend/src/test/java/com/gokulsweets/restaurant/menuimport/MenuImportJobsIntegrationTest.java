package com.gokulsweets.restaurant.menuimport;

import com.gokulsweets.restaurant.security.*;
import com.gokulsweets.restaurant.staff.*;
import com.gokulsweets.restaurant.menuimport.dto.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.core.userdetails.User;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(properties={"gokul.imports.async-enabled=true","gokul.jobs.worker-enabled=true","gokul.jobs.poll-ms=3600000"})
class MenuImportJobsIntegrationTest {
    @Autowired MenuImportJobs jobs;
    @Autowired MenuImportWorker worker;
    @Autowired JdbcTemplate jdbc;
    @Autowired StaffUserRepository staff;
    @MockitoBean MenuImportService imports;
    @MockitoBean StaffAuthorizationService authorization;
    @MockitoBean StaffUserDetailsService users;
    long branch,staffId;String username;
    MockMultipartFile file=new MockMultipartFile("file","menu.xlsx",null,new byte[]{1,2,3});
    @BeforeEach void setup(){
        username="job-"+UUID.randomUUID();
        branch=jdbc.queryForObject("INSERT INTO branches(code,name) VALUES(?,'Worker') RETURNING id",Long.class,username);
        staffId=jdbc.queryForObject("INSERT INTO staff_users(username,password_hash,full_name,role_id) SELECT ?,'disabled','Worker',id FROM roles WHERE name='OWNER_ADMIN' RETURNING id",Long.class,username);
        when(authorization.getCurrentStaff()).thenReturn(staff.findDetailedById(staffId).orElseThrow());
        when(users.loadUserByUsername(username)).thenReturn(User.withUsername(username).password("disabled").authorities("MENU_MANAGE").build());
    }
    @AfterEach void cleanup(){jdbc.update("DELETE FROM menu_import_jobs WHERE staff_id=?",staffId);}
    @Test void retriesOfQueuedSubmissionShareOneDurableJobAndConcurrentWorkersExecuteOnce()throws Exception {
        var job=jobs.enqueue(branch,file,"VALIDATE");assertThat(jobs.enqueue(branch,file,"VALIDATE").id()).isEqualTo(job.id());
        assertThatThrownBy(()->jobs.enqueue(branch,file,"IMPORT")).hasMessageContaining("already running");
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(imports.validate(eq(branch),any())).thenAnswer(invocation->{entered.countDown();assertThat(release.await(10,TimeUnit.SECONDS)).isTrue();return new MenuImportValidationResponse(true,1,List.of());});
        try(var executor=Executors.newFixedThreadPool(2)){
            var first=executor.submit(()->worker.process());assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
            executor.submit(()->worker.process()).get(5,TimeUnit.SECONDS);release.countDown();first.get(10,TimeUnit.SECONDS);
        }finally{release.countDown();}
        assertThat(jobs.get(branch,job.id()).status()).isEqualTo("SUCCEEDED");verify(imports,times(1)).validate(eq(branch),any());
        assertThat(jdbc.queryForObject("SELECT payload IS NULL FROM menu_import_jobs WHERE id=?",Boolean.class,job.id())).isTrue();
    }
    @Test void lostAcknowledgementCanRecoverTheSameJobAfterItHasCompleted(){
        UUID submission=UUID.randomUUID();
        var job=jobs.enqueue(branch,file,"VALIDATE",submission);
        when(imports.validate(eq(branch),any())).thenReturn(new MenuImportValidationResponse(true,1,List.of()));
        worker.process();
        assertThat(jobs.getSubmission(branch,submission).id()).isEqualTo(job.id());
        assertThat(jobs.getSubmission(branch,submission).status()).isEqualTo("SUCCEEDED");
        assertThat(jobs.enqueue(branch,file,"VALIDATE",submission).id()).isEqualTo(job.id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM menu_import_jobs WHERE staff_id=?",Long.class,staffId)).isEqualTo(1);
        verify(imports,times(1)).validate(eq(branch),any());
    }
    @Test void anotherAcknowledgementAliasRecoversTheDeduplicatedActiveJob(){
        var first=jobs.enqueue(branch,file,"IMPORT",UUID.randomUUID());
        UUID second=UUID.randomUUID();
        assertThat(jobs.enqueue(branch,file,"IMPORT",second).id()).isEqualTo(first.id());
        assertThat(jobs.getSubmission(branch,second).id()).isEqualTo(first.id());
        assertThatThrownBy(()->jobs.enqueue(branch,file,"VALIDATE",second)).hasMessageContaining("different submission");
        assertThatThrownBy(()->jobs.enqueue(branch,new MockMultipartFile("file","other.xlsx",null,new byte[]{4}),"IMPORT",second)).hasMessageContaining("different submission");
    }
    @Test void submissionRecoveryIsScopedToBothBranchAndRequester(){
        UUID submission=UUID.randomUUID();jobs.enqueue(branch,file,"IMPORT",submission);
        assertThatThrownBy(()->jobs.getSubmission(branch+1,submission)).hasMessageContaining("not been acknowledged");
        var other=mock(StaffUser.class);when(other.getId()).thenReturn(staffId+1);when(authorization.getCurrentStaff()).thenReturn(other);
        assertThatThrownBy(()->jobs.getSubmission(branch,submission)).hasMessageContaining("not been acknowledged");
    }
    @Test void expiredClaimRecoversAfterWorkerDeathAndFailureRollsBackMenuWrites(){
        var job=jobs.enqueue(branch,file,"IMPORT");
        jdbc.update("UPDATE menu_import_jobs SET status='PROCESSING',lease_until=CURRENT_TIMESTAMP-INTERVAL '1 minute',attempts=1 WHERE id=?",job.id());
        when(imports.importMenu(eq(branch),any())).thenAnswer(invocation->{jdbc.update("UPDATE branches SET name='Must roll back' WHERE id=?",branch);throw new IllegalArgumentException("Invalid file");});
        worker.process();
        assertThat(jobs.get(branch,job.id()).status()).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT name FROM branches WHERE id=?",String.class,branch)).isEqualTo("Worker");
    }
    @Test void rowValidationErrorsSurviveWorkerRollbackButUnexpectedErrorsStayGeneric(){
        var job=jobs.enqueue(branch,file,"IMPORT");
        String detail="Menu import failed validation. Row 7, column base_price: must be greater than zero.";
        when(imports.importMenu(eq(branch),any())).thenThrow(new MenuImportValidationException(detail));
        worker.process();assertThat(jobs.get(branch,job.id()).error()).isEqualTo(detail);
        assertThat(MenuImportWorker.safeFailureMessage(new MenuImportValidationException("Missing required column: base_price")))
                .isEqualTo("Missing required column: base_price");
        assertThat(MenuImportWorker.safeFailureMessage(new IllegalStateException("database password secret")))
                .doesNotContain("database", "secret");
        assertThat(MenuImportWorker.safeFailureMessage(new IllegalArgumentException("Internal parser details")))
                .doesNotContain("Internal parser");
    }
    @Test void currentPermissionRevocationBlocksAnAlreadyQueuedImport(){
        var job=jobs.enqueue(branch,file,"IMPORT");when(users.loadUserByUsername(username)).thenReturn(User.withUsername(username).password("disabled").authorities("ORDER_VIEW").build());
        worker.process();assertThat(jobs.get(branch,job.id()).status()).isEqualTo("FAILED");verify(imports,never()).importMenu(anyLong(),any());
    }
    @Test void anotherBranchCannotReadJobAndUploadBytesAreBounded(){
        var job=jobs.enqueue(branch,file,"VALIDATE");assertThatThrownBy(()->jobs.get(branch+1,job.id())).hasMessageContaining("not found");
        assertThatThrownBy(()->jobs.enqueue(branch,new MockMultipartFile("file","large.xlsx",null,new byte[2*1024*1024+1]),"IMPORT")).hasMessageContaining("2 MB");
        jdbc.update("UPDATE menu_import_jobs SET status='FAILED',payload=NULL WHERE id=?",job.id());
    }
}
