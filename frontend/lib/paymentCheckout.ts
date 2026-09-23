import {openPaytmCheckout} from "@/lib/paytmCheckout";
import {
    openRazorpayCheckout,
    type RazorpaySuccessResponse
} from "@/lib/razorpayCheckout";
import {
    refreshPayment,
    verifyRazorpayPayment
} from "@/services/paymentApi";
import type {
    PaymentResponse,
    PaymentStatus
} from "@/types/payment";


export type CheckoutOutcome =
    | {
        kind: "updated";
        payment: PaymentResponse;
    }
    | {
        kind: "dismissed";
    }
    | {
        kind: "failed";
        message: string;
    };


const FINAL_PAYMENT_STATUSES:
    ReadonlySet<PaymentStatus> =
    new Set<PaymentStatus>([
        "PAID",
        "FAILED",
        "EXPIRED",
        "REFUND_PENDING",
        "REFUNDED",
        "REFUND_FAILED"
    ]);


const CONFIRMATION_POLL_ATTEMPTS =
    10;


const CONFIRMATION_POLL_INTERVAL_MS =
    1500;


export async function openPaymentCheckout(
    payment: PaymentResponse
): Promise<CheckoutOutcome> {

    if (
        payment.provider ===
        "RAZORPAY"
    ) {

        return openRazorpay(
            payment
        );
    }


    if (
        payment.provider ===
        "PAYTM"
    ) {

        return openPaytm(
            payment
        );
    }

    if (
        payment.provider ===
        "PHONEPE"
    ) {
        return openPhonePe(
            payment
        );
    }


    return {
        kind: "failed",
        message: "The selected payment provider is not supported."
    };
}

async function openPhonePe(
    payment: PaymentResponse
): Promise<CheckoutOutcome> {

    if (
        !payment.paymentUrl
    ) {
        return {
            kind: "failed",
            message: "PhonePe checkout link is unavailable for this payment."
        };
    }

    const popup =
        window.open(
            payment.paymentUrl,
            "_blank",
            "noopener,noreferrer"
        );

    if (
        !popup
    ) {
        return {
            kind: "failed",
            message: "Unable to open PhonePe. Please allow pop-ups and try again."
        };
    }

    let latest =
        await refreshPayment(
            payment.paymentId
        );

    if (
        FINAL_PAYMENT_STATUSES.has(
            latest.paymentStatus
        )
    ) {
        return {
            kind: "updated",
            payment: latest
        };
    }

    for (
        let attempt = 0;
        attempt < CONFIRMATION_POLL_ATTEMPTS;
        attempt++
    ) {
        await delay(
            CONFIRMATION_POLL_INTERVAL_MS
        );

        latest =
            await refreshPayment(
                payment.paymentId
            );

        if (
            FINAL_PAYMENT_STATUSES.has(
                latest.paymentStatus
            )
        ) {
            return {
                kind: "updated",
                payment: latest
            };
        }

        if (
            popup.closed
        ) {
            break;
        }
    }

    return popup.closed
        ? {kind: "dismissed"}
        : {
            kind: "updated",
            payment: latest
        };
}


function openRazorpay(
    payment: PaymentResponse
): Promise<CheckoutOutcome> {

    return new Promise<CheckoutOutcome>(
        (
            resolve,
            reject
        ) => {

            let settled =
                false;


            const finish =
                (
                    outcome:
                        CheckoutOutcome
                ) => {

                    if (
                        settled
                    ) {

                        return;
                    }


                    settled =
                        true;


                    resolve(
                        outcome
                    );
                };


            const fail =
                (
                    error:
                        unknown
                ) => {

                    if (
                        settled
                    ) {

                        return;
                    }


                    settled =
                        true;


                    reject(
                        error
                    );
                };


            const handleSuccess =
                async (
                    response:
                        RazorpaySuccessResponse
                ) => {

                    try {

                        const verified =
                            await verifyRazorpayPayment(
                                payment.paymentId,
                                {
                                    razorpayPaymentId:
                                        response.razorpay_payment_id,

                                    razorpayOrderId:
                                        response.razorpay_order_id,

                                    razorpaySignature:
                                        response.razorpay_signature
                                }
                            );


                        const confirmed =
                            await waitForPaymentConfirmation(
                                verified
                            );


                        finish({
                            kind: "updated",
                            payment: confirmed
                        });

                    } catch (
                        error
                    ) {

                        fail(
                            error
                        );
                    }
                };


            void openRazorpayCheckout(
                payment,

                response => {

                    void handleSuccess(
                        response
                    );
                },

                () => {

                    finish({
                        kind: "dismissed"
                    });
                },

                message => {

                    finish({
                        kind: "failed",
                        message
                    });
                }
            ).catch(
                fail
            );
        }
    );
}


function openPaytm(
    payment: PaymentResponse
): Promise<CheckoutOutcome> {

    return new Promise<CheckoutOutcome>(
        (
            resolve,
            reject
        ) => {

            let settled =
                false;


            const finish =
                (
                    outcome:
                        CheckoutOutcome
                ) => {

                    if (
                        settled
                    ) {

                        return;
                    }


                    settled =
                        true;


                    resolve(
                        outcome
                    );
                };


            const fail =
                (
                    error:
                        unknown
                ) => {

                    if (
                        settled
                    ) {

                        return;
                    }


                    settled =
                        true;


                    reject(
                        error
                    );
                };


            void openPaytmCheckout(
                payment,

                () => {

                    void refreshPayment(
                        payment.paymentId
                    )
                        .then(
                            waitForPaymentConfirmation
                        )
                        .then(
                            updated => {

                                finish({
                                    kind: "updated",
                                    payment: updated
                                });
                            }
                        )
                        .catch(
                            fail
                        );
                },

                eventName => {

                    if (
                        eventName
                            .toLowerCase()
                            .includes(
                                "close"
                            )
                    ) {

                        finish({
                            kind: "dismissed"
                        });
                    }
                }
            ).catch(
                fail
            );
        }
    );
}


async function waitForPaymentConfirmation(
    initial:
        PaymentResponse
): Promise<PaymentResponse> {

    let latest =
        initial;


    if (
        FINAL_PAYMENT_STATUSES.has(
            latest.paymentStatus
        )
    ) {

        return latest;
    }


    for (
        let attempt = 0;
        attempt < CONFIRMATION_POLL_ATTEMPTS;
        attempt++
    ) {

        await delay(
            CONFIRMATION_POLL_INTERVAL_MS
        );


        latest =
            await refreshPayment(
                latest.paymentId
            );


        if (
            FINAL_PAYMENT_STATUSES.has(
                latest.paymentStatus
            )
        ) {

            return latest;
        }
    }


    return latest;
}


function delay(
    milliseconds:
        number
): Promise<void> {

    return new Promise<void>(
        resolve => {

            window.setTimeout(
                resolve,
                milliseconds
            );
        }
    );
}
