import {API_BASE_URL} from "@/lib/constants";
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

export const revalidate = 60;

export default async function manifest(): Promise<MetadataRoute.Manifest> {
    let revision: string | undefined;
    try {
        const response = await fetch(`${API_BASE_URL}/api/storefront/app-icon`, {
            next: {revalidate: 60}, signal: AbortSignal.timeout(1500)
        });
        if (response.ok) {
            const data = await response.json();
            if (typeof data.revision === "string" && /^[0-9a-f-]{36}$/i.test(data.revision)) revision = data.revision;
        }
    } catch {
        // The built-in install assets work even when the API is unavailable.
    }
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
                src: revision ? `/pwa-icon/192?v=${revision}` : versionedIcon("icon-192.png"),
                sizes: "192x192",
                type: "image/png"
            },
            {
                src: revision ? `/pwa-icon/512?v=${revision}` : versionedIcon("icon-512.png"),
                sizes: "512x512",
                type: "image/png"
            },
            {
                src: revision ? `/pwa-icon/maskable?v=${revision}` : versionedIcon("icon-maskable-512.png"),
                sizes: "512x512",
                type: "image/png",
                purpose: "maskable"
            }
        ]
    };
}