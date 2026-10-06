package com.gokulsweets.restaurant.menuimport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.web.SecurityFilterChain;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={"gokul.jobs.worker-enabled=true","gokul.jobs.poll-ms=3600000","gokul.jobs.background-enabled=false","inventory.automation.scheduler-enabled=false"})
class MenuWorkerStartupTest {
    @Autowired ApplicationContext context;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.gokulsweets.restaurant.payment.service.PaymentExpiryService expiry;
    @Test void workerBootsWithoutHttpServerOrServletSecurityChain(){
        context.getBean(com.gokulsweets.restaurant.payment.service.PaymentExpiryScheduler.class).expirePendingPayments();
        org.mockito.Mockito.verifyNoInteractions(expiry);
        assertThat(context.getBeansOfType(MenuImportWorker.class)).hasSize(1);
        assertThat(context.getBeansOfType(SecurityFilterChain.class)).isEmpty();
        assertThat(context).isNotInstanceOf(org.springframework.web.context.WebApplicationContext.class);
    }
}
