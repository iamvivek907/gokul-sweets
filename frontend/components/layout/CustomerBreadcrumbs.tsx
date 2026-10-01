"use client";
import LinkFeedback from "@/components/common/LinkFeedback";

import {T,useTranslation} from "@/lib/language";

import Link from "next/link";
import {usePathname} from "next/navigation";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";

export default function CustomerBreadcrumbs() {
    const translate = useTranslation();
    const pathname = usePathname();
    const {branch} = useSelectedBranch();
    if (pathname !== "/menu" && pathname !== "/cart") return null;

    return <nav className="gokul-customer-breadcrumbs" aria-label="Breadcrumb">
        <Link href="/"><T text="Home" /><LinkFeedback /></Link><span aria-hidden="true">/</span>
        <Link href="/branches"><T text="Branches" /><LinkFeedback /></Link><span aria-hidden="true">/</span>
        {pathname === "/cart" ? <>
            <Link href="/menu">{branch?.name ?? translate("Menu")}<LinkFeedback /></Link><span aria-hidden="true">/</span>
            <span aria-current="page"><T text="Cart" /></span>
        </> : <span aria-current="page">{branch?.name ?? translate("Menu")} <T text="menu" /></span>}
    </nav>;
}
