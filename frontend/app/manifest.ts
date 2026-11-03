import type { MetadataRoute } from "next";
import {createHash} from "node:crypto";
import {readFileSync} from "node:fs";
import {join} from "node:path";

// Keep the manifest URL and app identity stable; replace only changed icon URLs.
function versionedIcon(filename: string) {
    const bytes = readFileSync(join(process.cwd(), "public", "icons", filename));
    const version = createHash("sha256").update(bytes).digest("hex").slice(0, 16);
    return `/icons/${filename}?v=${version}`;
}

export default function manifest(): MetadataRoute.Manifest {
    return {
        id: "/",

        name: "Gokul",

        short_name: "Gokul",

        description:
            "Order fresh sweets, snacks and meals for pickup.",

        start_url: "/",

        scope: "/",

        display: "standalone",

        background_color: "#980c31",

        theme_color: "#980c31",

        orientation: "portrait",

        categories: [
            "food",
            "shopping"
        ],

        icons: [
            {
                src: versionedIcon("icon-192.png"),
                sizes: "192x192",
                type: "image/png"
            },
            {
                src: versionedIcon("icon-512.png"),
                sizes: "512x512",
                type: "image/png"
            },
            {
                src: versionedIcon("icon-maskable-512.png"),
                sizes: "512x512",
                type: "image/png",
                purpose: "maskable"
            }
        ]
    };
}