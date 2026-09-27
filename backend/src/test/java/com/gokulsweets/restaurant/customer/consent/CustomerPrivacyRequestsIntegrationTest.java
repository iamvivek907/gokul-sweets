package com.gokulsweets.restaurant.customer.consent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class CustomerPrivacyRequestsIntegrationTest {
    @Autowired CustomerPrivacyRequests requests;

    @Test
    void repeatedRequestIsIdempotentAndIsolatedBySubjectEnvironmentAndKind() {
        var subject = UUID.randomUUID();
        assertThat(requests.forSubject(ConsentEnvironment.DEV, subject)).isEmpty();
        var first = requests.submit(ConsentEnvironment.DEV, subject, PrivacyRequestKind.EXPORT);
        var repeat = requests.submit(ConsentEnvironment.DEV, subject, PrivacyRequestKind.EXPORT);
        assertThat(repeat).isEqualTo(first);
        assertThat(requests.forSubject(ConsentEnvironment.DEV, subject)).hasSize(1);
        assertThat(requests.forSubject(ConsentEnvironment.PROD, subject)).isEmpty();
        assertThat(requests.forSubject(ConsentEnvironment.DEV, UUID.randomUUID())).isEmpty();
        requests.submit(ConsentEnvironment.DEV, subject, PrivacyRequestKind.DELETION_REVIEW);
        assertThat(requests.forSubject(ConsentEnvironment.DEV, subject)).hasSize(2);
        assertThatThrownBy(() -> requests.submit(ConsentEnvironment.DEV, null, PrivacyRequestKind.EXPORT))
                .isInstanceOf(NullPointerException.class);
    }
}
