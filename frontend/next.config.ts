import type {
    NextConfig
} from "next";


const configuredMediaUrl = process.env.NEXT_PUBLIC_MEDIA_BASE_URL;
const configuredMediaPattern = configuredMediaUrl ? (() => {
    const url = new URL(configuredMediaUrl);
    if (url.protocol !== "https:") throw new Error("NEXT_PUBLIC_MEDIA_BASE_URL must use HTTPS.");
    return {protocol: "https" as const, hostname: url.hostname, port: url.port, pathname: "/**"};
})() : null;
const nextConfig: NextConfig = {

    images: {

        remotePatterns: [
            ...(configuredMediaPattern ? [configuredMediaPattern] : []),
            {protocol: "https", hostname: "**.r2.dev", pathname: "/**"},

            /*
             * Local development images/API-hosted images.
             */
            {
                protocol: "http",
                hostname: "localhost",
                port: "8080",
                pathname: "/**"
            },

            /*
             * Cloudflare R2 development public URL.
             */
            {
                protocol: "https",
                hostname:
                    "pub-1486d596635f4a65aa86b4afd04bec85.r2.dev",
                pathname: "/**"
            }
        ]
    },


    async headers() {

        return [
            {
                source: "/sw.js",

                headers: [
                    {
                        key: "Cache-Control",
                        value:
                            "no-cache, no-store, must-revalidate"
                    }
                ]
            }
        ];
    }
};


export default nextConfig;