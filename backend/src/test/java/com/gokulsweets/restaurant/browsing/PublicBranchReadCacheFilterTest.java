package com.gokulsweets.restaurant.browsing;

import static org.assertj.core.api.Assertions.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

class PublicBranchReadCacheFilterTest {
    private final AtomicLong clock = new AtomicLong();
    private final AtomicInteger reads = new AtomicInteger();
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    private final PublicBranchReadCacheFilter filter =
            new PublicBranchReadCacheFilter(3000, metrics, clock::get);
    private final FilterChain branch =
            (req, res) -> {
                res.setContentType("application/json");
                res.getWriter().write("{\"revision\":" + reads.incrementAndGet() + "}");
            };

    private MockHttpServletResponse read(String path) throws Exception {
        var response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", path), response, branch);
        return response;
    }

    @Test
    void sharesOnlyFreshPublicJsonAndExpiresWithoutExtendingOnHits() throws Exception {
        assertThat(read("/api/branches/1").getContentAsString()).contains("1");
        clock.set(2_000_000_000L);
        assertThat(read("/api/branches/1").getContentAsString()).contains("1");
        clock.set(3_000_000_000L);
        assertThat(read("/api/branches/1").getContentAsString()).contains("2");
        assertThat(reads).hasValue(2);
        assertThat(metrics.get("gokul.browsing.branch.cache.hits").counter().count()).isEqualTo(1);
    }

    @Test
    void separatesBranchesAndNeverCachesCheckoutOrPrivateReads() throws Exception {
        read("/api/branches/1");
        read("/api/branches/2");
        for (String path :
                new String[] {
                    "/api/cart/availability",
                    "/api/customer/identity/me",
                    "/api/orders/abc",
                    "/api/branches/1/experience"
                }) {
            read(path);
            read(path);
        }
        assertThat(reads).hasValue(10);
    }

    @Test
    void queryRequestsAndCookieResponsesBypassStorage() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/branches/1");
        request.setQueryString("x=1");
        filter.doFilter(request, new MockHttpServletResponse(), branch);
        read("/api/branches/1");
        assertThat(reads).hasValue(2);
        var other = new PublicBranchReadCacheFilter(3000, metrics, clock::get);
        FilterChain cookies =
                (req, res) -> {
                    ((jakarta.servlet.http.HttpServletResponse) res)
                            .setHeader("Set-Cookie", "session=test");
                    branch.doFilter(req, res);
                };
        for (int i = 0; i < 2; i++)
            other.doFilter(
                    new MockHttpServletRequest("GET", "/api/branches/1"),
                    new MockHttpServletResponse(),
                    cookies);
        assertThat(reads).hasValue(4);
    }

    @Test
    void successfulAdminWritesInvalidateAndFailedWritesDoNot() throws Exception {
        read("/api/branches/1");
        filter.doFilter(
                new MockHttpServletRequest("PATCH", "/api/admin/branches/1"),
                new MockHttpServletResponse(),
                (q, r) -> ((jakarta.servlet.http.HttpServletResponse) r).setStatus(409));
        read("/api/branches/1");
        assertThat(reads).hasValue(1);
        filter.doFilter(
                new MockHttpServletRequest("PATCH", "/api/admin/branches/1"),
                new MockHttpServletResponse(),
                (q, r) -> {});
        read("/api/branches/1");
        assertThat(reads).hasValue(2);
    }

    @Test
    void errorsAndOversizedResponsesAreNotCached() throws Exception {
        for (boolean error : new boolean[] {true, false}) {
            var calls = new AtomicInteger();
            FilterChain bad =
                    (q, r) -> {
                        calls.incrementAndGet();
                        r.setContentType("application/json");
                        if (error) ((jakarta.servlet.http.HttpServletResponse) r).setStatus(503);
                        r.getWriter().write("x".repeat(error ? 2 : 262145));
                    };
            for (int i = 0; i < 2; i++)
                filter.doFilter(
                        new MockHttpServletRequest("GET", "/api/branches/3"),
                        new MockHttpServletResponse(),
                        bad);
            assertThat(calls).hasValue(2);
        }
    }

    @Test
    void disabledCacheAlwaysReadsFresh() throws Exception {
        var off = new PublicBranchReadCacheFilter(0, metrics, clock::get);
        for (int i = 0; i < 2; i++)
            off.doFilter(
                    new MockHttpServletRequest("GET", "/api/branches/1"),
                    new MockHttpServletResponse(),
                    branch);
        assertThat(reads).hasValue(2);
    }

    @Test
    void adminCommitDuringLoadCannotRepopulateAnOldSnapshot() throws Exception {
        filter.doFilter(
                new MockHttpServletRequest("GET", "/api/branches/1"),
                new MockHttpServletResponse(),
                (q, r) -> {
                    try {
                        filter.doFilter(
                                new MockHttpServletRequest("PATCH", "/api/admin/branches/1"),
                                new MockHttpServletResponse(),
                                (a, b) -> {});
                    } catch (jakarta.servlet.ServletException e) {
                        throw e;
                    }
                    branch.doFilter(q, r);
                });
        read("/api/branches/1");
        assertThat(reads).hasValue(2);
    }
}
