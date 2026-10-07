import type {Metadata} from "next";
import {notFound} from "next/navigation";
import AppShell from "@/components/layout/AppShell";
import ProfileAccountExperience from "@/components/customer/ProfileAccountExperience";
import type {AccountSection} from "@/components/customer/CustomerAccountHub";
const sectionTitles: Record<string,string> = {badges:"Your Badges",orders:"Order History",favourites:"Your Favourites",addresses:"Your Addresses",preferences:"Your Preferences",details:"Your Details"};
export async function generateMetadata({params}:{params:Promise<{section:string}>}):Promise<Metadata> {
 const {section}=await params;
 return {title:{absolute:`${sectionTitles[section]??"Your Account"} | Gokul Sweets`}};
}
export default async function ProfileSectionPage({params}:{params:Promise<{section:string}>}){
 const {section}=await params;
 if(!["badges","orders","favourites","addresses","preferences","details"].includes(section))notFound();
 return <AppShell><ProfileAccountExperience initialSection={section as AccountSection}/></AppShell>;
}
