"use client";

import {
    useEffect,
    useState
} from "react";

import Link
    from "next/link";

import {
    useAdminAuth
} from "@/contexts/AdminAuthContext";

import {
    downloadMenuImportTemplate,
    importMenuFile,
    validateMenuImport
} from "@/services/adminMenuImportApi";

import type {
    MenuImportResultResponse,
    MenuImportValidationResponse
} from "@/types/adminMenuImport";


interface Branch {
    id: number;
    code: string;
    name: string;
    address: string;
    city: string;
    state: string;
    pincode: string;
    phone: string;
    latitude: number | null;
    longitude: number | null;
    openingTime: string;
    closingTime: string;
    active: boolean;
}


const API_BASE =
    process.env.NEXT_PUBLIC_API_BASE_URL
    ?? "http://localhost:8080";


export default function AdminMenuImportPage() {

    const {
        profile,
        authorization
    } =
        useAdminAuth();


    const [
        branches,
        setBranches
    ] =
        useState<Branch[]>(
            []
        );


    const [
        selectedBranchId,
        setSelectedBranchId
    ] =
        useState<number | null>(
            null
        );


    const [
        selectedFile,
        setSelectedFile
    ] =
        useState<File | null>(
            null
        );


    const [
        validationResult,
        setValidationResult
    ] =
        useState<MenuImportValidationResponse | null>(
            null
        );


    const [
        importResult,
        setImportResult
    ] =
        useState<MenuImportResultResponse | null>(
            null
        );


    const [
        branchesLoading,
        setBranchesLoading
    ] =
        useState(true);


    const [
        downloadingTemplate,
        setDownloadingTemplate
    ] =
        useState(false);


    const [
        validating,
        setValidating
    ] =
        useState(false);


    const [
        importing,
        setImporting
    ] =
        useState(false);


    const [
        error,
        setError
    ] =
        useState<string | null>(
            null
        );


    /*
     * =========================================================
     * LOAD ALLOWED BRANCHES
     * =========================================================
     */

    useEffect(
        () => {

            if (!profile) {
                return;
            }


            const currentProfile =
                profile;


            const controller =
                new AbortController();


            async function loadBranches() {

                try {

                    const response =
                        await fetch(
                            `${API_BASE}/api/branches`,
                            {
                                signal:
                                    controller.signal,

                                cache:
                                    "no-store"
                            }
                        );


                    if (!response.ok) {

                        throw new Error(
                            "Unable to load branches."
                        );
                    }


                    const allBranches:
                        Branch[] =
                        await response.json();


                    const activeBranches =
                        allBranches.filter(
                            branch =>
                                branch.active
                        );


                    const allowedBranches =
                        currentProfile.roleName
                        === "OWNER_ADMIN"
                            ? activeBranches
                            : activeBranches.filter(
                                branch =>
                                    currentProfile.branchIds.includes(
                                        branch.id
                                    )
                            );


                    setBranches(
                        allowedBranches
                    );


                    if (
                        allowedBranches.length
                        > 0
                    ) {

                        setSelectedBranchId(
                            allowedBranches[0].id
                        );
                    }

                } catch (exception) {

                    if (
                        exception
                        instanceof DOMException
                        &&
                        exception.name
                        === "AbortError"
                    ) {

                        return;
                    }


                    setError(
                        exception
                        instanceof Error
                            ? exception.message
                            : "Unable to load branches."
                    );

                } finally {

                    setBranchesLoading(
                        false
                    );
                }
            }


            void loadBranches();


            return () => {

                controller.abort();
            };

        },
        [
            profile
        ]
    );


    /*
     * =========================================================
     * TEMPLATE DOWNLOAD
     * =========================================================
     */

    async function handleDownloadTemplate() {

        if (
            selectedBranchId
            === null
            ||
            authorization
            === null
        ) {

            return;
        }


        setDownloadingTemplate(
            true
        );


        setError(
            null
        );


        try {

            await downloadMenuImportTemplate(
                selectedBranchId,
                authorization
            );

        } catch (exception) {

            setError(
                exception
                instanceof Error
                    ? exception.message
                    : "Unable to download menu template."
            );

        } finally {

            setDownloadingTemplate(
                false
            );
        }
    }


    /*
     * =========================================================
     * FILE SELECTION
     * =========================================================
     */

    function handleFileChange(
        file: File | null
    ) {

        setError(
            null
        );


        setValidationResult(
            null
        );


        setImportResult(
            null
        );


        if (!file) {

            setSelectedFile(
                null
            );

            return;
        }


        const lowerName =
            file.name.toLowerCase();


        if (
            !lowerName.endsWith(
                ".xlsx"
            )
        ) {

            setSelectedFile(
                null
            );


            setError(
                "Please select an Excel .xlsx file."
            );


            return;
        }


        setSelectedFile(
            file
        );
    }


    /*
     * =========================================================
     * VALIDATE
     * =========================================================
     */

    async function handleValidate() {

        if (
            selectedBranchId
            === null
            ||
            authorization
            === null
            ||
            selectedFile
            === null
        ) {

            return;
        }


        setValidating(
            true
        );


        setError(
            null
        );


        setValidationResult(
            null
        );


        setImportResult(
            null
        );


        try {

            const result =
                await validateMenuImport(
                    selectedBranchId,
                    selectedFile,
                    authorization
                );


            setValidationResult(
                result
            );

        } catch (exception) {

            setError(
                exception
                instanceof Error
                    ? exception.message
                    : "Unable to validate menu file."
            );

        } finally {

            setValidating(
                false
            );
        }
    }


    /*
     * =========================================================
     * IMPORT
     * =========================================================
     */

    async function handleImport() {

        if (
            selectedBranchId
            === null
            ||
            authorization
            === null
            ||
            selectedFile
            === null
            ||
            validationResult
            === null
            ||
            !validationResult.valid
        ) {

            return;
        }


        const confirmed =
            window.confirm(
                `Import ${validationResult.totalRows} menu rows into the selected branch?`
            );


        if (!confirmed) {

            return;
        }


        setImporting(
            true
        );


        setError(
            null
        );


        setImportResult(
            null
        );


        try {

            const result =
                await importMenuFile(
                    selectedBranchId,
                    selectedFile,
                    authorization
                );


            setImportResult(
                result
            );

        } catch (exception) {

            setError(
                exception
                instanceof Error
                    ? exception.message
                    : "Unable to import menu file."
            );

        } finally {

            setImporting(
                false
            );
        }
    }


    const selectedBranch =
        branches.find(
            branch =>
                branch.id
                === selectedBranchId
        );


    const canImport =
        validationResult?.valid
        === true
        &&
        selectedFile
        !== null
        &&
        !importing;


    return (
        <div
            className="
                px-4
                py-6

                sm:px-6
                sm:py-8

                lg:px-8
            "
        >

            <div
                className="
                    mx-auto
                    w-full
                    max-w-7xl
                "
            >

                <div
                    className="
                        flex
                        flex-col
                        gap-5

                        lg:flex-row
                        lg:items-start
                        lg:justify-between
                    "
                >

                    <div
                        className="
                            min-w-0
                        "
                    >

                        <Link
                            href="/admin/menu"
                            className="
                                text-sm
                                font-semibold
                                text-[#7a1625]

                                hover:underline
                            "
                        >
                            ← Menu Management
                        </Link>


                        <p
                            className="
                                mt-5
                                text-xs
                                font-semibold
                                uppercase
                                tracking-[0.16em]
                                text-[#c88a20]
                            "
                        >
                            Bulk update
                        </p>


                        <h1
                            className="
                                mt-2
                                text-3xl
                                font-bold
                                tracking-tight
                                text-[#241715]

                                sm:text-4xl
                            "
                        >
                            Excel Import / Update
                        </h1>


                        <p
                            className="
                                mt-3
                                max-w-3xl
                                text-sm
                                leading-6
                                text-[#756763]
                            "
                        >
                            Download the current menu template, prepare your spreadsheet, validate it, then import the changes safely.
                        </p>

                    </div>


                    <div
                        className="
                            w-full
                            shrink-0

                            lg:w-80
                        "
                    >

                        <label
                            htmlFor="import-branch"
                            className="
                                mb-2
                                block
                                text-sm
                                font-semibold
                                text-[#241715]
                            "
                        >
                            Branch
                        </label>


                        <select
                            id="import-branch"
                            value={
                                selectedBranchId
                                ?? ""
                            }
                            disabled={
                                branchesLoading
                                ||
                                branches.length
                                === 0
                            }
                            onChange={
                                event => {

                                    const value =
                                        Number(
                                            event.target.value
                                        );


                                    setSelectedBranchId(
                                        value
                                    );


                                    setSelectedFile(
                                        null
                                    );


                                    setValidationResult(
                                        null
                                    );


                                    setImportResult(
                                        null
                                    );


                                    setError(
                                        null
                                    );
                                }
                            }
                            className="
                                min-h-12
                                w-full
                                rounded-xl
                                border
                                border-[#eadfd6]
                                bg-white
                                px-4
                                text-sm
                                font-semibold
                                text-[#241715]
                                outline-none

                                focus:border-[#c88a20]
                                focus:ring-4
                                focus:ring-[#f6dfad]/40

                                disabled:bg-[#f8f4f1]
                            "
                        >

                            {
                                branches.length
                                === 0
                                && (

                                    <option value="">
                                        No branch available
                                    </option>

                                )
                            }


                            {
                                branches.map(
                                    branch => (

                                        <option
                                            key={
                                                branch.id
                                            }
                                            value={
                                                branch.id
                                            }
                                        >
                                            {
                                                branch.name
                                            }
                                        </option>

                                    )
                                )
                            }

                        </select>

                    </div>

                </div>


                {
                    selectedBranch
                    && (

                        <section
                            className="
                                mt-6
                                rounded-2xl
                                border
                                border-[#eadfd6]
                                bg-white
                                p-5
                            "
                        >

                            <div
                                className="
                                    flex
                                    flex-col
                                    gap-4

                                    sm:flex-row
                                    sm:items-center
                                    sm:justify-between
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
                                            selectedBranch.name
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
                                            selectedBranch.code
                                        }
                                        {" • "}
                                        {
                                            selectedBranch.address
                                        }
                                    </p>

                                </div>


                                <button
                                    type="button"
                                    disabled={
                                        downloadingTemplate
                                    }
                                    onClick={
                                        handleDownloadTemplate
                                    }
                                    className="
                                        min-h-11
                                        rounded-xl
                                        bg-[#7a1625]
                                        px-5
                                        text-sm
                                        font-semibold
                                        text-white
                                        transition

                                        hover:bg-[#5d0f1b]

                                        disabled:cursor-not-allowed
                                        disabled:opacity-60
                                    "
                                >
                                    {
                                        downloadingTemplate
                                            ? "Downloading..."
                                            : "Download Excel Template"
                                    }
                                </button>

                            </div>

                        </section>

                    )
                }


                <section
                    className="
                        mt-6
                        rounded-2xl
                        border
                        border-[#eadfd6]
                        bg-white
                        p-5

                        sm:p-6
                    "
                >

                    <div
                        className="
                            flex
                            items-start
                            gap-4
                        "
                    >

                        <div
                            className="
                                flex
                                h-9
                                w-9
                                shrink-0
                                items-center
                                justify-center
                                rounded-full
                                bg-[#fff1e9]
                                text-sm
                                font-bold
                                text-[#7a1625]
                            "
                        >
                            1
                        </div>


                        <div
                            className="
                                min-w-0
                                flex-1
                            "
                        >

                            <h2
                                className="
                                    text-lg
                                    font-bold
                                    text-[#241715]
                                "
                            >
                                Prepare the spreadsheet
                            </h2>


                            <p
                                className="
                                    mt-2
                                    text-sm
                                    leading-6
                                    text-[#756763]
                                "
                            >
                                Download the template for the selected branch and fill in the menu rows. Keep the template columns unchanged.
                            </p>

                        </div>

                    </div>


                    <div
                        className="
                            mt-6
                            border-t
                            border-[#eadfd6]
                            pt-6
                        "
                    >

                        <div
                            className="
                                flex
                                items-start
                                gap-4
                            "
                        >

                            <div
                                className="
                                    flex
                                    h-9
                                    w-9
                                    shrink-0
                                    items-center
                                    justify-center
                                    rounded-full
                                    bg-[#fff1e9]
                                    text-sm
                                    font-bold
                                    text-[#7a1625]
                                "
                            >
                                2
                            </div>


                            <div
                                className="
                                    min-w-0
                                    flex-1
                                "
                            >

                                <h2
                                    className="
                                        text-lg
                                        font-bold
                                        text-[#241715]
                                    "
                                >
                                    Select the completed Excel file
                                </h2>


                                <p
                                    className="
                                        mt-2
                                        text-sm
                                        leading-6
                                        text-[#756763]
                                    "
                                >
                                    Only Excel .xlsx files are accepted.
                                </p>


                                <label
                                    className="
                                        mt-4
                                        block
                                    "
                                >

                                    <span
                                        className="
                                            sr-only
                                        "
                                    >
                                        Select Excel menu file
                                    </span>


                                    <input
                                        type="file"
                                        accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                        onChange={
                                            event =>
                                                handleFileChange(
                                                    event.target.files?.[0]
                                                    ?? null
                                                )
                                        }
                                        className="
                                            block
                                            w-full
                                            rounded-xl
                                            border
                                            border-[#eadfd6]
                                            bg-[#fffaf3]
                                            text-sm
                                            text-[#756763]

                                            file:mr-4
                                            file:border-0
                                            file:bg-[#7a1625]
                                            file:px-4
                                            file:py-3
                                            file:text-sm
                                            file:font-semibold
                                            file:text-white
                                        "
                                    />

                                </label>


                                {
                                    selectedFile
                                    && (

                                        <div
                                            className="
                                                mt-4
                                                rounded-xl
                                                border
                                                border-[#eadfd6]
                                                bg-[#fffaf3]
                                                px-4
                                                py-3
                                            "
                                        >

                                            <p
                                                className="
                                                    text-sm
                                                    font-semibold
                                                    text-[#241715]
                                                "
                                            >
                                                {
                                                    selectedFile.name
                                                }
                                            </p>


                                            <p
                                                className="
                                                    mt-1
                                                    text-xs
                                                    text-[#756763]
                                                "
                                            >
                                                {
                                                    (
                                                        selectedFile.size
                                                        / 1024
                                                    ).toFixed(
                                                        1
                                                    )
                                                } KB
                                            </p>

                                        </div>

                                    )
                                }

                            </div>

                        </div>

                    </div>


                    <div
                        className="
                            mt-6
                            border-t
                            border-[#eadfd6]
                            pt-6
                        "
                    >

                        <div
                            className="
                                flex
                                items-start
                                gap-4
                            "
                        >

                            <div
                                className="
                                    flex
                                    h-9
                                    w-9
                                    shrink-0
                                    items-center
                                    justify-center
                                    rounded-full
                                    bg-[#fff1e9]
                                    text-sm
                                    font-bold
                                    text-[#7a1625]
                                "
                            >
                                3
                            </div>


                            <div
                                className="
                                    min-w-0
                                    flex-1
                                "
                            >

                                <h2
                                    className="
                                        text-lg
                                        font-bold
                                        text-[#241715]
                                    "
                                >
                                    Validate before importing
                                </h2>


                                <p
                                    className="
                                        mt-2
                                        text-sm
                                        leading-6
                                        text-[#756763]
                                    "
                                >
                                    Validation checks the spreadsheet without changing the database.
                                </p>


                                <button
                                    type="button"
                                    disabled={
                                        selectedFile
                                        === null
                                        ||
                                        selectedBranchId
                                        === null
                                        ||
                                        validating
                                    }
                                    onClick={
                                        handleValidate
                                    }
                                    className="
                                        mt-4
                                        min-h-11
                                        rounded-xl
                                        border
                                        border-[#7a1625]
                                        bg-white
                                        px-5
                                        text-sm
                                        font-semibold
                                        text-[#7a1625]
                                        transition

                                        hover:bg-[#fff1e9]

                                        disabled:cursor-not-allowed
                                        disabled:opacity-50
                                    "
                                >
                                    {
                                        validating
                                            ? "Validating..."
                                            : "Validate File"
                                    }
                                </button>

                            </div>

                        </div>

                    </div>

                </section>


                {
                    error
                    && (

                        <div
                            role="alert"
                            className="
                                mt-6
                                rounded-2xl
                                border
                                border-red-200
                                bg-red-50
                                px-5
                                py-4
                                text-sm
                                font-medium
                                text-red-700
                            "
                        >
                            {error}
                        </div>

                    )
                }


                {
                    validationResult
                    && (

                        <section
                            className="
                                mt-6
                                overflow-hidden
                                rounded-2xl
                                border
                                border-[#eadfd6]
                                bg-white
                            "
                        >

                            <div
                                className={[
                                    "px-5 py-4 sm:px-6",

                                    validationResult.valid
                                        ? "bg-green-50"
                                        : "bg-red-50"
                                ].join(
                                    " "
                                )}
                            >

                                <p
                                    className={[
                                        "font-bold",

                                        validationResult.valid
                                            ? "text-green-800"
                                            : "text-red-800"
                                    ].join(
                                        " "
                                    )}
                                >
                                    {
                                        validationResult.valid
                                            ? "Validation passed"
                                            : "Validation failed"
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
                                        validationResult.totalRows
                                    } data rows checked
                                </p>

                            </div>


                            {
                                validationResult.errors.length
                                > 0
                                && (

                                    <div
                                        className="
                                            overflow-x-auto
                                        "
                                    >

                                        <table
                                            className="
                                                w-full
                                                min-w-700px
                                                border-collapse
                                                text-left
                                                text-sm
                                            "
                                        >

                                            <thead
                                                className="
                                                    bg-[#fffaf3]
                                                    text-[#756763]
                                                "
                                            >

                                                <tr>

                                                    <th
                                                        className="
                                                            border-b
                                                            border-[#eadfd6]
                                                            px-5
                                                            py-3
                                                            font-semibold
                                                        "
                                                    >
                                                        Row
                                                    </th>

                                                    <th
                                                        className="
                                                            border-b
                                                            border-[#eadfd6]
                                                            px-5
                                                            py-3
                                                            font-semibold
                                                        "
                                                    >
                                                        Column
                                                    </th>

                                                    <th
                                                        className="
                                                            border-b
                                                            border-[#eadfd6]
                                                            px-5
                                                            py-3
                                                            font-semibold
                                                        "
                                                    >
                                                        Problem
                                                    </th>

                                                </tr>

                                            </thead>


                                            <tbody>

                                                {
                                                    validationResult.errors.map(
                                                        (
                                                            validationError,
                                                            index
                                                        ) => (

                                                            <tr
                                                                key={
                                                                    [
                                                                        validationError.row,
                                                                        validationError.column,
                                                                        index
                                                                    ].join(
                                                                        "-"
                                                                    )
                                                                }
                                                            >

                                                                <td
                                                                    className="
                                                                        border-b
                                                                        border-[#f1e8e1]
                                                                        px-5
                                                                        py-3
                                                                        font-semibold
                                                                        text-[#241715]
                                                                    "
                                                                >
                                                                    {
                                                                        validationError.row
                                                                    }
                                                                </td>


                                                                <td
                                                                    className="
                                                                        border-b
                                                                        border-[#f1e8e1]
                                                                        px-5
                                                                        py-3
                                                                        text-[#756763]
                                                                    "
                                                                >
                                                                    {
                                                                        validationError.column
                                                                    }
                                                                </td>


                                                                <td
                                                                    className="
                                                                        border-b
                                                                        border-[#f1e8e1]
                                                                        px-5
                                                                        py-3
                                                                        text-red-700
                                                                    "
                                                                >
                                                                    {
                                                                        validationError.message
                                                                    }
                                                                </td>

                                                            </tr>

                                                        )
                                                    )
                                                }

                                            </tbody>

                                        </table>

                                    </div>

                                )
                            }


                            {
                                validationResult.valid
                                && (

                                    <div
                                        className="
                                            flex
                                            flex-col
                                            gap-4
                                            border-t
                                            border-[#eadfd6]
                                            px-5
                                            py-5

                                            sm:flex-row
                                            sm:items-center
                                            sm:justify-between

                                            sm:px-6
                                        "
                                    >

                                        <div>

                                            <p
                                                className="
                                                    font-semibold
                                                    text-[#241715]
                                                "
                                            >
                                                Ready to import
                                            </p>


                                            <p
                                                className="
                                                    mt-1
                                                    text-sm
                                                    text-[#756763]
                                                "
                                            >
                                                {
                                                    validationResult.totalRows
                                                } validated rows will be processed.
                                            </p>

                                        </div>


                                        <button
                                            type="button"
                                            disabled={
                                                !canImport
                                            }
                                            onClick={
                                                handleImport
                                            }
                                            className="
                                                min-h-11
                                                rounded-xl
                                                bg-[#7a1625]
                                                px-6
                                                text-sm
                                                font-semibold
                                                text-white
                                                transition

                                                hover:bg-[#5d0f1b]

                                                disabled:cursor-not-allowed
                                                disabled:opacity-60
                                            "
                                        >
                                            {
                                                importing
                                                    ? "Importing..."
                                                    : "Confirm Import"
                                            }
                                        </button>

                                    </div>

                                )
                            }

                        </section>

                    )
                }


                {
                    importResult
                    && (

                        <section
                            className="
                                mt-6
                                rounded-2xl
                                border
                                border-green-200
                                bg-green-50
                                p-5

                                sm:p-6
                            "
                        >

                            <h2
                                className="
                                    text-lg
                                    font-bold
                                    text-green-900
                                "
                            >
                                Menu import completed
                            </h2>


                            <p
                                className="
                                    mt-2
                                    text-sm
                                    text-green-800
                                "
                            >
                                {
                                    importResult.rowsProcessed
                                } rows were processed successfully.
                            </p>


                            <div
                                className="
                                    mt-5
                                    grid
                                    gap-3

                                    sm:grid-cols-2
                                    xl:grid-cols-3
                                "
                            >

                                <ImportStat
                                    label="Categories created"
                                    value={
                                        importResult.categoriesCreated
                                    }
                                />

                                <ImportStat
                                    label="Categories updated"
                                    value={
                                        importResult.categoriesUpdated
                                    }
                                />

                                <ImportStat
                                    label="Products created"
                                    value={
                                        importResult.productsCreated
                                    }
                                />

                                <ImportStat
                                    label="Products updated"
                                    value={
                                        importResult.productsUpdated
                                    }
                                />

                                <ImportStat
                                    label="Branch items created"
                                    value={
                                        importResult.branchProductsCreated
                                    }
                                />

                                <ImportStat
                                    label="Branch items updated"
                                    value={
                                        importResult.branchProductsUpdated
                                    }
                                />

                            </div>


                            <div
                                className="
                                    mt-5
                                "
                            >

                                <Link
                                    href="/admin/menu/live"
                                    className="
                                        inline-flex
                                        min-h-11
                                        items-center
                                        rounded-xl
                                        bg-[#7a1625]
                                        px-5
                                        text-sm
                                        font-semibold
                                        text-white
                                        transition

                                        hover:bg-[#5d0f1b]
                                    "
                                >
                                    Open Live Menu
                                </Link>

                            </div>

                        </section>

                    )
                }

            </div>

        </div>
    );
}


function ImportStat({
    label,
    value
}: {
    label: string;
    value: number;
}) {

    return (
        <div
            className="
                rounded-xl
                border
                border-green-200
                bg-white
                p-4
            "
        >

            <p
                className="
                    text-sm
                    text-[#756763]
                "
            >
                {label}
            </p>


            <p
                className="
                    mt-2
                    text-2xl
                    font-bold
                    text-[#241715]
                "
            >
                {value}
            </p>

        </div>
    );
}