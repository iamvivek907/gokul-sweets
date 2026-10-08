package com.gokulsweets.restaurant.security;

import com.gokulsweets.restaurant.observability.MethodTiming;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** HTTP endpoints for owner account operations. */
@RestController
@RequiredArgsConstructor
public class OwnerAccountController {

    private final OwnerAccountService accounts;

    private final StaffAuthorizationService authorization;

    /**
     * Statuses the operation.
     *
     * @return the status result
     */
    @GetMapping("/api/admin/auth/owner-setup")
    public ResponseEntity<OwnerAccountService.Status> status() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OwnerAccountController.class, "status()");
        try {
            return response(accounts.status());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OwnerAccountController.class, "status()");
        }
    }

    /**
     * Setups the operation.
     *
     * @param input the input
     * @param request the request
     * @return the setup result
     */
    @PostMapping("/api/admin/auth/owner-setup")
    public ResponseEntity<OwnerAccountService.Created> setup(
            @RequestBody Setup input, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OwnerAccountController.class, "setup(Setup,HttpServletRequest)");
        try {
            accounts.limit("setup", request.getRemoteAddr());
            return response(
                    accounts.setup(
                            input.setupKey(),
                            input.username(),
                            input.password(),
                            input.fullName()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OwnerAccountController.class,
                    "setup(Setup,HttpServletRequest)");
        }
    }

    /**
     * Recovers the operation.
     *
     * @param input the input
     * @param request the request
     * @return the recover result
     */
    @PostMapping("/api/admin/auth/owner-recovery")
    public ResponseEntity<OwnerAccountService.Created> recover(
            @RequestBody Recovery input, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OwnerAccountController.class, "recover(Recovery,HttpServletRequest)");
        try {
            accounts.limit("recovery", request.getRemoteAddr());
            return response(
                    accounts.recover(input.recoveryKey(), input.username(), input.password()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OwnerAccountController.class,
                    "recover(Recovery,HttpServletRequest)");
        }
    }

    /**
     * Accounts the operation.
     *
     * @return the account result
     */
    @GetMapping("/api/admin/account-security")
    public ResponseEntity<OwnerAccountService.Account> account() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OwnerAccountController.class, "account()");
        try {
            return response(accounts.account(authorization.getCurrentStaff().getId()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OwnerAccountController.class, "account()");
        }
    }

    /**
     * Key the operation.
     *
     * @param input the input
     * @param request the request
     * @return the key result
     */
    @PostMapping("/api/admin/account-security/recovery-key")
    public ResponseEntity<OwnerAccountService.Created> key(
            @RequestBody Verification input, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OwnerAccountController.class, "key(Verification,HttpServletRequest)");
        try {
            long id = authorization.getCurrentStaff().getId();
            accounts.limit("key:" + id, request.getRemoteAddr());
            return response(accounts.recoveryKey(id, input.password(), input.code()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OwnerAccountController.class,
                    "key(Verification,HttpServletRequest)");
        }
    }

    /**
     * Usernames the operation.
     *
     * @param input the input
     * @param request the request
     * @return the username result
     */
    @PostMapping("/api/admin/account-security/username")
    public ResponseEntity<Void> username(@RequestBody Rename input, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OwnerAccountController.class, "username(Rename,HttpServletRequest)");
        try {
            long id = authorization.getCurrentStaff().getId();
            accounts.limit("rename:" + id, request.getRemoteAddr());
            accounts.rename(id, input.username(), input.password(), input.code());
            return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OwnerAccountController.class,
                    "username(Rename,HttpServletRequest)");
        }
    }

    /**
     * Responses the operation.
     *
     * @param <T> the generic t type
     * @param body the body
     * @return the response result
     */
    private static <T> ResponseEntity<T> response(T body) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OwnerAccountController.class, "response(T)");
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OwnerAccountController.class, "response(T)");
        }
    }

    /**
     * Immutable setup data contract.
     *
     * @param setupKey the setup key
     * @param username the username
     * @param password the password
     * @param fullName the full name
     */
    public record Setup(String setupKey, String username, String password, String fullName) {}

    /**
     * Immutable recovery data contract.
     *
     * @param recoveryKey the recovery key
     * @param username the username
     * @param password the password
     */
    public record Recovery(String recoveryKey, String username, String password) {}

    /**
     * Immutable verification data contract.
     *
     * @param password the password
     * @param code the code
     */
    public record Verification(String password, String code) {}

    /**
     * Immutable rename data contract.
     *
     * @param username the username
     * @param password the password
     * @param code the code
     */
    public record Rename(String username, String password, String code) {}
}
