import AppShell
    from "@/components/layout/AppShell";

import InstallAppBanner
    from "@/components/pwa/InstallAppBanner";
import CustomerIdentityPanel from "@/components/customer/CustomerIdentityPanel";

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
                <CustomerIdentityPanel />
                <section className="mt-6 rounded-3xl border border-[#e8d7c9] bg-white p-5 shadow-sm sm:p-6" aria-label="Rewards">
                    <h2 className="text-xl font-semibold text-[#241715]">Rewards</h2>
                    <p className="mt-2 text-sm leading-6 text-[#756763]">
                        Earned points are not available yet. A balance will appear here when the rewards programme is launched.
                    </p>
                </section>

            </div>

        </AppShell>
    );
}
