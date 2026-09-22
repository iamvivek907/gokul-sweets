import type {
    NextConfig
} from "next";


const nextConfig: NextConfig = {

    images: {

        remotePatterns: [

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