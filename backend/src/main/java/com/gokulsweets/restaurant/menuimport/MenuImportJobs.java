package com.gokulsweets.restaurant.menuimport;

import com.gokulsweets.restaurant.security.*;
import com.gokulsweets.restaurant.staff.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.security.*;
import java.io.*;

@Service @RequiredArgsConstructor
public class MenuImportJobs {
    private final JdbcTemplate jdbc;
    private final StaffAuthorizationService authorization;
    @Value("${gokul.imports.async-enabled:false}") private boolean enabled;
    public boolean enabled(){return enabled;}
    public record Job(UUID id,String operation,String status,String result,String error) {}
    private long authorize(long branchId) {
        authorization.requirePermission(PermissionName.MENU_MANAGE);authorization.requireBranchAccess(branchId);
        return authorization.getCurrentStaff().getId();
    }
    @Transactional
    public Job enqueue(long branchId,MultipartFile file,String operation) {
        long staff=authorize(branchId);
        if(!Set.of("VALIDATE","IMPORT").contains(operation))throw new IllegalArgumentException("Invalid menu operation.");
        if(file.isEmpty()||file.getSize()>2*1024*1024)throw new IllegalArgumentException("Choose an Excel file of at most 2 MB.");
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM branches WHERE id=?)",Boolean.class,branchId)))throw new IllegalArgumentException("Branch not found.");
        // Serialize admission only, not processing. Bound queued binary data across replicas.
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(714114)",Object.class);
        byte[] bytes;String digest;
        try{bytes=file.getBytes();digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(IOException|NoSuchAlgorithmException e){throw new IllegalArgumentException("Unable to read the upload.");}
        var active=jdbc.query("SELECT id,operation,status,result,error,file_digest,staff_id FROM menu_import_jobs WHERE branch_id=? AND status IN ('QUEUED','PROCESSING')",(r,n)->new Object[]{job(r),r.getString("file_digest"),r.getLong("staff_id")},branchId);
        if(!active.isEmpty()) {
            var value=active.getFirst();var job=(Job)value[0];
            if(value[1].equals(digest)&&value[2].equals(staff)&&job.operation().equals(operation))return job;
            throw new ResponseStatusException(HttpStatus.CONFLICT,"A menu job is already running for this branch. Wait for it to finish.");
        }
        if(jdbc.queryForObject("SELECT COUNT(*) FROM menu_import_jobs WHERE status IN ('QUEUED','PROCESSING')",Long.class)>=10)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"The import queue is full. Please try later.");
        UUID id=UUID.randomUUID();String name=Optional.ofNullable(file.getOriginalFilename()).orElse("menu.xlsx");name=name.substring(0,Math.min(255,name.length()));
        jdbc.update("INSERT INTO menu_import_jobs(id,branch_id,staff_id,operation,filename,file_digest,payload) VALUES(?,?,?,?,?,?,?)",id,branchId,staff,operation,name,digest,bytes);
        return new Job(id,operation,"QUEUED",null,null);
    }
    @Transactional(readOnly=true)
    public Job get(long branchId,UUID id) {
        authorize(branchId);
        return jdbc.query("SELECT id,operation,status,result,error FROM menu_import_jobs WHERE id=? AND branch_id=?",(r,n)->job(r),id,branchId).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Menu job not found."));
    }
    static Job job(java.sql.ResultSet r)throws java.sql.SQLException{return new Job(r.getObject("id",UUID.class),r.getString("operation"),r.getString("status"),r.getString("result"),r.getString("error"));}
}
