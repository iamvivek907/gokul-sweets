"use client";

export default function RetryConnectionButton() {

    return (
        <button
            type="button"
            onClick={() =>
                window.location.reload()
            }
            className="
                mt-6
                min-h-12
                rounded-xl
                bg-[#7a1625]
                px-6
                font-semibold
                text-white
            "
        >
            Reconnect and try again
        </button>
    );
}