"use client";

import {
    useEffect,
    useState
} from "react";


const SOCIAL_POPUP_STORAGE_KEY =
    "gokul-social-follow-popup-seen";


const INSTAGRAM_URL =
    "https://www.instagram.com/_gokulsweets";

const FACEBOOK_URL =
    "https://www.facebook.com/visitgokulsweets";


export default function SocialFollowPopup() {

    const [
        visible,
        setVisible
    ] =
        useState(false);


    useEffect(
        () => {

            const alreadySeen =
                window.localStorage.getItem(
                    SOCIAL_POPUP_STORAGE_KEY
                );


            if (
                alreadySeen === "true"
            ) {

                return;
            }


            const timer =
                window.setTimeout(
                    () => {

                        setVisible(
                            true
                        );
                    },
                    4500
                );


            return () => {

                window.clearTimeout(
                    timer
                );
            };

        },
        []
    );


    function dismiss() {

        window.localStorage.setItem(
            SOCIAL_POPUP_STORAGE_KEY,
            "true"
        );


        setVisible(
            false
        );
    }


    function handleSocialClick() {

        window.localStorage.setItem(
            SOCIAL_POPUP_STORAGE_KEY,
            "true"
        );


        setVisible(
            false
        );
    }


    if (
        !visible
    ) {

        return null;
    }


    return (
        <div
            className="
                fixed
                bottom-[calc(5.5rem+env(safe-area-inset-bottom))]
                left-1/2
                z-[80]
                w-[calc(100vw-2rem)]
                max-w-md
                -translate-x-1/2
            "
            role="dialog"
            aria-modal="false"
            aria-labelledby="social-follow-title"
        >

            <div
                className="
                    relative
                    w-full
                    overflow-hidden
                    rounded-3xl
                    border
                    border-[#eadfd6]
                    bg-[#fffdf9]
                    p-5
                    shadow-2xl
                "
            >

                <button
                    type="button"
                    aria-label="Dismiss social media popup"
                    onClick={
                        dismiss
                    }
                    className="
                        absolute
                        right-3
                        top-3
                        flex
                        h-9
                        w-9
                        items-center
                        justify-center
                        rounded-full
                        border
                        border-[#eadfd6]
                        bg-white
                        text-lg
                        font-semibold
                        text-[#756763]
                        transition

                        hover:bg-[#fffaf3]
                        active:scale-95
                    "
                >
                    ×
                </button>


                <p
                    className="
                        text-xs
                        font-bold
                        uppercase
                        tracking-[0.14em]
                        text-[#c88a20]
                    "
                >
                    Stay connected
                </p>


                <h2
                    id="social-follow-title"
                    className="
                        mt-1
                        pr-9
                        text-xl
                        font-extrabold
                        tracking-tight
                        text-[#241715]
                    "
                >
                    Follow Gokul Sweets
                </h2>


                <p
                    className="
                        mt-2
                        text-sm
                        leading-6
                        text-[#756763]
                    "
                >
                    See new sweets, festive specials and
                    shop updates on our social pages.
                </p>


                <div
                    className="
                        mt-4
                        grid
                        grid-cols-2
                        gap-3
                    "
                >

                    <a
                        href={
                            INSTAGRAM_URL
                        }
                        target="_blank"
                        rel="noreferrer"
                        onClick={
                            handleSocialClick
                        }
                        className="
                            flex
                            min-h-12
                            min-w-0
                            items-center
                            justify-center
                            rounded-xl
                            border-2
                            border-[#7a1625]
                            bg-white
                            px-3
                            text-sm
                            font-extrabold
                            text-[#7a1625]!
                            shadow-sm
                            transition

                            hover:bg-[#fff2d2]
                            active:scale-95
                        "
                    >
                        Instagram
                    </a>


                    <a
                        href={
                            FACEBOOK_URL
                        }
                        target="_blank"
                        rel="noreferrer"
                        onClick={
                            handleSocialClick
                        }
                        className="
                            flex
                            min-h-12
                            min-w-0
                            items-center
                            justify-center
                            rounded-xl
                            border-2
                            border-[#7a1625]
                            bg-[#7a1625]
                            px-3
                            text-sm
                            font-extrabold
                            text-white!
                            shadow-sm
                            transition

                            hover:bg-[#5d0f1b]
                            active:scale-95
                        "
                    >
                        Facebook
                    </a>

                </div>


                <button
                    type="button"
                    onClick={
                        dismiss
                    }
                    className="
                        mt-3
                        w-full
                        rounded-lg
                        py-2
                        text-center
                        text-xs
                        font-semibold
                        text-[#756763]
                        transition

                        hover:bg-[#fffaf3]
                        hover:text-[#241715]
                    "
                >
                    Not now
                </button>

            </div>

        </div>
    );
}
