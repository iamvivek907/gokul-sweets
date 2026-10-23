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

            event.respondWith(
                networkFirstNavigation(
                    request
                )
            );

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
