import type { MetadataRoute } from "next";

export default function manifest(): MetadataRoute.Manifest {
    return {
        id: "/",

        name: "Gokul Sweets",

        short_name: "Gokul Sweets",

        description:
            "Order fresh sweets, snacks and meals for pickup.",

        start_url: "/",

        scope: "/",

        display: "standalone",

        background_color: "#fffaf3",

        theme_color: "#7a1625",

        orientation: "portrait",

        categories: [
            "food",
            "shopping"
        ],

        icons: [
            {
                src: "/icons/icon-192.png",
                sizes: "192x192",
                type: "image/png"
            },
            {
                src: "/icons/icon-512.png",
                sizes: "512x512",
                type: "image/png"
            },
            {
                src: "/icons/icon-maskable-512.png",
                sizes: "512x512",
                type: "image/png",
                purpose: "maskable"
            }
        ]
    };
}