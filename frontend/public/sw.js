const VERSION =
    "v3";

const PUSH_DEDUPE_CACHE = "gokul-push-dedupe-v1";

const STATIC_CACHE =
    `gokul-static-${VERSION}`;

const PAGE_CACHE =
    `gokul-pages-${VERSION}`;

const IMAGE_CACHE =
    `gokul-images-${VERSION}`;

// Bound persistent public image storage independently from pages and push deduplication.
const IMAGE_META_CACHE = `gokul-image-meta-${VERSION}`;
const IMAGE_MAX_ENTRIES = 120;
const IMAGE_MAX_BYTES = 24 * 1024 * 1024;
const IMAGE_MAX_ITEM_BYTES = 2 * 1024 * 1024;
const IMAGE_MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000;
let imageWrites = Promise.resolve();
let pendingImageWrites = 0;

const OFFLINE_URL =
    "/offline";

const LAUNCH_WAIT_MS = 8000;
const LAUNCH_CACHE_WAIT_MS = 350;

const STATIC_ASSETS = [
    "/",
    OFFLINE_URL
];


self.addEventListener(
    "install",
    event => {

        event.waitUntil(
            caches
                .open(
                    STATIC_CACHE
                )
                .then(
                    cache =>
                        cache.addAll(
                            STATIC_ASSETS
                        )
                )
        );

        self.skipWaiting();
    }
);


self.addEventListener(
    "activate",
    event => {

        const allowedCaches = [
            STATIC_CACHE,
            PAGE_CACHE,
            IMAGE_CACHE,
            IMAGE_META_CACHE,
            PUSH_DEDUPE_CACHE
        ];

        event.waitUntil(
            caches
                .keys()
                .then(
                    cacheNames =>
                        Promise.all(
                            cacheNames
                                .filter(
                                    cacheName =>
                                        !allowedCaches.includes(
                                            cacheName
                                        )
                                )
                                .map(
                                    cacheName =>
                                        caches.delete(
                                            cacheName
                                        )
                                )
                        )
                )
        );

        self.clients.claim();
    }
);


self.addEventListener(
    "fetch",
    event => {

        const request =
            event.request;

        if (
            request.method !== "GET"
        ) {
            return;
        }


        const url =
            new URL(
                request.url
            );


        // Published app icons must follow HTTP freshness, including the stable Apple URL.
        if (url.origin === self.location.origin && url.pathname.startsWith("/pwa-icon/")) return;

        /*
         * Never cache transactional API calls.
         */
        if (
            url.pathname.startsWith(
                "/api/"
            )
        ) {
            return;
        }


        /*
         * Images:
         * cache-first.
         */
        if (
            request.destination ===
            "image"
        ) {

            event.respondWith(
                boundedImage(request, event)
            );

            return;
        }


        /*
         * Page navigation:
         * network-first.
         *
         * If network fails:
         * cached page -> offline page.
         */
        if (
            request.mode ===
            "navigate"
        ) {

            if (isStorefrontLaunch(url)) {
                const launch = launchNavigation(request, url);
                event.respondWith(launch.then(result => result.response));
                event.waitUntil(launch.then(result => result.cacheWrite).catch(() => {}));
            } else {
                event.respondWith(networkFirstNavigation(request));
            }

            return;
        }


        /*
         * JS / CSS / fonts:
         * stale-while-revalidate.
         */
        if (
            [
                "script",
                "style",
                "font"
            ].includes(
                request.destination
            )
        ) {

            event.respondWith(
                staleWhileRevalidate(
                    request,
                    STATIC_CACHE
                )
            );
        }
    }
);


/** Cache storage failures never prevent a successful network image from rendering. */
async function boundedImage(request, event) {
    let cache, metadata, cached;
    try {
        [cache, metadata] = await Promise.all([caches.open(IMAGE_CACHE), caches.open(IMAGE_META_CACHE)]);
        const [found, stored] = await Promise.all([cache.match(request), metadata.match(request)]);
        cached = found;
        const record = stored ? await stored.json() : null;
        const age = record ? Date.now() - record.storedAt : -1;
        if (cached && record && Number.isFinite(age) && age >= 0 && age < IMAGE_MAX_AGE_MS) return cached;
    } catch {
        return fetch(request);
    }
    let response;
    try {response = await fetch(request);} catch (error) {
        // Expired public artwork remains available offline; refresh it once the network returns.
        if (cached) return cached;
        throw error;
    }
    if (response.ok && response.type !== "opaque" && pendingImageWrites < 4
        && !(Number(response.headers.get("content-length")) > IMAGE_MAX_ITEM_BYTES)) {
        pendingImageWrites++;
        const copy = response.clone();
        // Serialize storage changes so simultaneous image loads cannot exceed either bound.
        imageWrites = imageWrites.then(() => storeImage(request, copy, cache, metadata))
            .catch(() => {}).finally(() => {pendingImageWrites--;});
        event.waitUntil(imageWrites);
    }
    return response;
}

