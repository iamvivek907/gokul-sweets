import type {
    Metadata,
    Viewport
} from "next";

import "./globals.css";

import AppProviders
    from "@/components/providers/AppProviders";

import {
    APP_DESCRIPTION,
    APP_NAME
} from "@/lib/constants";

export const metadata: Metadata = {
    title: {
        default: APP_NAME,
        template: `%s | ${APP_NAME}`
    },
    description: APP_DESCRIPTION,
    applicationName: APP_NAME,
    appleWebApp: {
        capable: true,
        statusBarStyle: "default",
        title: APP_NAME
    },
    formatDetection: {
        telephone: false
    }
};

export const viewport: Viewport = {
    width: "device-width",
    initialScale: 1,
    maximumScale: 1,
    themeColor: "#7a1625"
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
