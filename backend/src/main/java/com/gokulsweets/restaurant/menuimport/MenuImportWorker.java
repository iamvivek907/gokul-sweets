package com.gokulsweets.restaurant.menuimport;

import com.gokulsweets.restaurant.security.StaffUserDetailsService;
import com.gokulsweets.restaurant.staff.StaffUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
import java.util.*;

@Component @RequiredArgsConstructor @Slf4j
@ConditionalOnProperty(name="gokul.jobs.worker-enabled",havingValue="true")
public class MenuImportWorker {
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager manager;
    private final MenuImportService imports;
    private final StaffUserRepository staff;
    private final StaffUserDetailsService users;
    private final ObjectMapper mapper;
    record Claim(UUID id,UUID token) {}
    @Scheduled(fixedDelayString="${gokul.jobs.poll-ms:2000}",initialDelayString="${gokul.jobs.poll-ms:2000}")
    public void process() {
        var transaction=new TransactionTemplate(manager);
        var claim=transaction.execute(status->{
            var ids=jdbc.queryForList("SELECT id FROM menu_import_jobs WHERE status='QUEUED' OR (status='PROCESSING' AND lease_until<CURRENT_TIMESTAMP) ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 1",UUID.class);
            if(ids.isEmpty())return null;
            var id=ids.getFirst();var token=UUID.randomUUID();
            int attempts=jdbc.queryForObject("SELECT attempts FROM menu_import_jobs WHERE id=?",Integer.class,id);
            if(attempts>=3){jdbc.update("UPDATE menu_import_jobs SET status='FAILED',error='Worker stopped repeatedly. Upload again after checking worker health.',payload=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=?",id);return null;}
            jdbc.update("UPDATE menu_import_jobs SET status='PROCESSING',attempts=attempts+1,claim_token=?,lease_until=CURRENT_TIMESTAMP+INTERVAL '10 minutes',updated_at=CURRENT_TIMESTAMP WHERE id=?",token,id);
            return new Claim(id,token);
        });
        if(claim==null)return;
        var previous=SecurityContextHolder.getContext();
        try {
            transaction.executeWithoutResult(status->{
                // Lock is held through the import and its result: process death rolls both back.
                var rows=jdbc.queryForList("SELECT * FROM menu_import_jobs WHERE id=? AND claim_token=? AND status='PROCESSING' FOR UPDATE",claim.id(),claim.token());
                if(rows.isEmpty())return;var row=rows.getFirst();
                var owner=staff.findDetailedById(((Number)row.get("staff_id")).longValue()).orElseThrow();
                var details=users.loadUserByUsername(owner.getUsername());
                if(!details.isEnabled())throw new org.springframework.security.access.AccessDeniedException("Import requester is disabled.");
                var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(details,null,details.getAuthorities()));SecurityContextHolder.setContext(context);
                var file=new Upload((String)row.get("filename"),(byte[])row.get("payload"));
                long branch=((Number)row.get("branch_id")).longValue();
                if(details.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("MENU_MANAGE")) || !owner.getRole().getName().equals("OWNER_ADMIN")&&owner.getBranches().stream().noneMatch(b->b.getId()==branch))
                    throw new org.springframework.security.access.AccessDeniedException("Import requester no longer has branch menu access.");
                Object result=row.get("operation").equals("VALIDATE")?imports.validate(branch,file):imports.importMenu(branch,file);
                jdbc.update("UPDATE menu_import_jobs SET status='SUCCEEDED',result=?,payload=NULL,lease_until=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=?",mapper.writeValueAsString(result),claim.id());
            });
        }catch(Exception failure){
            log.warn("Menu job failed: jobId={}",claim.id(),failure);
            transaction.executeWithoutResult(status->jdbc.update("UPDATE menu_import_jobs SET status='FAILED',error=?,payload=NULL,lease_until=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=? AND claim_token=? AND status='PROCESSING'",safeFailureMessage(failure),claim.id(),claim.token()));
        }finally{SecurityContextHolder.setContext(previous);}
    }
    static String safeFailureMessage(Exception failure) {
        // Only application-authored validation errors may be shown to staff.
        // Database, authorization and unexpected parser failures remain generic.
        String message=failure.getMessage();
        if(failure instanceof MenuImportValidationException && message!=null)
            return message.substring(0,Math.min(message.length(),500));
        return "Menu job failed. Check the file and your current branch permissions, then try again.";
    }
    record Upload(String filename,byte[] bytes) implements MultipartFile {
        public String getName(){return "file";}public String getOriginalFilename(){return filename;}public String getContentType(){return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";}
        public boolean isEmpty(){return bytes.length==0;}public long getSize(){return bytes.length;}public byte[] getBytes(){return bytes;}
        public java.io.InputStream getInputStream(){return new java.io.ByteArrayInputStream(bytes);}
        public void transferTo(java.io.File dest)throws java.io.IOException{java.nio.file.Files.write(dest.toPath(),bytes);}
    }
}
