import type {
    PaymentResponse
} from "@/types/payment";


export interface RazorpaySuccessResponse {
    razorpay_payment_id: string;
    razorpay_order_id: string;
    razorpay_signature: string;
}


interface RazorpayFailureResponse {
    error?: {
        description?: string;
    };
}


interface RazorpayOptions {
    key: string;
    amount: number;
    currency: string;
    name: string;
    description: string;
    order_id: string;

    handler:
        (
            response:
                RazorpaySuccessResponse
        ) => void;

    modal: {
        ondismiss: () => void;
        escape: boolean;
        confirm_close: boolean;
    };

    theme: {
        color: string;
    };

    retry: {
        enabled: boolean;
    };
}


interface RazorpayInstance {
    open(): void;

    on(
        eventName: "payment.failed",

        handler:
            (
                response:
                    RazorpayFailureResponse
            ) => void
    ): void;
}


type RazorpayConstructor =
    new (
        options:
            RazorpayOptions
    ) => RazorpayInstance;


declare global {
    interface Window {
        Razorpay?:
            RazorpayConstructor;
    }
}


let scriptPromise:
    Promise<void> | null =
    null;


function loadRazorpayScript():
    Promise<void> {

    if (
        typeof window ===
        "undefined"
    ) {
        return Promise.reject(
            new Error(
                "Razorpay Checkout is only available in the browser."
            )
        );
    }


    if (
        window.Razorpay
    ) {
        return Promise.resolve();
    }


    if (
        scriptPromise
    ) {
        return scriptPromise;
    }


    const loadingPromise:
        Promise<void> =
        new Promise<void>(
            (
                resolve,
                reject
            ) => {

                const existingScript =
                    document.getElementById(
                        "razorpay-checkout-js"
                    ) as HTMLScriptElement | null;


                const handleLoad =
                    () => {

                        if (
                            window.Razorpay
                        ) {
                            resolve();

                            return;
                        }


                        reject(
                            new Error(
                                "Razorpay Checkout loaded but is unavailable."
                            )
                        );
                    };


                const handleError =
                    () => {

                        reject(
                            new Error(
                                "Unable to load Razorpay Checkout."
                            )
                        );
                    };


                if (
                    existingScript
                ) {
                    existingScript.addEventListener(
                        "load",
                        handleLoad,
                        {
                            once: true
                        }
                    );


                    existingScript.addEventListener(
                        "error",
                        handleError,
                        {
                            once: true
                        }
                    );


                    return;
                }


                const script =
                    document.createElement(
                        "script"
                    );


                script.id =
                    "razorpay-checkout-js";


                script.src =
                    "https://checkout.razorpay.com/v1/checkout.js";


                script.async =
                    true;


                script.onload =
                    handleLoad;


                script.onerror =
                    handleError;


                document.head.appendChild(
                    script
                );
            }
        )
        .catch(
            error => {

                /*
                 * Permit a later attempt to load the script
                 * if this request failed.
                 */
                scriptPromise =
                    null;


                throw error;
            }
        );


    scriptPromise =
        loadingPromise;


    return loadingPromise;
}


export async function openRazorpayCheckout(
    payment:
        PaymentResponse,

    onSuccess:
        (
            response:
                RazorpaySuccessResponse
        ) => void,

    onDismiss:
        () => void,

    onFailure:
        (
            message:
                string
        ) => void
): Promise<void> {

    if (
        payment.provider !==
            "RAZORPAY"
        ||
        payment.paymentStatus !==
            "PENDING"
    ) {
        throw new Error(
            "This Razorpay payment is no longer pending."
        );
    }


    if (
        !payment.providerOrderId
        ||
        !payment.checkoutKeyId
    ) {
        throw new Error(
            "Razorpay checkout information is incomplete."
        );
    }


    /*
     * Capture the validated values before entering
     * asynchronous callbacks. This preserves TypeScript's
     * non-null type narrowing.
     */
    const providerOrderId =
        payment.providerOrderId;


    const checkoutKeyId =
        payment.checkoutKeyId;


    await loadRazorpayScript();


    const Razorpay =
        window.Razorpay;


    if (
        !Razorpay
    ) {
        throw new Error(
            "Razorpay Checkout is unavailable."
        );
    }


    const checkout =
        new Razorpay(
            {
                key:
                    checkoutKeyId,

                amount:
                    Math.round(
                        payment.amount
                        *
                        100
                    ),

                currency:
                    payment.currency,

                name:
                    "Gokul Sweets",

                description:
                    `Order ${payment.orderNumber}`,

                order_id:
                    providerOrderId,

                handler:
                    onSuccess,

                modal: {
                    ondismiss:
                        onDismiss,

                    escape:
                        true,

                    confirm_close:
                        true
                },

                theme: {
                    color:
                        "#7a1625"
                },

                retry: {
                    enabled:
                        true
                }
            }
        );


    checkout.on(
        "payment.failed",

        response => {

            onFailure(
                response.error
                    ?.description
                ??
                "The payment was not completed. You can safely retry."
            );
        }
    );


    checkout.open();
}