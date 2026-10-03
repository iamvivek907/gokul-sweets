import AppShell from "@/components/layout/AppShell";
import CustomerNotificationsPage from "@/components/customer/CustomerNotificationsPage";

export default function NotificationsPage() {
    return <AppShell showSocialPopup={false}><CustomerNotificationsPage /></AppShell>;
}
