package com.gokulsweets.restaurant.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class OwnerAccountController {
    private final OwnerAccountService accounts;
    private final StaffAuthorizationService authorization;
    @GetMapping("/api/admin/auth/owner-setup") public ResponseEntity<OwnerAccountService.Status> status() {return response(accounts.status());}
    @PostMapping("/api/admin/auth/owner-setup") public ResponseEntity<OwnerAccountService.Created> setup(@RequestBody Setup input,HttpServletRequest request) {
        accounts.limit("setup",request.getRemoteAddr());return response(accounts.setup(input.setupKey(),input.username(),input.password(),input.fullName()));
    }
    @PostMapping("/api/admin/auth/owner-recovery") public ResponseEntity<OwnerAccountService.Created> recover(@RequestBody Recovery input,HttpServletRequest request) {
        accounts.limit("recovery",request.getRemoteAddr());return response(accounts.recover(input.recoveryKey(),input.username(),input.password()));
    }
    @GetMapping("/api/admin/account-security") public ResponseEntity<OwnerAccountService.Account> account() {return response(accounts.account(authorization.getCurrentStaff().getId()));}
    @PostMapping("/api/admin/account-security/recovery-key") public ResponseEntity<OwnerAccountService.Created> key(@RequestBody Verification input,HttpServletRequest request) {
        long id=authorization.getCurrentStaff().getId();accounts.limit("key:"+id,request.getRemoteAddr());return response(accounts.recoveryKey(id,input.password(),input.code()));
    }
    @PostMapping("/api/admin/account-security/username") public ResponseEntity<Void> username(@RequestBody Rename input,HttpServletRequest request) {
        long id=authorization.getCurrentStaff().getId();accounts.limit("rename:"+id,request.getRemoteAddr());accounts.rename(id,input.username(),input.password(),input.code());return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    private static <T>ResponseEntity<T> response(T body) {return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
    public record Setup(String setupKey,String username,String password,String fullName) {}
    public record Recovery(String recoveryKey,String username,String password) {}
    public record Verification(String password,String code) {}
    public record Rename(String username,String password,String code) {}
}
