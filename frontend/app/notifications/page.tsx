import AppShell from "@/components/layout/AppShell";
import CustomerNotificationsPage from "@/components/customer/CustomerNotificationsPage";

export default async function NotificationsPage({searchParams}: {searchParams: Promise<{from?: string | string[]}>}) {
    const {from} = await searchParams;
    return <AppShell showSocialPopup={false}><CustomerNotificationsPage initialFrom={typeof from === "string" ? from : undefined} /></AppShell>;
}
