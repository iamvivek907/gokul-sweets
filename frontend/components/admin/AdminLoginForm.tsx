"use client";

import {
    FormEvent,
    useState
} from "react";

import {
    useRouter
} from "next/navigation";

import {useAdminAuth} from "@/contexts/AdminAuthContext";
import {StaffEnrollmentRequired, setupStaffMfa, confirmStaffMfa} from "@/services/adminApi";


export default function AdminLoginForm() {

    const router =
        useRouter();


    const {
        login, refresh
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


    const [code, setCode] = useState("");
    const [enrollment, setEnrollment] = useState<{token: string; secret: string; uri: string} | null>(null);
    const [recovery, setRecovery] = useState<string[]>([]);

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


        if (!password && !enrollment) {

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

            if (enrollment) {
                const result = await confirmStaffMfa(enrollment.token, code);
                setRecovery(result.recoveryCodes);
                setEnrollment(null);
                setPassword("");
                return;
            }
            await login(normalizedUsername, password, code || undefined);
            setPassword("");


            router.replace(
                "/admin"
            );

        } catch (exception) {
            if (exception instanceof StaffEnrollmentRequired) {
                try {
                    const setup = await setupStaffMfa(exception.token);
                    setEnrollment({token: exception.token, ...setup});
                    setPassword(""); setCode("");
                } catch (setupError) {setError(setupError instanceof Error ? setupError.message : "Enrollment unavailable.");}
                return;
            }
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


            {!enrollment && <div>

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

            </div>}

            {enrollment && <div className="rounded-xl border p-4 text-sm">
                <p className="font-bold">Set up your authenticator</p>
                <p>In your authenticator app, add this setup key. Then enter its current six-digit code.</p>
                <p className="my-2 select-text break-all font-mono" aria-label="Authenticator setup key">{enrollment.secret}</p>
                <p>Keep the recovery codes shown after confirmation in a safe place.</p>
            </div>}
            <div><label htmlFor="admin-mfa" className="mb-2 block text-sm font-semibold">{enrollment ? "Authenticator code" : "Authenticator or recovery code (if required)"}</label>
                <input id="admin-mfa" autoComplete="one-time-code" inputMode="numeric" value={code}
                    onChange={event => setCode(event.target.value)} className="min-h-12 w-full rounded-xl border p-3" />
            </div>
            {recovery.length > 0 && <div role="status" className="rounded-xl border p-4">
                <h2 className="font-bold">Save these one-time recovery codes</h2>
                <ul className="select-text break-all font-mono">{recovery.map(value => <li key={value}>{value}</li>)}</ul>
                <button type="button" className="mt-3 rounded border px-3 py-2" onClick={() => {void refresh().then(() => router.replace("/admin"));}}>I saved the codes</button>
            </div>}
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
                disabled={submitting || recovery.length > 0}
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
                        : enrollment ? "Confirm authenticator" : "Sign in to Admin"
                }

            </button>

        </form>
    );
}