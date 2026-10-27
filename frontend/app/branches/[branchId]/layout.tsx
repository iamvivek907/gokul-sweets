import type {Metadata} from "next";
import type {ReactNode} from "react";
import {branchDescription,branchPath,branchSchema,safeStructuredData,STORE_ORIGIN} from "@/lib/branchSeo";
import {serverBranches} from "@/lib/serverBranches";
type Props={children:ReactNode;params:Promise<{branchId:string}>};
async function resolveBranch(params:Props["params"]) {
    const {branchId}=await params;
    if(!/^[1-9]\d*$/.test(branchId))return undefined;
    return (await serverBranches()).find(branch=>branch.id===Number(branchId));
}
export async function generateMetadata({params}:Props):Promise<Metadata> {
    const branch=await resolveBranch(params);
    if(!branch)return {title:{absolute:"Branch Home | Gokul Sweets"}};
    const title=`${branch.name} | Gokul Sweets`,description=branchDescription(branch),url=STORE_ORIGIN+branchPath(branch.id);
    return {title:{absolute:title},description,alternates:{canonical:url},openGraph:{title,description,url,type:"website",siteName:"Gokul Sweets"}};
}
export default async function PageLayout({children,params}:Props) {
    const branch=await resolveBranch(params),schema=branch&&branchSchema(branch);
    return <>{schema&&<script type="application/ld+json" dangerouslySetInnerHTML={{__html:safeStructuredData(schema)}}/>}{children}</>;
}
