const VERSION =
    "v1";

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
            IMAGE_CACHE
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