import type {
    PaymentResponse
} from "@/types/payment";


interface PaytmTransactionStatus {
    [key: string]: unknown;
}


interface PaytmCheckoutConfig {

    root: string;

    flow: "DEFAULT";

    data: {
        orderId: string;

        token: string;

        tokenType: "TXN_TOKEN";

        amount: string;
    };

    handler: {

        transactionStatus:
            (
                status:
                    PaytmTransactionStatus
            ) => void;

        notifyMerchant:
            (
                eventName: string,
                data: unknown
            ) => void;
    };
}


interface PaytmCheckoutJs {

    onLoad:
        (
            callback: () => void
        ) => void;

    init:
        (
            config:
                PaytmCheckoutConfig
        ) => Promise<unknown>;

    invoke:
        () => void;
}


declare global {

    interface Window {

        Paytm?: {
            CheckoutJS:
                PaytmCheckoutJs;
        };
    }
}


function getPaytmHost():
    string {

    const environment =
        process.env
            .NEXT_PUBLIC_PAYTM_ENVIRONMENT
            ?.toUpperCase();


    if (
        environment ===
        "PRODUCTION"
    ) {

        return "https://secure.paytmpayments.com";
    }


    return "https://securestage.paytmpayments.com";
}


function getPaytmMid():
    string {

    const mid =
        process.env
            .NEXT_PUBLIC_PAYTM_MID;


    if (
        !mid
        ||
        mid.trim().length === 0
    ) {

        throw new Error(
            "NEXT_PUBLIC_PAYTM_MID is not configured."
        );
    }


    return mid.trim();
}


function loadPaytmScript():
    Promise<void> {

    if (
        typeof window === "undefined"
    ) {

        return Promise.reject(
            new Error(
                "Paytm Checkout is only available in the browser."
            )
        );
    }


    if (
        window.Paytm?.CheckoutJS
    ) {

        return Promise.resolve();
    }


    const mid =
        getPaytmMid();


    const host =
        getPaytmHost();


    const scriptId =
        "paytm-checkout-js";


    const existingScript =
        document.getElementById(
            scriptId
        ) as HTMLScriptElement | null;


    if (existingScript) {

        return new Promise(
            (
                resolve,
                reject
            ) => {

                if (
                    window.Paytm?.CheckoutJS
                ) {

                    resolve();

                    return;
                }


                existingScript.addEventListener(
                    "load",
                    () => resolve(),
                    {
                        once: true
                    }
                );


                existingScript.addEventListener(
                    "error",
                    () =>
                        reject(
                            new Error(
                                "Unable to load Paytm Checkout."
                            )
                        ),
                    {
                        once: true
                    }
                );
            }
        );
    }


    return new Promise(
        (
            resolve,
            reject
        ) => {

            const script =
                document.createElement(
                    "script"
                );


            script.id =
                scriptId;


            script.type =
                "application/javascript";


            script.src =
                `${host}/merchantpgpui/checkoutjs/merchants/${encodeURIComponent(
                    mid
                )}.js`;


            script.crossOrigin =
                "anonymous";


            script.onload =
                () => {

                    if (
                        window.Paytm?.CheckoutJS
                    ) {

                        resolve();

                        return;
                    }


                    reject(
                        new Error(
                            "Paytm Checkout loaded but could not be initialized."
                        )
                    );
                };


            script.onerror =
                () => {

                    reject(
                        new Error(
                            "Unable to load Paytm Checkout."
                        )
                    );
                };


            document.head.appendChild(
                script
            );
        }
    );
}


export async function openPaytmCheckout(
    payment: PaymentResponse,
    onTransactionStatus:
        (
            status:
                PaytmTransactionStatus
        ) => void,
    onNotifyMerchant:
        (
            eventName: string,
            data: unknown
        ) => void
): Promise<void> {

    if (
        payment.paymentStatus !==
        "PENDING"
    ) {

        throw new Error(
            "This payment is no longer pending."
        );
    }


    if (
        !payment.providerOrderId
    ) {

        throw new Error(
            "Paytm order ID is missing."
        );
    }


    if (
        !payment.paymentSessionId
    ) {

        throw new Error(
            "Paytm transaction token is missing."
        );
    }


    await loadPaytmScript();


    const checkout =
        window.Paytm?.CheckoutJS;


    if (!checkout) {

        throw new Error(
            "Paytm Checkout is unavailable."
        );
    }


    const config:
        PaytmCheckoutConfig =
        {

            root: "",

            flow:
                "DEFAULT",

            data: {

                orderId:
                    payment.providerOrderId,

                token:
                    payment.paymentSessionId,

                tokenType:
                    "TXN_TOKEN",

                amount:
                    payment.amount
                        .toFixed(2)
            },

            handler: {

                transactionStatus:
                    onTransactionStatus,

                notifyMerchant:
                    onNotifyMerchant
            }
        };


    await new Promise<void>(
        (
            resolve,
            reject
        ) => {

            checkout.onLoad(
                () => {

                    checkout
                        .init(
                            config
                        )
                        .then(
                            () => {

                                checkout.invoke();

                                resolve();
                            }
                        )
                        .catch(
                            error => {

                                console.error(
                                    "Paytm Checkout init failed:",
                                    error
                                );


                                reject(
                                    new Error(
                                        "Unable to start Paytm Checkout."
                                    )
                                );
                            }
                        );
                }
            );
        }
    );
}