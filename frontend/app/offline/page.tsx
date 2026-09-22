import RetryConnectionButton
    from "@/components/common/RetryConnectionButton";

export default function OfflinePage() {

    return (
        <main
            className="
                flex
                min-h-screen
                items-center
                justify-center
                bg-[#fffaf3]
                px-6
            "
        >

            <div
                className="
                    max-w-md
                    text-center
                "
            >

                <div
                    className="
                        mx-auto
                        flex
                        h-20
                        w-20
                        items-center
                        justify-center
                        rounded-full
                        bg-[#fff0dc]
                        text-3xl
                    "
                >
                    ☁
                </div>

                <h1
                    className="
                        mt-6
                        text-2xl
                        font-bold
                        text-[#5d0f1b]
                    "
                >
                    You&apos;re offline
                </h1>

                <p
                    className="
                        mt-3
                        leading-6
                        text-[#756763]
                    "
                >
                    You can browse cached content,
                    but placing orders and making
                    payments require an internet
                    connection.
                </p>

                <RetryConnectionButton />

            </div>

        </main>
    );
}