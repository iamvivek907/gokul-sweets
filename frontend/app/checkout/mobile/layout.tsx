import type {Metadata} from "next";
import type {ReactNode} from "react";

export const metadata: Metadata = {title: {absolute: "Checkout | Gokul Sweets"}};

export default function PageLayout({children}:{children:ReactNode}) {
    return children;
}
