"use client";

import {
    FormEvent,
    useState
} from "react";

import {
    useRouter
} from "next/navigation";

import {
    useAdminAuth
} from "@/contexts/AdminAuthContext";


export default function AdminLoginForm() {

    const router =
        useRouter();


    const {
        login
    } =
        useAdminAuth();


    const [
        username,
        setUsername
    ] =
        useState("");


    const [
        password,
        setPassword
    ] =
        useState("");


    const [
        submitting,
        setSubmitting
    ] =
        useState(false);


    const [
        error,
        setError
    ] =
        useState<string | null>(
            null
        );


    async function handleSubmit(
        event: FormEvent<HTMLFormElement>
    ) {

        event.preventDefault();


        if (submitting) {

            return;
        }


        const normalizedUsername =
            username.trim();


        if (!normalizedUsername) {

            setError(
                "Enter your username."
            );

            return;
        }


        if (!password) {

            setError(
                "Enter your password."
            );

            return;
        }


        setSubmitting(
            true
        );


        setError(
            null
        );


        try {

            await login(
                normalizedUsername,
                password
            );


            router.replace(
                "/admin"
            );

        } catch (exception) {

            setError(
                exception instanceof Error
                    ? exception.message
                    : "Unable to sign in."
            );

        } finally {

            setSubmitting(
                false
            );
        }
    }


    return (
        <form
            onSubmit={
                handleSubmit
            }
            className="
                mt-8
                space-y-5
            "
        >

            <div>

                <label
                    htmlFor="admin-username"
                    className="
                        mb-2
                        block
                        text-sm
                        font-semibold
                        text-[#241715]
                    "
                >
                    Username
                </label>


                <input
                    id="admin-username"
                    type="text"
                    autoComplete="username"
                    value={
                        username
                    }
                    onChange={
                        event => {

                            setUsername(
                                event.target.value
                            );


                            if (error) {

                                setError(
                                    null
                                );
                            }
                        }
                    }
                    disabled={
                        submitting
                    }
                    placeholder="Enter username"
                    className="
                        min-h-12
                        w-full
                        rounded-xl
                        border
                        border-[#eadfd6]
                        bg-white
                        px-4
                        text-[#241715]
                        outline-none
                        transition

                        placeholder:text-[#a99a94]

                        focus:border-[#c88a20]
                        focus:ring-4
                        focus:ring-[#f6dfad]/40

                        disabled:cursor-not-allowed
                        disabled:bg-[#faf7f4]
                        disabled:opacity-70
                    "
                />

            </div>


            <div>

                <label
                    htmlFor="admin-password"
                    className="
                        mb-2
                        block
                        text-sm
                        font-semibold
                        text-[#241715]
                    "
                >
                    Password
                </label>


                <input
                    id="admin-password"
                    type="password"
                    autoComplete="current-password"
                    value={
                        password
                    }
                    onChange={
                        event => {

                            setPassword(
                                event.target.value
                            );


                            if (error) {

                                setError(
                                    null
                                );
                            }
                        }
                    }
                    disabled={
                        submitting
                    }
                    placeholder="Enter password"
                    className="
                        min-h-12
                        w-full
                        rounded-xl
                        border
                        border-[#eadfd6]
                        bg-white
                        px-4
                        text-[#241715]
                        outline-none
                        transition

                        placeholder:text-[#a99a94]

                        focus:border-[#c88a20]
                        focus:ring-4
                        focus:ring-[#f6dfad]/40

                        disabled:cursor-not-allowed
                        disabled:bg-[#faf7f4]
                        disabled:opacity-70
                    "
                />

            </div>


            {error && (

                <div
                    role="alert"
                    className="
                        rounded-xl
                        border
                        border-red-200
                        bg-red-50
                        px-4
                        py-3
                        text-sm
                        font-medium
                        leading-6
                        text-red-700
                    "
                >
                    {error}
                </div>

            )}


            <button
                type="submit"
                disabled={
                    submitting
                }
                className="
                    flex
                    min-h-12
                    w-full
                    items-center
                    justify-center
                    rounded-xl
                    bg-[#7a1625]
                    px-5
                    font-semibold
                    text-white
                    transition

                    hover:bg-[#5d0f1b]

                    focus-visible:outline-none
                    focus-visible:ring-4
                    focus-visible:ring-[#c88a20]/30

                    disabled:cursor-not-allowed
                    disabled:opacity-60
                "
            >

                {
                    submitting
                        ? "Signing in..."
                        : "Sign in to Admin"
                }

            </button>

        </form>
    );
}