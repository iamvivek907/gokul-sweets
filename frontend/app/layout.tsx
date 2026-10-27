import type {
    Metadata,
    Viewport
} from "next";

import "./globals.css";
import "@/components/layout/customer-ease.css";
import "@/components/common/brand-experience.css";

import AppProviders
    from "@/components/providers/AppProviders";

import {
    APP_DESCRIPTION,
    APP_NAME
} from "@/lib/constants";

export const metadata: Metadata = {
    title: {
        default: "Gokul Sweets | Fresh Sweets & Snacks",
        template: `%s | ${APP_NAME}`
    },
    description: APP_DESCRIPTION,
    metadataBase: new URL("https://gokulsweets.in"),
    openGraph: {type: "website", siteName: APP_NAME, title: "Gokul Sweets | Fresh Sweets, Snacks & Meals", description: APP_DESCRIPTION},
    twitter: {card: "summary", title: "Gokul Sweets | Fresh Sweets, Snacks & Meals", description: APP_DESCRIPTION},
    ...(process.env.NEXT_PUBLIC_API_URL?.includes("api-dev.gokulsweets.in") ? {robots: {index: false, follow: false}} : {}),
    applicationName: "Gokul",
    appleWebApp: {
        capable: true,
        statusBarStyle: "default",
        title: "Gokul"
    },
    formatDetection: {
        telephone: false
    }
};

export const viewport: Viewport = {
    width: "device-width",
    initialScale: 1,
    maximumScale: 1,
    userScalable: false,
    themeColor: "#980c31"
};

export default function RootLayout({
    children
}: Readonly<{
    children: React.ReactNode;
}>) {
    return (
        <html
            lang="en"
            data-scroll-behavior="smooth"
        >
            <body>
                <AppProviders>
                    {children}
                </AppProviders>
            </body>
        </html>
    );
}
