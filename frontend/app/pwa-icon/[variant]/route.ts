import {API_BASE_URL} from "@/lib/constants";

const defaults: Record<string, string> = {
    "192": "icon-192.png", "512": "icon-512.png", maskable: "icon-maskable-512.png", apple: "apple-default.png"
};

export async function GET(request: Request, {params}: {params: Promise<{variant: string}>}) {
    const {variant} = await params;
    if (!Object.hasOwn(defaults, variant)) return new Response(null, {status: 404});
    const revision = new URL(request.url).searchParams.get("v");
    if (revision !== null && !/^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i.test(revision)) {
        return new Response(null, {status: 404, headers: {"Cache-Control": "no-store"}});
    }
    try {
        const icon = await fetch(`${API_BASE_URL}/api/storefront/app-icon/${variant}${revision ? `?revision=${revision}` : ""}`, {
            cache: "no-store", signal: AbortSignal.timeout(2500)
        });
        if (icon.status === 404 && revision) {
            return new Response(null, {status: 307, headers: {
                Location: `/pwa-icon/${variant}`, "Cache-Control": "no-store"
            }});
        }
        if (icon.ok && icon.headers.get("content-type") === "image/png") {
            return new Response(icon.body, {
                headers: {"Content-Type": "image/png", "Cache-Control": "public, max-age=0, s-maxage=60, must-revalidate",
                    "X-Content-Type-Options": "nosniff"}
            });
        }
    } catch {
        // Keep the bundled icon available when the backend cannot respond.
    }
    return new Response(null, {status: 307, headers: {
        Location: new URL(`/icons/${defaults[variant]}`, request.url).href, "Cache-Control": "no-store"
    }});
}
