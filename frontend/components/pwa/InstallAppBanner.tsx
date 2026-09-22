"use client";

import {
    useState
} from "react";

import {
    usePwaInstall
} from "@/hooks/usePwaInstall";

export default function InstallAppBanner() {

    const {
        canInstall,
        isInstalled,
        isIOS,
        install
    } = usePwaInstall();

    const [
        showIOSHelp,
        setShowIOSHelp
    ] =
        useState(false);

    if (isInstalled) {
        return null;
    }

    if (
        !canInstall &&
        !isIOS
    ) {

        return null;
    }

    async function handleInstall() {

        if (isIOS) {

            setShowIOSHelp(
                true
            );

            return;
        }

        await install();
    }

    return (
        <>
            <button
                type="button"
                onClick={
                    handleInstall
                }
                className="
                    mt-4
                    flex
                    w-full
                    items-center
                    justify-between
                    gap-4
                    rounded-2xl
                    border
                    border-[#e0ad54]
                    bg-[#fff3d5]
                    p-4
                    text-left
                    shadow-sm
                    transition
                    active:scale-[0.99]
                "
            >

                <div
                    className="
                        flex
                        items-center
                        gap-3
                    "
                >

                    <div
                        className="
                            flex
                            h-11
                            w-11
                            items-center
                            justify-center
                            rounded-xl
                            bg-white
                            text-xl
                        "
                    >
                        ↓
                    </div>

                    <div>

                        <p
                            className="
                                font-bold
                                text-[#5d0f1b]
                            "
                        >
                            Install Gokul Sweets
                        </p>

                        <p
                            className="
                                mt-1
                                text-xs
                                text-[#756763]
                            "
                        >
                            Faster access from your home screen
                        </p>

                    </div>

                </div>

                <span
                    className="
                        text-xl
                        text-[#7a1625]
                    "
                >
                    ›
                </span>

            </button>

            {
                showIOSHelp && (

                    <div
                        className="
                            fixed
                            inset-0
                            z-110
                            flex
                            items-end
                            bg-black/40
                        "
                        onClick={() =>
                            setShowIOSHelp(false)
                        }
                    >

                        <div
                            className="
                                w-full
                                rounded-t-3xl
                                bg-white
                                p-6
                            "
                            onClick={
                                event =>
                                    event.stopPropagation()
                            }
                        >

                            <h2
                                className="
                                    text-xl
                                    font-bold
                                "
                            >
                                Install Gokul Sweets
                            </h2>

                            <p
                                className="
                                    mt-2
                                    text-sm
                                    leading-6
                                    text-[#756763]
                                "
                            >
                                Add the app to your
                                iPhone home screen
                                for quicker access.
                            </p>

                            <div
                                className="
                                    mt-5
                                    space-y-4
                                "
                            >

                                <div>
                                    <strong>
                                        1.
                                    </strong>
                                    {" "}
                                    Tap the Share button
                                    in your browser.
                                </div>

                                <div>
                                    <strong>
                                        2.
                                    </strong>
                                    {" "}
                                    Select
                                    {" "}
                                    <strong>
                                        Add to Home Screen
                                    </strong>.
                                </div>

                                <div>
                                    <strong>
                                        3.
                                    </strong>
                                    {" "}
                                    Tap
                                    {" "}
                                    <strong>
                                        Add
                                    </strong>.
                                </div>

                            </div>

                            <button
                                type="button"
                                onClick={() =>
                                    setShowIOSHelp(false)
                                }
                                className="
                                    mt-6
                                    min-h-12
                                    w-full
                                    rounded-xl
                                    bg-[#7a1625]
                                    px-5
                                    font-semibold
                                    text-white
                                "
                            >
                                Got it
                            </button>

                        </div>

                    </div>

                )
            }
        </>
    );
}