async function storeImage(request, response, cache, metadata) {
    const advertised = Number(response.headers.get("content-length"));
    if (Number.isFinite(advertised) && advertised > IMAGE_MAX_ITEM_BYTES) return;
    if (!response.body) return;
    const reader = response.body.getReader(), parts = [];
    let size = 0;
    for (;;) {
        const {done, value} = await reader.read();
        if (done) break;
        size += value.byteLength;
        if (size > IMAGE_MAX_ITEM_BYTES) {void reader.cancel().catch(() => {}); return;}
        parts.push(value);
    }
    const bytes = new Uint8Array(size);
    let offset = 0;
    for (const part of parts) {bytes.set(part, offset); offset += part.byteLength;}
    try {
        const headers = new Headers(response.headers);
        headers.delete("content-encoding");
        headers.delete("content-length");
        await cache.put(request, new Response(bytes, {status: response.status, statusText: response.statusText, headers}));
        await metadata.put(request, new Response(JSON.stringify({storedAt: Date.now(), size})));
        await trimImages(cache, metadata);
    } catch {
        // Roll back partial writes, then let subsequent requests use the network normally.
        await Promise.allSettled([cache.delete(request), metadata.delete(request)]);
    }
}

async function trimImages(cache, metadata) {
    const records = [], now = Date.now();
    for (const key of await cache.keys()) {
        let record;
        try {record = await (await metadata.match(key))?.json();} catch {record = null;}
        if (!record || !Number.isFinite(record.storedAt) || !Number.isFinite(record.size)
            || record.size < 0 || record.size > IMAGE_MAX_ITEM_BYTES || now < record.storedAt
            || now - record.storedAt >= IMAGE_MAX_AGE_MS) {
            await Promise.all([cache.delete(key), metadata.delete(key)]);
        } else records.push({key, ...record});
    }
    records.sort((left, right) => left.storedAt - right.storedAt);
    let bytes = records.reduce((sum, record) => sum + record.size, 0);
    while (records.length > IMAGE_MAX_ENTRIES || bytes > IMAGE_MAX_BYTES) {
        const removed = records.shift();
        bytes -= removed.size;
        await Promise.all([cache.delete(removed.key), metadata.delete(removed.key)]);
    }
    const retained = new Set((await cache.keys()).map(key => key.url));
    for (const key of await metadata.keys()) if (!retained.has(key.url)) await metadata.delete(key);
}

async function staleWhileRevalidate(
    request,
    cacheName
) {

    const cache =
        await caches.open(
            cacheName
        );


    const cached =
        await cache.match(
            request
        );


    const networkPromise =
        fetch(
            request
        )
            .then(
                async response => {

                    if (
                        response.ok
                    ) {

                        await cache.put(
                            request,
                            response.clone()
                        );
                    }


                    return response;
                }
            )
            .catch(
                () => null
            );


    /*
     * If cached content exists,
     * return it immediately.
     *
     * Network request continues in the
     * background and refreshes the cache.
     */
    if (cached) {

        void networkPromise;

        return cached;
    }


    const networkResponse =
        await networkPromise;


    if (networkResponse) {
        return networkResponse;
    }


    return Response.error();
}


function isStorefrontLaunch(url) {
    return url.origin === self.location.origin
        && /^(?:\/|\/menu\/?|\/branches(?:\/[1-9]\d*)?\/?)$/.test(url.pathname);
}

