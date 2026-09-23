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


/*
 * =========================================================
 * OPEN PAYMENT CHECKOUT
 * =========================================================
 */

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


/*
 * =========================================================
 * PHONEPE
 * =========================================================
 *
 * PhonePe Standard Checkout V2 returns a hosted checkout
 * URL from the backend.
 *
 * We intentionally navigate the CURRENT browser tab instead
 * of opening a popup/new tab.
 *
 * PhonePe will redirect the customer back to:
 *
 * /payment/{orderNumber}
 *
 * The PaymentPage then refreshes the backend payment status.
 *
 * Once the backend reports PAID, PaymentPage performs:
 *
 * /orders/{orderNumber}
 *
 * This avoids:
 *
 * - popup blockers
 * - a second browser tab
 * - duplicate status polling
 * - the customer being left on the payment page after success
 *
 * Razorpay and Paytm continue using their existing flows.
 */

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


    /*
     * Navigate to the PhonePe hosted checkout in the
     * current browser tab.
     *
     * The backend created this URL using the order-specific
     * redirect URL:
     *
     * https://gokul-sweets-dev.vercel.app/payment/{orderNumber}
     */
    window.location.assign(
        payment.paymentUrl
    );


    /*
     * The current page will normally unload immediately
     * after window.location.assign().
     *
     * This return value only satisfies CheckoutOutcome.
     *
     * The PaymentPage loaded after PhonePe's redirect is
     * responsible for refreshing the payment and handling
     * the final PAID → /orders/{orderNumber} transition.
     */
    return {
        kind: "dismissed"
    };
}


/*
 * =========================================================
 * RAZORPAY
 * =========================================================
 *
 * Existing Razorpay flow intentionally preserved.
 */

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


/*
 * =========================================================
 * PAYTM
 * =========================================================
 *
 * Existing Paytm flow intentionally preserved.
 */

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


/*
 * =========================================================
 * WAIT FOR PAYMENT CONFIRMATION
 * =========================================================
 *
 * Used by Razorpay and Paytm.
 *
 * PhonePe does NOT use this browser-side polling flow.
 * PhonePe returns to PaymentPage, which owns the normal
 * payment refresh lifecycle.
 */

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


/*
 * =========================================================
 * DELAY
 * =========================================================
 */

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
