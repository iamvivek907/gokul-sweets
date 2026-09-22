"use client";

import {
    useEffect,
    useState
} from "react";

import {
    getActiveBranches
} from "@/services/branchApi";

import {
    useSelectedBranch
} from "@/hooks/useSelectedBranch";

import type {
    Branch
} from "@/types/branch";


export default function BranchSelector() {

    const {
        branch,
        selectBranch
    } =
        useSelectedBranch();


    const [
        branches,
        setBranches
    ] =
        useState<Branch[]>(
            []
        );


    const [
        loading,
        setLoading
    ] =
        useState(true);


    const [
        error,
        setError
    ] =
        useState<
            string | null
        >(null);


    const [
        reloadKey,
        setReloadKey
    ] =
        useState(0);


    useEffect(() => {

        const controller =
            new AbortController();


        async function loadBranches() {

            try {

                const result =
                    await getActiveBranches(
                        controller.signal
                    );


                if (
                    controller.signal.aborted
                ) {
                    return;
                }


                const activeBranches =
                    result.filter(
                        item =>
                            item.active
                            !== false
                    );


                setBranches(
                    activeBranches
                );


                setError(
                    null
                );


                setLoading(
                    false
                );

            } catch (exception) {

                if (
                    controller.signal.aborted
                ) {
                    return;
                }


                console.error(
                    "Unable to load branches:",
                    exception
                );


                setError(
                    exception instanceof Error
                        ? exception.message
                        : "Unable to load branches."
                );


                setLoading(
                    false
                );
            }
        }


        void loadBranches();


        return () => {
            controller.abort();
        };

    }, [
        reloadKey
    ]);


    function handleSelectBranch(
        selectedBranch: Branch
    ) {

        selectBranch(
            selectedBranch
        );


        const popover =
            document.getElementById(
                "branch-selector-popover"
            );


        if (
            popover
            &&
            "hidePopover" in popover
        ) {

            (
                popover as HTMLElement & {
                    hidePopover: () => void;
                }
            ).hidePopover();
        }
    }


    function retryBranches() {

        setLoading(
            true
        );


        setError(
            null
        );


        setReloadKey(
            current =>
                current + 1
        );
    }


    return (
        <div>

            <button
                type="button"
                popoverTarget="branch-selector-popover"
                className="
                    w-full
                    rounded-2xl
                    border
                    border-[#eadfd6]
                    bg-white
                    p-4
                    text-left
                    shadow-sm
                    transition
                    active:scale-[0.99]
                "
            >

                <div
                    className="
                        flex
                        items-start
                        justify-between
                        gap-4
                    "
                >

                    <div>

                        <p
                            className="
                                text-xs
                                font-medium
                                text-[#756763]
                            "
                        >
                            Pickup from
                        </p>


                        <p
                            className="
                                mt-1
                                text-base
                                font-bold
                                text-[#241715]
                            "
                        >
                            {
                                branch
                                    ? branch.name
                                    : "Select a branch"
                            }
                        </p>


                        <p
                            className="
                                mt-1
                                text-sm
                                text-[#756763]
                            "
                        >
                            {
                                branch?.address
                                ??
                                "Choose your nearest pickup location"
                            }
                        </p>

                    </div>


                    <span
                        className="
                            text-sm
                            font-semibold
                            text-[#7a1625]
                        "
                    >
                        {
                            branch
                                ? "Change"
                                : "Select"
                        }
                    </span>

                </div>

            </button>


            <div
                id="branch-selector-popover"
                popover="auto"
                className="
                    fixed
                    inset-0
                    m-auto
                    h-fit
                    max-h-[calc(100dvh-2rem)]
                    w-[calc(100vw-2rem)]
                    max-w-md
                    overflow-y-auto
                    rounded-3xl
                    border
                    border-[#eadfd6]
                    bg-white
                    p-5
                    shadow-xl
                    backdrop:bg-black/40
                "
            >

                <div
                    className="
                        mb-5
                    "
                >

                    <p
                        className="
                            text-xs
                            font-semibold
                            uppercase
                            tracking-wide
                            text-[#c88a20]
                        "
                    >
                        Pickup location
                    </p>


                    <h2
                        className="
                            mt-1
                            text-xl
                            font-bold
                            text-[#241715]
                        "
                    >
                        Select Branch
                    </h2>

                </div>


                {loading && (

                    <div
                        className="
                            space-y-3
                        "
                    >

                        {[1, 2, 3].map(
                            item => (

                                <div
                                    key={item}
                                    className="
                                        h-20
                                        animate-pulse
                                        rounded-2xl
                                        bg-[#f4e9df]
                                    "
                                />

                            )
                        )}

                    </div>

                )}


                {!loading
                    &&
                    error
                    && (

                        <div
                            className="
                                rounded-2xl
                                bg-[#fff4e5]
                                p-4
                                text-center
                            "
                        >

                            <p
                                className="
                                    text-sm
                                    text-[#7a1625]
                                "
                            >
                                {error}
                            </p>


                            <button
                                type="button"
                                onClick={
                                    retryBranches
                                }
                                className="
                                    mt-4
                                    rounded-xl
                                    bg-[#7a1625]
                                    px-4
                                    py-2
                                    text-sm
                                    font-semibold
                                    text-white!
                                "
                            >
                                Try again
                            </button>

                        </div>

                    )}


                {!loading
                    &&
                    !error
                    &&
                    branches.length === 0
                    && (

                        <div
                            className="
                                rounded-2xl
                                bg-[#fff4e5]
                                p-5
                                text-center
                            "
                        >

                            <p
                                className="
                                    font-semibold
                                    text-[#241715]
                                "
                            >
                                No branches available
                            </p>


                            <p
                                className="
                                    mt-1
                                    text-sm
                                    text-[#756763]
                                "
                            >
                                Please try again later.
                            </p>

                        </div>

                    )}


                {!loading
                    &&
                    !error
                    &&
                    branches.length > 0
                    && (

                        <div
                            className="
                                space-y-3
                            "
                        >

                            {branches.map(
                                item => {

                                    const selected =
                                        branch?.id
                                        === item.id;


                                    return (
                                        <button
                                            key={
                                                item.id
                                            }
                                            type="button"
                                            onClick={() =>
                                                handleSelectBranch(
                                                    item
                                                )
                                            }
                                            className={`
                                                w-full
                                                rounded-2xl
                                                border
                                                p-4
                                                text-left
                                                transition
                                                active:scale-[0.99]

                                                ${
                                                    selected
                                                        ? "border-[#7a1625] bg-[#fff4e5]"
                                                        : "border-[#eadfd6] bg-white"
                                                }
                                            `}
                                        >

                                            <div
                                                className="
                                                    flex
                                                    items-center
                                                    justify-between
                                                    gap-4
                                                "
                                            >

                                                <div>

                                                    <p
                                                        className="
                                                            font-bold
                                                            text-[#241715]
                                                        "
                                                    >
                                                        {
                                                            item.name
                                                        }
                                                    </p>


                                                    {
                                                        item.address
                                                        && (

                                                            <p
                                                                className="
                                                                    mt-1
                                                                    text-sm
                                                                    text-[#756763]
                                                                "
                                                            >
                                                                {
                                                                    item.address
                                                                }
                                                            </p>

                                                        )
                                                    }

                                                </div>


                                                {selected && (

                                                    <span
                                                        className="
                                                            font-bold
                                                            text-[#7a1625]
                                                        "
                                                    >
                                                        ✓
                                                    </span>

                                                )}

                                            </div>

                                        </button>
                                    );
                                }
                            )}

                        </div>

                    )}

            </div>

        </div>
    );
}