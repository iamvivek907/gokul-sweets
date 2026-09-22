"use client";

import {
    useEffect
} from "react";


export default function ServiceWorkerRegistration() {

    useEffect(() => {

        if (
            !(
                "serviceWorker"
                in navigator
            )
        ) {
            return;
        }


        /*
         * IMPORTANT:
         *
         * Do not keep our PWA service worker active
         * while running Next.js development mode.
         *
         * It can interfere with Turbopack/HMR and
         * App Router navigation because it may serve
         * cached frontend assets or navigations.
         */
        if (
            process.env.NODE_ENV
            !== "production"
        ) {

            async function cleanupDevelopmentPwa() {

                try {

                    const registrations =
                        await navigator
                            .serviceWorker
                            .getRegistrations();


                    await Promise.all(
                        registrations.map(
                            registration =>
                                registration.unregister()
                        )
                    );


                    const cacheNames =
                        await caches.keys();


                    const gokulCaches =
                        cacheNames.filter(
                            cacheName =>
                                cacheName.startsWith(
                                    "gokul-"
                                )
                        );


                    await Promise.all(
                        gokulCaches.map(
                            cacheName =>
                                caches.delete(
                                    cacheName
                                )
                        )
                    );


                    console.info(
                        "Development PWA caches and service workers cleared."
                    );

                } catch (error) {

                    console.warn(
                        "Unable to clean development PWA state:",
                        error
                    );
                }
            }


            void cleanupDevelopmentPwa();

            return;
        }


        /*
         * Production only.
         */
        async function registerServiceWorker() {

            try {

                await navigator
                    .serviceWorker
                    .register(
                        "/sw.js",
                        {
                            scope: "/"
                        }
                    );


                console.info(
                    "Gokul Sweets service worker registered."
                );

            } catch (error) {

                console.error(
                    "Service worker registration failed:",
                    error
                );
            }
        }


        void registerServiceWorker();

    }, []);


    return null;
}