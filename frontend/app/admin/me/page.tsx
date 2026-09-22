"use client";

import Link
    from "next/link";

import {
    useAdminAuth
} from "@/contexts/AdminAuthContext";


export default function MyDetailsPage() {

    const {
        profile
    } =
        useAdminAuth();


    return (
        <div className="px-4 py-6 sm:px-6 sm:py-8 lg:px-8">

            <div className="mx-auto max-w-6xl">

                <div>

                    <p
                        className="
                            text-xs
                            font-semibold
                            uppercase
                            tracking-[0.16em]
                            text-[#c88a20]
                        "
                    >
                        My Workforce
                    </p>


                    <h1
                        className="
                            mt-2
                            text-3xl
                            font-bold
                            tracking-tight
                            text-[#241715]
                            sm:text-4xl
                        "
                    >
                        My Details
                    </h1>


                    <p
                        className="
                            mt-3
                            max-w-3xl
                            text-sm
                            leading-6
                            text-[#756763]
                        "
                    >
                        View your own staff information, leave requests, attendance and payroll.
                    </p>

                </div>


                <section
                    className="
                        mt-6
                        overflow-hidden
                        rounded-2xl
                        border
                        border-[#eadfd6]
                        bg-white
                    "
                >

                    <div
                        className="
                            border-b
                            border-[#eadfd6]
                            bg-[#fffaf3]
                            px-5
                            py-4
                        "
                    >

                        <h2
                            className="
                                font-bold
                                text-[#241715]
                            "
                        >
                            My Profile
                        </h2>

                    </div>


                    <div
                        className="
                            grid
                            gap-4
                            p-5
                            sm:grid-cols-2
                        "
                    >

                        <ProfileField
                            label="Name"
                            value={
                                profile?.fullName
                                ?? "—"
                            }
                        />


                        <ProfileField
                            label="Role"
                            value={
                                profile?.roleName
                                ?? "—"
                            }
                        />

                    </div>

                </section>


                <div
                    className="
                        mt-6
                        grid
                        gap-5
                        md:grid-cols-3
                    "
                >

                    <SelfServiceCard
                        href="/admin/me/leave-requests"
                        title="My Leave"
                        description="Submit leave requests, check approval status, correct sent-back requests and resubmit."
                    />


                    <SelfServiceCard
                        href="/admin/me/attendance"
                        title="My Attendance"
                        description="Submit daily attendance, review attendance history and track approval status."
                    />


                    <SelfServiceCard
                        href="/admin/me/payroll"
                        title="My Payroll"
                        description="View earned balance, earnings history and request partial payments."
                    />

                </div>

            </div>

        </div>
    );
}


function ProfileField({
    label,
    value
}: {
    label: string;
    value: string;
}) {

    return (
        <div
            className="
                rounded-xl
                border
                border-[#eadfd6]
                bg-white
                p-4
            "
        >

            <p
                className="
                    text-xs
                    font-semibold
                    uppercase
                    tracking-0.1em
                    text-[#756763]
                "
            >
                {label}
            </p>


            <p
                className="
                    mt-2
                    text-base
                    font-bold
                    text-[#241715]
                "
            >
                {value}
            </p>

        </div>
    );
}


function SelfServiceCard({
    href,
    title,
    description
}: {
    href: string;
    title: string;
    description: string;
}) {

    return (
        <Link
            href={
                href
            }
            className="
                group
                rounded-2xl
                border
                border-[#eadfd6]
                bg-white
                p-5
                transition

                hover:border-[#c88a20]
                hover:bg-[#fffaf3]
            "
        >

            <p
                className="
                    text-lg
                    font-bold
                    text-[#241715]
                "
            >
                {title}
            </p>


            <p
                className="
                    mt-2
                    text-sm
                    leading-6
                    text-[#756763]
                "
            >
                {description}
            </p>


            <p
                className="
                    mt-4
                    text-sm
                    font-semibold
                    text-[#7a1625]
                "
            >
                Open →
            </p>

        </Link>
    );
}
