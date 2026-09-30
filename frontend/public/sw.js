const VERSION =
    "v2";

const PUSH_DEDUPE_CACHE = "gokul-push-dedupe-v1";

const STATIC_CACHE =
    `gokul-static-${VERSION}`;

const PAGE_CACHE =
    `gokul-pages-${VERSION}`;

const IMAGE_CACHE =
    `gokul-images-${VERSION}`;

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
                cacheFirst(
                    request,
                    IMAGE_CACHE
                )
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


async function cacheFirst(
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


    if (cached) {
        return cached;
    }


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
function notificationDestination(value) {
    return typeof value === "string" && (/^\/orders\/[A-Za-z0-9_%.-]{1,200}$/.test(value) || /^\/occasions#occasion-[A-Za-z0-9_%.-]{1,200}$/.test(value)) ? value : "/profile#account-notifications";
}
let pushSequence = Promise.resolve();
self.addEventListener("push", event => {
    let payload;
    try {payload = event.data?.json();} catch {return;}
    if (!payload || !/^[0-9]{1,20}$/.test(String(payload.eventId))) return;
    const eventId = String(payload.eventId);
    pushSequence = pushSequence.catch(() => {}).then(async () => {
        const cache = await caches.open(PUSH_DEDUPE_CACHE);
        const key = new Request(new URL("/__push_seen", self.location.origin));
        const previous = await cache.match(key);
        const seen = previous ? await previous.json() : [];
        if (Array.isArray(seen) && seen.includes(eventId)) return;
        const destination = notificationDestination(payload.url);
        const custom = destination !== "/profile#account-notifications";
        const title = custom && typeof payload.title === "string" ? payload.title.slice(0, 80) : "Gokul Sweets";
        const body = custom && typeof payload.body === "string" ? payload.body.slice(0, 240) : "A new account update is waiting in your notification inbox.";
        await self.registration.showNotification(title, {
            body, badge: "/notification-badge.svg",
            icon: "/icon-192.png", tag: `gokul-event-${eventId}`, renotify: false,
            data: {url: destination}
        });
        await cache.put(key, new Response(JSON.stringify([...(Array.isArray(seen) ? seen : []), eventId].slice(-256)),
            {headers: {"Content-Type": "application/json"}}));
    });
    event.waitUntil(pushSequence);
});
self.addEventListener("notificationclick", event => {
    event.notification.close();
    event.waitUntil((async () => {
        const url = new URL(notificationDestination(event.notification.data?.url), self.location.origin).href;
        const windows = await self.clients.matchAll({type: "window", includeUncontrolled: true});
        for (const client of windows) {
            const current = new URL(client.url);
            if (current.origin === self.location.origin && !current.pathname.startsWith("/admin")) {
                await client.navigate(url); await client.focus(); return;
            }
        }
        await self.clients.openWindow(url);
    })());
});
