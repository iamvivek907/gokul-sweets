package com.gokulsweets.restaurant.security;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.staff.StaffUser;
import com.gokulsweets.restaurant.staff.StaffUserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Coordinates staff user details operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class StaffUserDetailsService implements UserDetailsService {

    private final StaffUserRepository staffUserRepository;

    /**
     * Loads user by username.
     *
     * @param username the username
     * @return the load user by username result
     * @throws UsernameNotFoundException if the operation cannot complete
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffUserDetailsService.class, "loadUserByUsername(String)");
        try {
            StaffUser staffUser =
                    staffUserRepository
                            .findByUsername(username)
                            .orElseThrow(
                                    () -> {
                                        log.warn("Staff login failed: username not found");
                                        return new UsernameNotFoundException(
                                                "Invalid username or password.");
                                    });
            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            /*
             * ROLE_OWNER_ADMIN
             * ROLE_MANAGER
             * etc.
             */
            authorities.add(new SimpleGrantedAuthority("ROLE_" + staffUser.getRole().getName()));
            /*
             * ORDER_VIEW
             * REPORT_VIEW
             * etc.
             */
            staffUser
                    .getRole()
                    .getPermissions()
                    .forEach(
                            permission ->
                                    authorities.add(
                                            new SimpleGrantedAuthority(
                                                    permission.getName().name())));
            log.debug(
                    "Staff user loaded for authentication: username={}, role={}",
                    staffUser.getUsername(),
                    staffUser.getRole().getName());
            return User.withUsername(staffUser.getUsername())
                    .password(staffUser.getPasswordHash())
                    .authorities(authorities)
                    .disabled(!staffUser.isActive())
                    .build();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffUserDetailsService.class,
                    "loadUserByUsername(String)");
        }
    }
}
