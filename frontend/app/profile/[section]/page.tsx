import {notFound} from "next/navigation";
import AppShell from "@/components/layout/AppShell";
import ProfileAccountExperience from "@/components/customer/ProfileAccountExperience";
import type {AccountSection} from "@/components/customer/CustomerAccountHub";
export default async function ProfileSectionPage({params}:{params:Promise<{section:string}>}){
 const {section}=await params;
 if(!["badges","orders","favourites","addresses","preferences","details"].includes(section))notFound();
 return <AppShell><ProfileAccountExperience initialSection={section as AccountSection}/></AppShell>;
}