// A Next.js response can flush metadata and scripts before it sends any launch UI.
function launchMarkupProbe() {
    const decoder = new TextDecoder();
    const tag = /^<\/?([a-z][\w:-]*)(?:[^"'<>]|"[^"]*"|'[^']*')*>/i;
    const headTags = new Set(["html", "body", "base", "link", "meta", "head"]);
    const nonRendering = new Set(["script", "style", "title", "noscript"]);
    // This sentinel follows the complete opening screen, including its retry link.
    const marker = '<span data-gokul-launch-ready="" hidden=""></span>';
    const voidTags = new Set(["area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"]);
    const visualTags = new Set(["canvas", "embed", "hr", "iframe", "img", "svg", "video"]);
    const attributes = /([^\s"'<>/=]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>]+)))?/g;
    const hiddenStack = [];
    let pending = "", raw = "", inHead = false, hasBodyMarkup = false;

    function hidesContent(name, openingTag) {
        const attrs = new Map();
        for (const [, key, doubleQuoted, singleQuoted, unquoted] of openingTag.slice(name.length + 1, -1).matchAll(attributes)) {
            const normalized = key.toLowerCase();
            if (!attrs.has(normalized)) attrs.set(normalized, doubleQuoted ?? singleQuoted ?? unquoted ?? "");
        }
        return name === "template" || attrs.has("hidden") || (name === "dialog" && !attrs.has("open"))
            || /(?:^|;)\s*(?:display\s*:\s*none|visibility\s*:\s*(?:hidden|collapse))\s*(?:!important)?\s*(?:;|$)/i.test(attrs.get("style") ?? "");
    }

    return {hasBodyMarkup: () => hasBodyMarkup, read(bytes) {
        pending += decoder.decode(bytes, {stream: true});
        let offset = 0;
        while (offset < pending.length) {
            if (raw) {
                const end = pending.toLowerCase().indexOf(raw, offset);
                if (end < 0) {
                    // Only a split closing delimiter needs to survive the next read.
                    offset = Math.max(offset, pending.length - raw.length + 1);
                    break;
                }
                offset = end + raw.length;
                raw = "";
                continue;
            }

            const next = pending[offset];
            if (/\s/.test(next)) { offset++; continue; }
            if (next !== "<") {
                if (!inHead && !hiddenStack.length) hasBodyMarkup = true;
                const end = pending.indexOf("<", offset);
                offset = end < 0 ? pending.length : end;
                continue;
            }

            const rest = pending.slice(offset);
            if ("<!--".startsWith(rest)) break;
            if (rest.startsWith("<!--")) {
                raw = "-->";
                offset += 4;
                continue;
            }
            if (/^<[!?]/.test(rest)) {
                const end = rest.indexOf(">");
                if (end < 0) break;
                offset += end + 1;
                continue;
            }

            if (!inHead && !hiddenStack.length && rest.startsWith(marker)) return true;
            if (!inHead && !hiddenStack.length && marker.startsWith(rest)) break;

            const match = tag.exec(rest);
            if (!match) break;
            offset += match[0].length;
            const name = match[1].toLowerCase();
            const closing = match[0][1] === "/";
            if (name === "head") inHead = !closing;
            if (name === "body" && !closing) inHead = false;
            if (closing) {
                const ancestor = hiddenStack.lastIndexOf(name);
                if (ancestor >= 0) hiddenStack.length = ancestor;
                continue;
            }
            if (nonRendering.has(name)) {
                raw = "</" + name + ">";
                continue;
            }
            if (inHead || (headTags.has(name) && name !== "body" && name !== "html")) continue;
            const hidden = hiddenStack.length > 0 || hidesContent(name, match[0]);
            if (hidden) {
                if (!voidTags.has(name)) hiddenStack.push(name);
            } else if (visualTags.has(name)) {
                hasBodyMarkup = true;
            }
        }
        pending = pending.slice(offset);
        // Do not keep buffering a malformed tag indefinitely.
        if (pending.length > 128 * 1024) throw Error("Invalid storefront launch markup");
        return false;
    }};
}

async function openLaunchDocument(request, signal) {
    const response = await fetch(request, {signal});
    if (response.status >= 500) {
        void response.body?.cancel().catch(() => {});
        return null;
    }
    if (!response.ok || !response.body) return response;

    // Peek at a clone so the original response keeps its URL, headers, and streaming body.
    const reader = response.clone().body.getReader();
    const isHtml = /\btext\/html\b/i.test(response.headers.get("content-type") ?? "");
    const markup = isHtml ? launchMarkupProbe() : null;
    try {
        for (;;) {
            const chunk = await reader.read();
            if (chunk.done) {
                // A complete older storefront document may not carry the marker yet.
                if (markup?.hasBodyMarkup()) return response;
                void response.body.cancel().catch(() => {});
                return null;
            }
            if (markup ? markup.read(chunk.value) : chunk.value.byteLength > 0) return response;
        }
    } finally {
        // A tee branch's cancel promise can wait for the browser to consume the original.
        void reader.cancel().catch(() => {});
    }
}

async function cachedLaunch(request) {
    let timer;
    try {
        const lookup = async () => {
            const page = await caches.match(request, {cacheName: PAGE_CACHE}).catch(() => null);
            return page ?? await caches.match(request, {cacheName: STATIC_CACHE});
        };
        const cached = await Promise.race([
            lookup(),
            new Promise(resolve => { timer = setTimeout(() => resolve(null), LAUNCH_CACHE_WAIT_MS); })
        ]);
        return cached?.ok ? cached : null;
    } catch {
        return null;
    } finally {
        clearTimeout(timer);
    }
}

async function cacheLaunch(request, response) {
    const cache = await caches.open(PAGE_CACHE);
    await cache.put(request, response);
}

async function launchNavigation(request, url) {
    const controller = new AbortController();
    let timer, response;
    try {
        const deadline = new Promise(resolve => {
            timer = setTimeout(() => {
                resolve(null);
                controller.abort();
            }, LAUNCH_WAIT_MS);
        });
        response = await Promise.race([openLaunchDocument(request, controller.signal), deadline]);
    } catch {
        // A network or body failure may still have an exact saved page.
        controller.abort();
    } finally {
        clearTimeout(timer);
    }

    if (response) {
        const cacheable = response.ok && !response.redirected
            && response.headers.get("content-type")?.includes("text/html")
            && !/\bno-store\b/i.test(response.headers.get("cache-control") ?? "");
        const cacheWrite = cacheable ? cacheLaunch(request, response.clone()).catch(() => {}) : undefined;
        return {response, cacheWrite};
    }
    return {response: await cachedLaunch(request) ?? launchRecoveryPage(url, request.headers.get("accept-language"))};
}

// Each attempt is a real navigation so redirects, headers and streaming HTML are preserved.
const LAUNCH_RETRY_SCRIPT = `(() => {
    const key = "gokulLaunchRetry";
    const delays = [650, 1200];
    const previous = history.state?.[key];
    const continuing = performance.getEntriesByType("navigation")[0]?.type === "reload"
        && previous?.url === location.href
        && Number.isInteger(previous.attempt) && previous.attempt >= 0;
    const attempt = continuing ? previous.attempt : 0;
    function clearRetry() {
        try {
            const state = {...history.state};
            delete state[key];
            history.replaceState(state, "");
        } catch {}
    }
    document.addEventListener("click", event => {
        if (event.target?.closest?.("#launch-manual-retry")) clearRetry();
    });
    if (attempt >= delays.length) return;
    try {
        history.replaceState({...history.state, [key]: {
            url: location.href, attempt: attempt + 1
        }}, "");
    } catch {
        // Keep the manual link usable if the browser won't preserve the retry budget.
        return;
    }
    document.documentElement.classList.add("retrying");
    const timer = setTimeout(() => location.reload(), delays[attempt]);
    addEventListener("pagehide", () => clearTimeout(timer), {once: true});
    addEventListener("pageshow", event => {
        if (event.persisted) document.documentElement.classList.remove("retrying");
    });
})();`;

function launchRecoveryPage(url, language) {
    // Render a complete document from the worker, even after old app assets are evicted.
    const hindi = /^hi(?:[-,;\s]|$)/i.test(language ?? "");
    const title = hindi ? "फिर से कोशिश करें" : "Let’s get you back to Gokul";
    const message = hindi
        ? "पेज अभी नहीं खुल सका। अपना इंटरनेट कनेक्शन जाँचें और फिर कोशिश करें।"
        : "This page couldn’t load. Check your connection and try again.";
    const retry = hindi ? "फिर कोशिश करें" : "Try again";
    const reconnecting = hindi ? "Gokul से फिर जुड़ रहे हैं" : "Reconnecting to Gokul";
    const loading = hindi ? "पेज खोलने की कोशिश जारी है।" : "We’re still working to open your page.";
    const nonce = crypto.randomUUID();
    const href = url.href.replace(/[&"<>'`]/g, char => ({
        "&": "&amp;", '"': "&quot;", "<": "&lt;", ">": "&gt;", "'": "&#39;", "`": "&#96;"
    })[char]);
    return new Response(`<!doctype html>
<html lang="${hindi ? "hi" : "en"}">
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="theme-color" content="#980c31">
<title>${title} · Gokul</title>
<style>
*{box-sizing:border-box}
html{color-scheme:dark;background:#980c31}
body{margin:0;min-height:100vh;min-height:100dvh;display:grid;place-items:center;padding:max(24px,env(safe-area-inset-top)) max(24px,env(safe-area-inset-right)) max(24px,env(safe-area-inset-bottom)) max(24px,env(safe-area-inset-left));font:1rem/1.55 system-ui,sans-serif;color:#fff3dd;background:radial-gradient(ellipse at 50% 12%,#b74041 0%,transparent 58%),linear-gradient(145deg,#980c31,#4e0b26)}
main{width:min(100%,28rem);padding:clamp(1.5rem,6vw,3rem);text-align:center;border:1px solid #f9cf975e;border-radius:1.75rem;background:#5b0e2ada;box-shadow:0 1.25rem 4rem #2b07134d}
.brand{margin:0;font:700 clamp(2.5rem,12vw,4rem)/1 Georgia,serif;letter-spacing:-.04em}
.rule{display:block;width:2.5rem;height:2px;margin:1.3rem auto;background:#ffcb83;border-radius:1rem}
h1{margin:0;font-size:clamp(1.25rem,5vw,1.65rem);line-height:1.3}
.message{margin:.7rem 0 1.5rem;color:#fce3ca}
a{display:inline-flex;justify-content:center;align-items:center;min-height:48px;padding:.65rem 1.65rem;border-radius:2rem;background:#fff1d7;color:#631430;font-weight:700;text-decoration:none}
a:hover{background:#ffe2b2}
a:focus-visible{outline:3px solid #ffcf89;outline-offset:4px}
.progress,.retrying .failure{display:none}
.retrying .progress{display:block}
.icons{display:flex;justify-content:center;align-items:center;gap:.75rem;margin:0 auto 1.3rem;color:#ffd49a}
.icons svg{width:3rem;height:3rem;padding:.65rem;border:1px solid #ffd49a6b;border-radius:50%;animation:launch-pulse 1.6s ease-in-out infinite}
.icons svg:nth-child(2){animation-delay:.18s}
.icons svg:nth-child(3){animation-delay:.36s}
@keyframes launch-pulse{0%,75%,100%{transform:translateY(0);opacity:.68}35%{transform:translateY(-.35rem);opacity:1}}
@media(prefers-reduced-motion:reduce){.icons svg{animation:none}}
</style>
<script nonce="${nonce}">${LAUNCH_RETRY_SCRIPT}</script>
<main aria-label="Gokul">
  <p class="brand">Gokul</p><span class="rule" aria-hidden="true"></span>
  <div class="progress" role="status">
    <div class="icons" aria-hidden="true">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M3 9l9-5 9 5-9 5-9-5zm0 0v7l9 5 9-5V9m-9 5v7"/></svg>
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><path d="M3 9a14 14 0 0 1 18 0M6 12a9 9 0 0 1 12 0m-9 3a4.5 4.5 0 0 1 6 0"/><circle cx="12" cy="19" r="1" fill="currentColor" stroke="none"/></svg>
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M6 8h12l2 12H4L6 8zm3 1V6a3 3 0 0 1 6 0v3"/></svg>
    </div>
    <h1>${reconnecting}</h1>
    <p class="message">${loading}</p>
  </div>
  <div class="failure">
    <h1>${title}</h1>
    <p class="message">${message}</p>
    <a id="launch-manual-retry" href="${href}">${retry}</a>
  </div>
</main>
</html>`, {status: 503, headers: {
        "Content-Type": "text/html; charset=utf-8",
        "Cache-Control": "no-store",
        "Content-Security-Policy": `default-src 'none'; script-src 'nonce-${nonce}'; style-src 'unsafe-inline'; base-uri 'none'`,
        "Referrer-Policy": "no-referrer"
    }});
}

async function networkFirstNavigation(
    request
) {

    const cache =
        await caches.open(
            PAGE_CACHE
        );


    try {

        const response =
            await fetch(
                request
            );


        if (
            response.ok
        ) {

            await cache.put(
                request,
                response.clone()
            );
        }


        return response;

    } catch {

        const cached =
            await cache.match(
                request
            );


        if (cached) {
            return cached;
        }


        const offlinePage =
            await caches.match(
                OFFLINE_URL
            );


        if (offlinePage) {
            return offlinePage;
        }


        return Response.error();
    }
}

// Stage-specific push works while the app is closed; custom audio belongs to an explicitly activated page.
function notificationDestination(value, staff = false, marketing = false) {
    if (marketing) return typeof value === "string" && /^\/branches\/[1-9][0-9]{0,18}$/.test(value) ? value : "/profile#account-notifications";
    if (staff) return typeof value === "string" && (/^\/admin\/orders\/[A-Za-z0-9_%.-]{1,200}$/.test(value) || /^\/admin\/occasion-enquiries\?branch=[0-9]{1,20}&enquiry=[a-f0-9-]{36}$/.test(value)) ? value : "/admin/staff-notifications";
    return typeof value === "string" && (/^\/orders\/[A-Za-z0-9_%.-]{1,200}(?:#order-review)?$/.test(value) || /^\/occasions(?:#occasion-|\/requests\?enquiry=)[A-Za-z0-9_%.-]{1,200}$/.test(value)) ? value : "/profile#account-notifications";
}
let pushSequence = Promise.resolve();
self.addEventListener("push", event => {
    let payload;
    try {payload = event.data?.json();} catch {return;}
    if (!payload || !/^(?:staff:|marketing:)?[0-9]{1,20}$/.test(String(payload.eventId))) return;
    const eventId = String(payload.eventId);
    pushSequence = pushSequence.catch(() => {}).then(async () => {
        const cache = await caches.open(PUSH_DEDUPE_CACHE);
        const key = new Request(new URL("/__push_seen", self.location.origin));
        const previous = await cache.match(key);
        const seen = previous ? await previous.json() : [];
        if (Array.isArray(seen) && seen.includes(eventId)) return;
        const staff = eventId.startsWith("staff:");
        const marketing = eventId.startsWith("marketing:");
        const destination = notificationDestination(payload.url, staff, marketing);
        if (marketing && destination === "/profile#account-notifications") return;
        const custom = destination !== "/profile#account-notifications" && destination !== "/admin/staff-notifications";
        const title = custom && typeof payload.title === "string" ? payload.title.slice(0, 80) : "Gokul Sweets";
        const body = custom && typeof payload.body === "string" ? payload.body.slice(0, 500) : "A new account update is waiting in your notification inbox.";
        await self.registration.showNotification(title, {
            body, silent: false, ...(staff ? {vibrate: [300, 120, 300, 120, 500]} : {}), badge: "/notification-badge.svg",
            icon: "/icon-192.png", tag: marketing ? `gokul-${eventId}` : `gokul-${staff ? "staff" : "customer"}-${destination.split("#")[0]}`, renotify: staff, requireInteraction: staff,
            data: {url: destination, staff, marketing}
        });
        if (staff) {
            try {
                const windows = await self.clients.matchAll({type: "window", includeUncontrolled: true});
                for (const client of windows) {
                    if (new URL(client.url).pathname.startsWith("/admin")) {
                        try {client.postMessage({type: "gokul-staff-alert"});} catch { /* Client closed. */ }
                    }
                }
            } catch { /* Notification already displayed; client discovery cannot undo delivery or dedupe. */ }
        }
        await cache.put(key, new Response(JSON.stringify([...(Array.isArray(seen) ? seen : []), eventId].slice(-256)),
            {headers: {"Content-Type": "application/json"}}));
    });
    event.waitUntil(pushSequence);
});
self.addEventListener("notificationclick", event => {
    event.notification.close();
    event.waitUntil((async () => {
        const url = new URL(notificationDestination(event.notification.data?.url, event.notification.data?.staff === true, event.notification.data?.marketing === true), self.location.origin).href;
        const windows = await self.clients.matchAll({type: "window", includeUncontrolled: true});
        for (const client of windows) {
            const current = new URL(client.url);
            if (current.origin === self.location.origin && (current.pathname.startsWith("/admin") === (event.notification.data?.staff === true))) {
                await client.navigate(url); await client.focus(); return;
            }
        }
        await self.clients.openWindow(url);
    })());
});
