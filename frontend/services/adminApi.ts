import type {
    AdminProfile
} from "@/types/admin";


const API_BASE =
    process.env.NEXT_PUBLIC_API_BASE_URL
    ?? "http://localhost:8080";


export async function authenticateAdmin(
    username: string,
    password: string,
    signal?: AbortSignal
): Promise<{
    authorization: string;
    profile: AdminProfile;
}> {

    const normalizedUsername =
        username
            .trim()
            .toLowerCase();


    const authorization =
        `Basic ${btoa(
            `${normalizedUsername}:${password}`
        )}`;


    const response =
        await fetch(
            `${API_BASE}/api/admin/auth/me`,
            {
                method: "GET",

                headers: {
                    Authorization:
                        authorization
                },

                signal,

                cache:
                    "no-store"
            }
        );


    if (
        response.status
        === 401
    ) {

        throw new Error(
            "Invalid username or password."
        );
    }


    if (
        response.status
        === 403
    ) {

        throw new Error(
            "This staff account is not allowed to access the admin portal."
        );
    }


    if (
        !response.ok
    ) {

        throw new Error(
            "Unable to sign in right now."
        );
    }


    const profile: AdminProfile =
        await response.json();


    return {
        authorization,
        profile
    };
}


export async function adminFetch(
    path: string,
    authorization: string,
    init?: RequestInit
): Promise<Response> {

    const headers =
        new Headers(
            init?.headers
        );


    headers.set(
        "Authorization",
        authorization
    );


    return fetch(
        `${API_BASE}${path}`,
        {
            ...init,

            headers,

            cache:
                "no-store"
        }
    );
}