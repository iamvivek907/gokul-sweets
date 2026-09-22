import AdminLoginForm
    from "@/components/admin/AdminLoginForm";


export default function AdminLoginPage() {

    return (
        <main
            className="
                flex
                min-h-screen
                items-center
                justify-center
                bg-[#fffaf3]
                px-4
                py-10
            "
        >

            <section
                className="
                    w-full
                    max-w-md
                    rounded-3xl
                    border
                    border-[#eadfd6]
                    bg-white
                    p-6
                    shadow-[0_24px_80px_rgba(36,23,21,0.08)]

                    sm:p-8
                "
            >

                <div
                    className="
                        inline-flex
                        rounded-full
                        bg-[#fff1e9]
                        px-3
                        py-1
                        text-xs
                        font-bold
                        uppercase
                        tracking-[0.16em]
                        text-[#c88a20]
                    "
                >
                    Staff access
                </div>


                <h1
                    className="
                        mt-5
                        text-3xl
                        font-bold
                        tracking-tight
                        text-[#241715]
                    "
                >
                    Gokul Sweets Admin
                </h1>


                <p
                    className="
                        mt-2
                        text-sm
                        leading-6
                        text-[#756763]
                    "
                >
                    Sign in with your staff account to access the dashboard and the tools allowed by your role.
                </p>


                <AdminLoginForm />

            </section>

        </main>
    );
}