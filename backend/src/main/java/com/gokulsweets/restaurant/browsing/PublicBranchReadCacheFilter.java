package com.gokulsweets.restaurant.browsing;

import com.gokulsweets.restaurant.observability.MethodTiming;

import io.micrometer.core.instrument.MeterRegistry;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/** Short, bounded public-display cache. Never intercepts cart, checkout, stock or account reads. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class PublicBranchReadCacheFilter extends OncePerRequestFilter {
    private final long ttlNanos;
    private final MeterRegistry metrics;
    private final LongSupplier clock;
    private final Map<String, Entry> cache = new LinkedHashMap<>(16, .75f, true);
    private final Object[] locks = new Object[32];
    private final AtomicLong generation = new AtomicLong();

    /** Immutable cached JSON with a monotonic expiry and no customer-specific headers. */
    private record Entry(byte[] body, String contentType, long expires) {}

    /** Builds the process-local cache; zero TTL disables it for troubleshooting. */
    @Autowired
    public PublicBranchReadCacheFilter(
            @Value("${gokul.browsing.branch-cache-ms:3000}") long ttlMillis,
            MeterRegistry metrics) {
        this(ttlMillis, metrics, System::nanoTime);
    }

    PublicBranchReadCacheFilter(long ttlMillis, MeterRegistry metrics, LongSupplier clock) {
        this.ttlNanos =
                java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(
                        Math.min(5000, Math.max(0, ttlMillis)));
        this.metrics = metrics;
        this.clock = clock;
        for (int i = 0; i < locks.length; i++) locks[i] = new Object();
    }

    /** Filters only exact public branch endpoints and admin writes that invalidate display data. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        long started =
                MethodTiming.start(
                        PublicBranchReadCacheFilter.class, "shouldNotFilter(HttpServletRequest)");
        try {
            String path = request.getRequestURI();
            boolean write =
                    !java.util.Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())
                            && path.startsWith("/api/admin/");
            boolean read =
                    "GET".equals(request.getMethod())
                            && request.getQueryString() == null
                            && path.matches("/api/branches(?:/[0-9]{1,18})?");
            return !write && !read;
        } finally {
            MethodTiming.finish(
                    started,
                    PublicBranchReadCacheFilter.class,
                    "shouldNotFilter(HttpServletRequest)");
        }
    }

    /**
     * Shares successful JSON display responses; errors and oversized responses are never retained.
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long started =
                MethodTiming.start(
                        PublicBranchReadCacheFilter.class,
                        "doFilterInternal(HttpServletRequest,HttpServletResponse,FilterChain)");
        try {
            if (!"GET".equals(request.getMethod())) {
                chain.doFilter(request, response);
                if (response.getStatus() >= 200 && response.getStatus() < 300) {
                    generation.incrementAndGet();
                    synchronized (cache) {
                        cache.clear();
                    }
                }
                return;
            }
            if (ttlNanos == 0) {
                chain.doFilter(request, response);
                return;
            }
            String key = request.getRequestURI();
            synchronized (locks[Math.floorMod(key.hashCode(), locks.length)]) {
                Entry entry;
                synchronized (cache) {
                    entry = cache.get(key);
                }
                if (entry != null && clock.getAsLong() - entry.expires() < 0) {
                    metrics.counter("gokul.browsing.branch.cache.hits").increment();
                    response.setContentType(entry.contentType());
                    response.setContentLength(entry.body().length);
                    response.getOutputStream().write(entry.body());
                    return;
                }
                metrics.counter("gokul.browsing.branch.cache.misses").increment();
                long observed = generation.get(), start = clock.getAsLong();
                var wrapped = new ContentCachingResponseWrapper(response);
                chain.doFilter(request, wrapped);
                byte[] body = wrapped.getContentAsByteArray();
                String type = wrapped.getContentType();
                if (wrapped.getStatus() == 200
                        && type != null
                        && type.startsWith("application/json")
                        && body.length <= 262144
                        && !wrapped.containsHeader("Set-Cookie")
                        && clock.getAsLong() - start < ttlNanos) {
                    synchronized (cache) {
                        if (generation.get() == observed) {
                            cache.put(key, new Entry(body, type, start + ttlNanos));
                            while (cache.size() > 128)
                                cache.remove(cache.keySet().iterator().next());
                        }
                    }
                }
                wrapped.copyBodyToResponse();
            }
        } finally {
            MethodTiming.finish(
                    started,
                    PublicBranchReadCacheFilter.class,
                    "doFilterInternal(HttpServletRequest,HttpServletResponse,FilterChain)");
        }
    }
}
