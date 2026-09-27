"use client";

import Link from "next/link";
import {usePathname} from "next/navigation";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";

export default function CustomerBreadcrumbs() {
    const pathname = usePathname();
    const {branch} = useSelectedBranch();
    if (pathname !== "/menu" && pathname !== "/cart") return null;

    return <nav className="gokul-customer-breadcrumbs" aria-label="Breadcrumb">
        <Link href="/">Home</Link><span aria-hidden="true">/</span>
        <Link href="/branches">Branches</Link><span aria-hidden="true">/</span>
        {pathname === "/cart" ? <>
            <Link href="/menu">{branch?.name ?? "Menu"}</Link><span aria-hidden="true">/</span>
            <span aria-current="page">Cart</span>
        </> : <span aria-current="page">{branch?.name ?? "Menu"} menu</span>}
    </nav>;
}
