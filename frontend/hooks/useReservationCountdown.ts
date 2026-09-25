import {
    useEffect,
    useMemo,
    useState
} from "react";

import {parseBusinessTimestamp} from "@/lib/businessTime";


export interface ReservationCountdown {

    expiresAt:
        Date
        | null;

    remainingSeconds:
        number;

    expired:
        boolean;

    minutes:
        number;

    seconds:
        number;
}


export function useReservationCountdown(
    reservationExpiresAt:
        string
        | null
        | undefined
): ReservationCountdown {

    const [
        now,
        setNow
    ] =
        useState(
            () =>
                Date.now()
        );


    const expiresAt =
        useMemo(
            () => {

                if (
                    !reservationExpiresAt
                ) {

                    return null;
                }


                const parsed =
                    parseBusinessTimestamp(
                        reservationExpiresAt
                    );


                if (
                    Number.isNaN(
                        parsed.getTime()
                    )
                ) {

                    return null;
                }


                return parsed;

            },
            [
                reservationExpiresAt
            ]
        );


    useEffect(
        () => {

            if (
                !expiresAt
            ) {

                return;
            }


            const interval =
                window.setInterval(
                    () => {

                        setNow(
                            Date.now()
                        );

                    },
                    1000
                );


            return () => {

                window.clearInterval(
                    interval
                );
            };

        },
        [
            expiresAt
        ]
    );


    const remainingSeconds =
        expiresAt
            ? Math.max(
                0,
                Math.ceil(
                    (
                        expiresAt.getTime()
                        -
                        now
                    )
                    /
                    1000
                )
            )
            : 0;


    const minutes =
        Math.floor(
            remainingSeconds
            /
            60
        );


    const seconds =
        remainingSeconds
        %
        60;


    return {
        expiresAt,

        remainingSeconds,

        expired:
            expiresAt !==
                null
            &&
            remainingSeconds <=
                0,

        minutes,

        seconds
    };
}
