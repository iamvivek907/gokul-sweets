import type {Metadata} from "next";
import type {ReactNode} from "react";

export const metadata: Metadata = {title: {absolute: "Choose a Branch | Gokul Sweets"}};

export default function PageLayout({children}:{children:ReactNode}) {
    return children;
}
