package com.gokulsweets.restaurant.customer.consent;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import com.gokulsweets.restaurant.staff.StaffUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
class AdminPrivacyRequestQueueIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired CustomerPrivacyRequests requests;

    @Test
    void ownerReadsOnlyCurrentEnvironmentAndEachSuccessfulPageIsAudited() {
        var features = new EnhancementProperties();
        var staff = mock(StaffAuthorizationService.class);
        var actor = new StaffUser();
        var roleId = jdbc.queryForObject("SELECT id FROM roles WHERE name = 'OWNER_ADMIN'", Long.class);
        var actorId = jdbc.queryForObject("""
                INSERT INTO staff_users (username, password_hash, full_name, role_id)
                VALUES (?, 'test-hash', 'Privacy tester', ?) RETURNING id
                """, Long.class, "privacy-" + UUID.randomUUID(), roleId);
        actor.setId(actorId);
        when(staff.getCurrentStaff()).thenReturn(actor);
        var settings = new MockEnvironment().withProperty("gokul.environment-isolation.environment", "DEV");
        var queue = new AdminPrivacyRequestQueue(jdbc, features, settings, staff);
        var subject = UUID.randomUUID();
        requests.submit(ConsentEnvironment.DEV, subject, PrivacyRequestKind.EXPORT);
        requests.submit(ConsentEnvironment.PROD, UUID.randomUUID(), PrivacyRequestKind.DELETION_REVIEW);

        assertThatThrownBy(() -> queue.view(0)).isInstanceOf(ResponseStatusException.class);
        features.setCustomerConsentControls(true);
        doThrow(new AccessDeniedException("denied"))
                .when(staff).requirePermission(PermissionName.PRIVACY_REQUEST_VIEW);
        assertThatThrownBy(() -> queue.view(0)).isInstanceOf(AccessDeniedException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_privacy_queue_access_audit WHERE staff_user_id = ?", Long.class, actorId))
                .isZero();

        doNothing().when(staff).requirePermission(PermissionName.PRIVACY_REQUEST_VIEW);
        var entries = queue.view(0);
        assertThat(entries).singleElement().satisfies(entry -> {
            assertThat(entry.subjectId()).isEqualTo(subject);
            assertThat(entry.kind()).isEqualTo(PrivacyRequestKind.EXPORT);
        });
        verify(staff, times(2)).requirePermission(PermissionName.PRIVACY_REQUEST_VIEW);
        assertThat(jdbc.queryForObject("""
                SELECT returned_count FROM customer_privacy_queue_access_audit
                WHERE staff_user_id = ? AND environment = 'DEV'
                """, Integer.class, actorId)).isEqualTo(1);
    }
}
