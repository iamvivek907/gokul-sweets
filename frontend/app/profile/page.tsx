import AppShell
    from "@/components/layout/AppShell";

import InstallAppBanner
    from "@/components/pwa/InstallAppBanner";

export default function ProfilePage() {

    return (
        <AppShell>

            <div
                className="
                    mx-auto
                    max-w-xl
                "
            >

                <p
                    className="
                        text-xs
                        font-semibold
                        uppercase
                        tracking-wide
                        text-[#c88a20]
                    "
                >
                    Your account
                </p>

                <h1
                    className="
                        mt-1
                        text-2xl
                        font-bold
                    "
                >
                    Profile
                </h1>

                <InstallAppBanner />

            </div>

        </AppShell>
    );
}