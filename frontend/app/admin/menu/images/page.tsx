"use client";
import Link from "next/link";

import {
    useEffect,
    useMemo,
    useRef,
    useState,
} from "react";

import Image from "next/image";

import {
    useAdminAuth
} from "@/contexts/AdminAuthContext";

import {
    removeProductImage,
    uploadProductImage,
} from "@/services/productApi";

import {
    useMenuForBranch
} from "@/hooks/useMenuForBranch";

import type {
    MenuProduct,
} from "@/types/menu";


/* -------------------------------------------------------------------------- */
/* Types                                                                      */
/* -------------------------------------------------------------------------- */

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


type ImageFilter =
    | "ALL"
    | "WITH_IMAGE"
    | "WITHOUT_IMAGE";


/* -------------------------------------------------------------------------- */
/* Configuration                                                              */
/* -------------------------------------------------------------------------- */

const API_BASE =
    process.env.NEXT_PUBLIC_API_BASE_URL
    ?? "http://localhost:8080";


const MAX_FILE_SIZE =
    5 * 1024 * 1024;


const ALLOWED_IMAGE_TYPES = [
    "image/jpeg",
    "image/png",
    "image/webp",
];


/* -------------------------------------------------------------------------- */
/* Page                                                                       */
/* -------------------------------------------------------------------------- */

export default function ImageManagementPage() {
    const restoredProduct = useRef<number | null>(null);
    const restored = useRef(false);
    const [preferencesLoaded, setPreferencesLoaded] = useState(false);

    const {
        profile
    } = useAdminAuth();


    /* ---------------------------------------------------------------------- */
    /* Branch state                                                           */
    /* ---------------------------------------------------------------------- */

    const [
        branches,
        setBranches
    ] = useState<Branch[]>(
        []
    );


    const [
        selectedBranchId,
        setSelectedBranchId
    ] = useState<number | null>(
        null
    );


    const [
        branchesLoading,
        setBranchesLoading
    ] = useState(
        true
    );


    const [
        branchError,
        setBranchError
    ] = useState<string | null>(
        null
    );


    /* ---------------------------------------------------------------------- */
    /* Page state                                                             */
    /* ---------------------------------------------------------------------- */

    const [
        search,
        setSearch
    ] = useState("");


    const [
        categoryFilter,
        setCategoryFilter
    ] = useState("ALL");


    const [
        imageFilter,
        setImageFilter
    ] = useState<ImageFilter>(
        "ALL"
    );


    const [
        selectedProduct,
        setSelectedProduct
    ] = useState<MenuProduct | null>(
        null
    );


    const [
        refreshing,
        setRefreshing
    ] = useState(false);

    useEffect(() => {
        if (!preferencesLoaded) return;
        sessionStorage.setItem("gokul-admin-image-context", JSON.stringify({
            branchId: selectedBranchId, search, category: categoryFilter, image: imageFilter, productId: selectedProduct?.id ?? restoredProduct.current
        }));
    }, [preferencesLoaded, selectedBranchId, search, categoryFilter, imageFilter, selectedProduct]);


    /* ---------------------------------------------------------------------- */
    /* Load allowed branches                                                  */
    /* ---------------------------------------------------------------------- */

    useEffect(
        () => {

            /*
             * Do not call setState synchronously here when profile
             * is unavailable. AdminAuthContext may still be loading.
             *
             * When profile becomes available, this effect runs again.
             */
            if (!profile) {
                return;
            }


            /*
             * Capture the non-null profile so TypeScript knows it
             * cannot become null inside the async function.
             */
            const currentProfile =
                profile;


            const controller =
                new AbortController();


            async function loadBranches() {

                try {

                    setBranchesLoading(
                        true
                    );

                    setBranchError(
                        null
                    );


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


                    if (!restored.current) {
                        try {
                            const saved = JSON.parse(sessionStorage.getItem("gokul-admin-image-context") ?? "null");
                            if (saved) {
                                setSelectedBranchId(saved.branchId ?? null); setSearch(saved.search ?? "");
                                setCategoryFilter(saved.category ?? "ALL");
                                if (["ALL", "WITH_IMAGE", "WITHOUT_IMAGE"].includes(saved.image)) setImageFilter(saved.image);
                                restoredProduct.current = saved.productId ?? null;
                            }
                        } catch (error) {console.warn("Image management preferences could not be restored.", error);}
                        restored.current = true; setPreferencesLoaded(true);
                    }
                    setBranches(
                        allowedBranches
                    );


                    setSelectedBranchId(
                        previousBranchId => {

                            /*
                             * Keep the current branch if it is still
                             * available to the current admin.
                             */
                            if (
                                previousBranchId
                                &&
                                allowedBranches.some(
                                    branch =>
                                        branch.id
                                        === previousBranchId
                                )
                            ) {

                                return previousBranchId;

                            }


                            return allowedBranches.length > 0
                                ? allowedBranches[0].id
                                : null;

                        }
                    );

                } catch (exception) {

                    if (
                        exception instanceof DOMException
                        &&
                        exception.name === "AbortError"
                    ) {

                        return;
                    }


                    setBranchError(
                        exception instanceof Error
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
     * If AdminAuthContext has not produced a profile yet, consider the
     * branch selector to still be loading.
     *
     * This avoids the synchronous setState-in-effect warning.
     */
    const isBranchLoading =
        !profile
        ||
        branchesLoading;


    /* ---------------------------------------------------------------------- */
    /* Load menu for selected branch                                          */
    /* ---------------------------------------------------------------------- */

    const {
        categories,
        loading,
        error,
        refresh,
    } =
        useMenuForBranch(
            selectedBranchId
        );


    /* ---------------------------------------------------------------------- */
    /* Flatten categories into products                                       */
    /* ---------------------------------------------------------------------- */

    const products =
        useMemo(
            () => {

                return categories.flatMap(
                    category =>
                        category.products.map(
                            product => ({
                                ...product,

                                categoryName:
                                    product.categoryName
                                    ||
                                    category.name,
                            })
                        )
                );

            },
            [
                categories
            ]
        );


    /* ---------------------------------------------------------------------- */
    /* Apply filters                                                          */
    /* ---------------------------------------------------------------------- */

    useEffect(() => {
        if (!restoredProduct.current || !products.length) return;
        const product = products.find(item => item.id === restoredProduct.current);
        if (product) setSelectedProduct(product);
        restoredProduct.current = null;
    }, [products]);

    const filteredProducts =
        useMemo(
            () => {

                const normalizedSearch =
                    search
                        .trim()
                        .toLowerCase();


                return products.filter(
                    product => {

                        const matchesSearch =
                            !normalizedSearch
                            ||
                            product.name
                                .toLowerCase()
                                .includes(
                                    normalizedSearch
                                );


                        const matchesCategory =
                            categoryFilter === "ALL"
                            ||
                            String(
                                product.categoryId
                            )
                            ===
                            categoryFilter;


                        const hasImage =
                            Boolean(
                                product.imageUrl
                            );


                        const matchesImageFilter =
                            imageFilter === "ALL"
                            ||
                            (
                                imageFilter
                                === "WITH_IMAGE"
                                &&
                                hasImage
                            )
                            ||
                            (
                                imageFilter
                                === "WITHOUT_IMAGE"
                                &&
                                !hasImage
                            );


                        return (
                            matchesSearch
                            &&
                            matchesCategory
                            &&
                            matchesImageFilter
                        );

                    }
                );

            },
            [
                products,
                search,
                categoryFilter,
                imageFilter
            ]
        );


    /* ---------------------------------------------------------------------- */
    /* Statistics                                                             */
    /* ---------------------------------------------------------------------- */

    const totalProducts =
        products.length;


    const productsWithImages =
        products.filter(
            product =>
                Boolean(
                    product.imageUrl
                )
        ).length;


    const productsWithoutImages =
        totalProducts -
        productsWithImages;


    /* ---------------------------------------------------------------------- */
    /* Selected branch                                                        */
    /* ---------------------------------------------------------------------- */

    const selectedBranch =
        branches.find(
            branch =>
                branch.id
                === selectedBranchId
        );


    /* ---------------------------------------------------------------------- */
    /* Branch change                                                          */
    /* ---------------------------------------------------------------------- */

    function handleBranchChange(
        branchId: number
    ) {

        setSelectedBranchId(
            branchId
        );


        setSelectedProduct(
            null
        );


        setSearch(
            ""
        );


        setCategoryFilter(
            "ALL"
        );


        setImageFilter(
            "ALL"
        );

    }


    /* ---------------------------------------------------------------------- */
    /* Manual refresh                                                         */
    /* ---------------------------------------------------------------------- */

    async function handleRefresh() {

        if (!selectedBranchId) {
            return;
        }


        try {

            setRefreshing(
                true
            );


            await refresh();

        } finally {

            setRefreshing(
                false
            );

        }

    }


    /* ---------------------------------------------------------------------- */
    /* Remove image                                                           */
    /* ---------------------------------------------------------------------- */

    async function handleRemoveImage(
        product: MenuProduct
    ) {

        const confirmed =
            window.confirm(
                `Remove the image for "${product.name}"?`
            );


        if (!confirmed) {
            return;
        }


        try {

            setRefreshing(
                true
            );


            await removeProductImage(
                product.id
            );


            await refresh();


            if (
                selectedProduct?.id
                ===
                product.id
            ) {

                setSelectedProduct(
                    null
                );

            }

        } catch (exception) {

            console.error(
                exception
            );

        } finally {

            setRefreshing(
                false
            );

        }

    }


    /* ---------------------------------------------------------------------- */
    /* Image uploaded                                                         */
    /* ---------------------------------------------------------------------- */

    async function handleImageUploaded() {

        setSelectedProduct(
            null
        );


        await refresh();

    }


    /* ---------------------------------------------------------------------- */
    /* Render                                                                 */
    /* ---------------------------------------------------------------------- */

    return (
        <div className="min-h-screen bg-slate-50">

            <div className="mx-auto w-full max-w-7xl px-4 py-6 sm:px-6 sm:py-8 lg:px-8">

                {/* ========================================================= */}
                {/* Header                                                      */}
                {/* ========================================================= */}

                <div className="mb-8 flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between">

                    <div className="min-w-0">

                        <div className="mb-2 flex items-center gap-2 text-sm font-medium text-slate-500">

                            <Link href="/admin/menu" className="inline-flex min-h-11 items-center underline">Back to menu</Link>

                            <span>
                                /
                            </span>

                            <span className="text-slate-900">
                                Image Management
                            </span>

                        </div>


                        <h1 className="text-3xl font-bold tracking-tight text-slate-900 sm:text-4xl">
                            Menu Image Management
                        </h1>


                        <p className="mt-3 max-w-3xl text-sm leading-6 text-slate-600">
                            Upload, crop, replace, and remove product
                            images used on your customer menu.
                        </p>

                    </div>


                    {/* Branch selector */}

                    <div className="w-full shrink-0 lg:w-80">

                        <label
                            htmlFor="image-branch"
                            className="mb-2 block text-sm font-semibold text-slate-900"
                        >
                            Branch
                        </label>


                        <select
                            id="image-branch"
                            value={
                                selectedBranchId
                                ?? ""
                            }
                            disabled={
                                isBranchLoading
                                ||
                                branches.length === 0
                            }
                            onChange={
                                event => {

                                    const value =
                                        Number(
                                            event.target.value
                                        );


                                    handleBranchChange(
                                        value
                                    );

                                }
                            }
                            className="
                                min-h-12
                                w-full
                                rounded-xl
                                border
                                border-slate-200
                                bg-white
                                px-4
                                text-sm
                                font-semibold
                                text-slate-900
                                outline-none

                                focus:border-slate-400
                                focus:ring-4
                                focus:ring-slate-100

                                disabled:bg-slate-50
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


                {/* ========================================================= */}
                {/* Branch loading                                             */}
                {/* ========================================================= */}

                {
                    isBranchLoading
                    && (

                        <div className="mb-6 rounded-2xl border border-slate-200 bg-white p-4 text-sm text-slate-500">

                            Loading branches...

                        </div>

                    )
                }


                {/* ========================================================= */}
                {/* Branch error                                               */}
                {/* ========================================================= */}

                {
                    branchError
                    && (

                        <div
                            role="alert"
                            className="mb-6 rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-700"
                        >
                            {
                                branchError
                            }
                        </div>

                    )
                }


                {/* ========================================================= */}
                {/* Selected branch                                             */}
                {/* ========================================================= */}

                {
                    selectedBranch
                    && (

                        <section className="mb-6 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">

                            <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">

                                <div>

                                    <p className="font-bold text-slate-900">
                                        {
                                            selectedBranch.name
                                        }
                                    </p>


                                    <p className="mt-1 text-sm text-slate-500">

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
                                    onClick={
                                        handleRefresh
                                    }
                                    disabled={
                                        loading
                                        ||
                                        refreshing
                                        ||
                                        !selectedBranchId
                                    }
                                    className="
                                        inline-flex
                                        min-h-11
                                        items-center
                                        justify-center
                                        rounded-xl
                                        border
                                        border-slate-200
                                        bg-white
                                        px-4
                                        text-sm
                                        font-semibold
                                        text-slate-700
                                        shadow-sm
                                        transition

                                        hover:bg-slate-50

                                        disabled:cursor-not-allowed
                                        disabled:opacity-50
                                    "
                                >
                                    {
                                        refreshing
                                            ? "Refreshing..."
                                            : "Refresh"
                                    }
                                </button>

                            </div>

                        </section>

                    )
                }


                {/* ========================================================= */}
                {/* Menu error                                                  */}
                {/* ========================================================= */}

                {
                    error
                    && (

                        <div
                            role="alert"
                            className="mb-6 rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-700"
                        >
                            {
                                error
                            }
                        </div>

                    )
                }


                {/* ========================================================= */}
                {/* No branch                                                  */}
                {/* ========================================================= */}

                {
                    !isBranchLoading
                    &&
                    branches.length === 0
                    &&
                    !branchError
                    && (

                        <div className="mb-6 rounded-2xl border border-amber-200 bg-amber-50 p-5">

                            <div className="flex gap-3">

                                <div className="mt-0.5 text-amber-600">
                                    ⚠
                                </div>


                                <div>

                                    <h2 className="font-semibold text-amber-900">
                                        No branch available
                                    </h2>


                                    <p className="mt-1 text-sm text-amber-800">
                                        There are no active branches
                                        available for your account.
                                    </p>

                                </div>

                            </div>

                        </div>

                    )
                }


                {/* ========================================================= */}
                {/* Statistics                                                  */}
                {/* ========================================================= */}

                {
                    selectedBranchId
                    && (

                        <div className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-3">

                            <StatCard
                                label="Total Products"
                                value={
                                    totalProducts
                                }
                                description="Products in this branch"
                            />


                            <StatCard
                                label="Images Added"
                                value={
                                    productsWithImages
                                }
                                description="Products with images"
                            />


                            <StatCard
                                label="Images Missing"
                                value={
                                    productsWithoutImages
                                }
                                description="Products still needing images"
                            />

                        </div>

                    )
                }


                {/* ========================================================= */}
                {/* Filters                                                     */}
                {/* ========================================================= */}

                {
                    selectedBranchId
                    && (

                        <div className="mb-6 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">

                            <div className="grid grid-cols-1 gap-4 md:grid-cols-4">

                                <div className="md:col-span-2">

                                    <label
                                        htmlFor="product-search"
                                        className="mb-1.5 block text-xs font-semibold uppercase tracking-wide text-slate-500"
                                    >
                                        Search Product
                                    </label>


                                    <input
                                        id="product-search"
                                        type="text"
                                        value={
                                            search
                                        }
                                        onChange={
                                            event =>
                                                setSearch(
                                                    event.target.value
                                                )
                                        }
                                        placeholder="Search product name..."
                                        className="
                                            w-full
                                            rounded-xl
                                            border
                                            border-slate-200
                                            bg-slate-50
                                            px-4
                                            py-2.5
                                            text-sm
                                            outline-none
                                            transition

                                            focus:border-slate-400
                                            focus:bg-white
                                        "
                                    />

                                </div>


                                <div>

                                    <label
                                        htmlFor="category-filter"
                                        className="mb-1.5 block text-xs font-semibold uppercase tracking-wide text-slate-500"
                                    >
                                        Category
                                    </label>


                                    <select
                                        id="category-filter"
                                        value={
                                            categoryFilter
                                        }
                                        onChange={
                                            event =>
                                                setCategoryFilter(
                                                    event.target.value
                                                )
                                        }
                                        className="
                                            w-full
                                            rounded-xl
                                            border
                                            border-slate-200
                                            bg-slate-50
                                            px-4
                                            py-2.5
                                            text-sm
                                            outline-none
                                        "
                                    >

                                        <option value="ALL">
                                            All Categories
                                        </option>


                                        {
                                            categories.map(
                                                category => (

                                                    <option
                                                        key={
                                                            category.id
                                                        }
                                                        value={
                                                            category.id
                                                        }
                                                    >
                                                        {
                                                            category.name
                                                        }
                                                    </option>

                                                )
                                            )
                                        }

                                    </select>

                                </div>


                                <div>

                                    <label
                                        htmlFor="image-filter"
                                        className="mb-1.5 block text-xs font-semibold uppercase tracking-wide text-slate-500"
                                    >
                                        Image Status
                                    </label>


                                    <select
                                        id="image-filter"
                                        value={
                                            imageFilter
                                        }
                                        onChange={
                                            event =>
                                                setImageFilter(
                                                    event.target.value as ImageFilter
                                                )
                                        }
                                        className="
                                            w-full
                                            rounded-xl
                                            border
                                            border-slate-200
                                            bg-slate-50
                                            px-4
                                            py-2.5
                                            text-sm
                                            outline-none
                                        "
                                    >

                                        <option value="ALL">
                                            All Products
                                        </option>


                                        <option value="WITH_IMAGE">
                                            Has Image
                                        </option>


                                        <option value="WITHOUT_IMAGE">
                                            Missing Image
                                        </option>

                                    </select>

                                </div>

                            </div>

                        </div>

                    )
                }


                {/* ========================================================= */}
                {/* Loading                                                     */}
                {/* ========================================================= */}

                {
                    selectedBranchId
                    &&
                    loading
                    && (

                        <div className="rounded-2xl border border-slate-200 bg-white p-12 text-center">

                            <div className="mx-auto h-8 w-8 animate-spin rounded-full border-2 border-slate-200 border-t-slate-700" />


                            <p className="mt-4 text-sm text-slate-500">
                                Loading menu products...
                            </p>

                        </div>

                    )
                }


                {/* ========================================================= */}
                {/* Empty                                                      */}
                {/* ========================================================= */}

                {
                    !loading
                    &&
                    selectedBranchId
                    &&
                    filteredProducts.length === 0
                    && (

                        <div className="rounded-2xl border border-slate-200 bg-white p-12 text-center">

                            <div className="text-4xl">
                                🖼️
                            </div>


                            <h2 className="mt-4 text-lg font-semibold text-slate-900">
                                No products found
                            </h2>


                            <p className="mt-1 text-sm text-slate-500">
                                Try changing your search
                                or filters.
                            </p>

                        </div>

                    )
                }


                {/* ========================================================= */}
                {/* Product grid                                                */}
                {/* ========================================================= */}

                {
                    !loading
                    &&
                    selectedBranchId
                    &&
                    filteredProducts.length > 0
                    && (

                        <div className="grid grid-cols-2 gap-3 sm:grid-cols-2 sm:gap-4 lg:grid-cols-3 xl:grid-cols-4">

                            {
                                filteredProducts.map(
                                    product => (

                                        <ProductImageCard
                                            key={
                                                product.id
                                            }
                                            product={
                                                product
                                            }
                                            onManage={
                                                () =>
                                                    setSelectedProduct(
                                                        product
                                                    )
                                            }
                                            onRemove={
                                                () =>
                                                    handleRemoveImage(
                                                        product
                                                    )
                                            }
                                        />

                                    )
                                )
                            }

                        </div>

                    )
                }


                {/* ========================================================= */}
                {/* Result count                                                */}
                {/* ========================================================= */}

                {
                    !loading
                    &&
                    selectedBranchId
                    && (

                        <div className="mt-6 text-center text-xs text-slate-500">

                            Showing{" "}

                            <span className="font-semibold text-slate-700">
                                {
                                    filteredProducts.length
                                }
                            </span>

                            {" "}of{" "}

                            <span className="font-semibold text-slate-700">
                                {
                                    totalProducts
                                }
                            </span>

                            {" "}products

                        </div>

                    )
                }

            </div>


            {/* ============================================================= */}
            {/* Upload / Crop modal                                            */}
            {/* ============================================================= */}

            {
                selectedProduct
                && (

                    <ImageUploadModal
                        product={
                            selectedProduct
                        }
                        onClose={
                            () =>
                                setSelectedProduct(
                                    null
                                )
                        }
                        onUploaded={
                            handleImageUploaded
                        }
                    />

                )
            }

        </div>
    );
}


/* -------------------------------------------------------------------------- */
/* Stat Card                                                                  */
/* -------------------------------------------------------------------------- */

function StatCard({
    label,
    value,
    description,
}: {
    label: string;
    value: number;
    description: string;
}) {

    return (
        <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm sm:p-5">

            <p className="text-sm font-medium text-slate-500">
                {
                    label
                }
            </p>


            <p className="mt-2 text-2xl font-bold text-slate-900 sm:text-3xl">
                {
                    value
                }
            </p>


            <p className="mt-1 text-xs text-slate-500">
                {
                    description
                }
            </p>

        </div>
    );
}


/* -------------------------------------------------------------------------- */
/* Product Image Card                                                         */
/* -------------------------------------------------------------------------- */

function ProductImageCard({
    product,
    onManage,
    onRemove,
}: {
    product: MenuProduct;
    onManage: () => void;
    onRemove: () => void;
}) {

    return (
        <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm transition hover:shadow-md sm:rounded-2xl">

            {/* Image */}

            <div className="relative aspect-[4/3] overflow-hidden bg-slate-100 sm:aspect-[16/10]">

                {
                    product.imageUrl
                        ? (

                            <Image
                                src={
                                    product.imageUrl
                                }
                                alt={
                                    product.name
                                }
                                fill
                                sizes="(max-width: 640px) 50vw, (max-width: 1024px) 50vw, 25vw"
                                className="object-cover"
                            />

                        )
                        : (

                            <div className="flex h-full flex-col items-center justify-center text-slate-400">

                                <div className="text-3xl sm:text-4xl">
                                    🖼️
                                </div>


                                <p className="mt-1 text-xs font-medium sm:mt-2 sm:text-sm">
                                    No image
                                </p>

                            </div>

                        )
                }


                <div className="absolute left-2 top-2 sm:left-3 sm:top-3">

                    {
                        product.imageUrl
                            ? (

                                <span className="rounded-full bg-emerald-100 px-2 py-1 text-[9px] font-semibold text-emerald-700 sm:px-2.5 sm:py-1 sm:text-xs">
                                    Image Added
                                </span>

                            )
                            : (

                                <span className="rounded-full bg-amber-100 px-2 py-1 text-[9px] font-semibold text-amber-700 sm:px-2.5 sm:py-1 sm:text-xs">
                                    Missing
                                </span>

                            )
                    }

                </div>

            </div>


            {/* Details */}

            <div className="p-3 sm:p-4">

                <div className="flex items-start justify-between gap-2">

                    <div className="min-w-0">

                        <h3 className="truncate text-sm font-semibold text-slate-900 sm:text-base">
                            {
                                product.name
                            }
                        </h3>


                        <p className="mt-0.5 truncate text-[10px] text-slate-500 sm:mt-1 sm:text-xs">
                            {
                                product.categoryName
                            }
                        </p>

                    </div>


                    <span className="shrink-0 text-xs font-semibold text-slate-900 sm:text-sm">
                        ₹{
                            product.price
                        }
                    </span>

                </div>


                {/* Actions */}

                <div className="mt-3 flex gap-1.5 sm:mt-4 sm:gap-2">

                    <button
                        type="button"
                        onClick={
                            onManage
                        }
                        className="
                            min-w-0
                            flex-1
                            rounded-lg
                            bg-slate-900
                            px-2
                            py-2
                            text-[11px]
                            font-semibold
                            text-white
                            transition

                            hover:bg-slate-800

                            sm:rounded-xl
                            sm:px-3
                            sm:py-2.5
                            sm:text-sm
                        "
                    >
                        {
                            product.imageUrl
                                ? "Change"
                                : "Upload"
                        }
                    </button>


                    {
                        product.imageUrl
                        && (

                            <button
                                type="button"
                                onClick={
                                    onRemove
                                }
                                className="
                                    rounded-lg
                                    border
                                    border-red-200
                                    px-2
                                    py-2
                                    text-[11px]
                                    font-semibold
                                    text-red-600
                                    transition

                                    hover:bg-red-50

                                    sm:rounded-xl
                                    sm:px-3
                                    sm:py-2.5
                                    sm:text-sm
                                "
                            >
                                Remove
                            </button>

                        )
                    }

                </div>

            </div>

        </div>
    );
}


/* -------------------------------------------------------------------------- */
/* Upload / Crop Modal                                                        */
/* -------------------------------------------------------------------------- */

function ImageUploadModal({
    product,
    onClose,
    onUploaded,
}: {
    product: MenuProduct;
    onClose: () => void;
    onUploaded: () => Promise<void>;
}) {

    const [
        selectedFile,
        setSelectedFile
    ] = useState<File | null>(
        null
    );


    const [
        previewUrl,
        setPreviewUrl
    ] = useState<string | null>(
        product.imageUrl
    );


    const [
        uploading,
        setUploading
    ] = useState(false);


    const [
        error,
        setError
    ] = useState<string | null>(
        null
    );


    const [
        cropMode,
        setCropMode
    ] = useState(false);


    const [
        cropZoom,
        setCropZoom
    ] = useState(1);


    const [
        cropX,
        setCropX
    ] = useState(0);


    const [
        cropY,
        setCropY
    ] = useState(0);


    const [
        imageNaturalWidth,
        setImageNaturalWidth
    ] = useState(0);


    const [
        imageNaturalHeight,
        setImageNaturalHeight
    ] = useState(0);


    /* ---------------------------------------------------------------------- */
    /* Blob preview cleanup                                                   */
    /* ---------------------------------------------------------------------- */

    useEffect(
        () => {

            return () => {

                if (
                    previewUrl
                    &&
                    previewUrl.startsWith(
                        "blob:"
                    )
                ) {

                    URL.revokeObjectURL(
                        previewUrl
                    );

                }

            };

        },
        [
            previewUrl
        ]
    );


    /* ---------------------------------------------------------------------- */
    /* Reset crop                                                             */
    /* ---------------------------------------------------------------------- */

    function resetCrop() {

        setCropZoom(
            1
        );


        setCropX(
            0
        );


        setCropY(
            0
        );

    }


    /* ---------------------------------------------------------------------- */
    /* File selection                                                         */
    /* ---------------------------------------------------------------------- */

    function handleFileChange(
        event: React.ChangeEvent<HTMLInputElement>
    ) {

        const file =
            event.target.files?.[0];


        if (!file) {
            return;
        }


        setError(
            null
        );


        if (
            !ALLOWED_IMAGE_TYPES.includes(
                file.type
            )
        ) {

            setError(
                "Only JPG, PNG and WebP images are allowed."
            );


            event.target.value =
                "";


            return;
        }


        if (
            file.size
            >
            MAX_FILE_SIZE
        ) {

            setError(
                "Image must be 5 MB or smaller."
            );


            event.target.value =
                "";


            return;
        }


        if (
            previewUrl
            &&
            previewUrl.startsWith(
                "blob:"
            )
        ) {

            URL.revokeObjectURL(
                previewUrl
            );

        }


        const objectUrl =
            URL.createObjectURL(
                file
            );


        setSelectedFile(
            file
        );


        setPreviewUrl(
            objectUrl
        );


        setCropMode(
            false
        );


        resetCrop();

    }


    /* ---------------------------------------------------------------------- */
    /* Image loaded                                                           */
    /* ---------------------------------------------------------------------- */

    function handleImageLoad(
        event: React.SyntheticEvent<HTMLImageElement>
    ) {

        const image =
            event.currentTarget;


        setImageNaturalWidth(
            image.naturalWidth
        );


        setImageNaturalHeight(
            image.naturalHeight
        );

    }


    /* ---------------------------------------------------------------------- */
    /* Crop image                                                             */
    /* ---------------------------------------------------------------------- */

    async function createCroppedImage(): Promise<File> {

        if (!previewUrl) {

            throw new Error(
                "Please select an image first."
            );

        }


        const image =
            new window.Image();


        image.src =
            previewUrl;


        await new Promise<void>(
            (
                resolve,
                reject
            ) => {

                image.onload = () =>
                    resolve();

                image.onerror = () =>
                    reject(
                        new Error(
                            "Unable to read the selected image."
                        )
                    );

            }
        );


        /*
         * The output is a square image.
         *
         * This gives the customer menu a consistent visual
         * appearance on mobile and desktop.
         */
        const outputSize =
            1200;


        const sourceSize =
            Math.min(
                image.naturalWidth,
                image.naturalHeight
            )
            /
            cropZoom;


        const centerX =
            image.naturalWidth / 2
            +
            cropX
            *
            (
                image.naturalWidth
                /
                2
            );


        const centerY =
            image.naturalHeight / 2
            +
            cropY
            *
            (
                image.naturalHeight
                /
                2
            );


        let sourceX =
            centerX
            -
            sourceSize / 2;


        let sourceY =
            centerY
            -
            sourceSize / 2;


        sourceX =
            Math.max(
                0,
                Math.min(
                    sourceX,
                    image.naturalWidth
                    -
                    sourceSize
                )
            );


        sourceY =
            Math.max(
                0,
                Math.min(
                    sourceY,
                    image.naturalHeight
                    -
                    sourceSize
                )
            );


        const canvas =
            document.createElement(
                "canvas"
            );


        canvas.width =
            outputSize;

        canvas.height =
            outputSize;


        const context =
            canvas.getContext(
                "2d"
            );


        if (!context) {

            throw new Error(
                "Unable to prepare the cropped image."
            );

        }


        context.imageSmoothingEnabled =
            true;


        context.imageSmoothingQuality =
            "high";


        context.drawImage(
            image,
            sourceX,
            sourceY,
            sourceSize,
            sourceSize,
            0,
            0,
            outputSize,
            outputSize
        );


        const blob =
            await new Promise<Blob | null>(
                resolve =>
                    canvas.toBlob(
                        resolve,
                        "image/webp",
                        0.88
                    )
            );


        if (!blob) {

            throw new Error(
                "Unable to create the cropped image."
            );

        }


        return new File(
            [
                blob
            ],
            `${product.name
                .trim()
                .replace(
                    /[^a-zA-Z0-9]+/g,
                    "-"
                )
                .replace(
                    /^-+|-+$/g,
                    ""
                )
                .toLowerCase()
            }.webp`,
            {
                type:
                    "image/webp"
            }
        );

    }


    /* ---------------------------------------------------------------------- */
    /* Upload                                                                 */
    /* ---------------------------------------------------------------------- */

    async function handleUpload() {

        if (!selectedFile) {

            setError(
                "Please select an image first."
            );

            return;
        }


        try {

            setUploading(
                true
            );


            setError(
                null
            );


            let fileToUpload =
                selectedFile;


            /*
             * If crop mode is active, create the final cropped
             * WebP before sending it to the backend.
             */
            if (
                cropMode
            ) {

                fileToUpload =
                    await createCroppedImage();

            }


            await uploadProductImage(
                product.id,
                fileToUpload
            );


            await onUploaded();

        } catch (exception) {

            console.error(
                exception
            );


            setError(
                exception instanceof Error
                    ? exception.message
                    : "Unable to upload image."
            );

        } finally {

            setUploading(
                false
            );

        }

    }


    /* ---------------------------------------------------------------------- */
    /* Render                                                                 */
    /* ---------------------------------------------------------------------- */

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-slate-950/60 p-3 sm:p-4">

            <div className="my-4 w-full max-w-xl overflow-hidden rounded-2xl bg-white shadow-2xl sm:my-8">

                {/* Header */}

                <div className="flex items-center justify-between border-b border-slate-200 px-4 py-4 sm:px-5">

                    <div className="min-w-0">

                        <h2 className="truncate font-semibold text-slate-900">
                            {
                                product.imageUrl
                                    ? "Change Product Image"
                                    : "Upload Product Image"
                            }
                        </h2>


                        <p className="mt-0.5 truncate text-xs text-slate-500">
                            {
                                product.name
                            }
                        </p>

                    </div>


                    <button
                        type="button"
                        onClick={
                            onClose
                        }
                        disabled={
                            uploading
                        }
                        aria-label="Close"
                        className="
                            ml-3
                            shrink-0
                            rounded-lg
                            p-2
                            text-slate-400
                            transition

                            hover:bg-slate-100
                            hover:text-slate-700

                            disabled:opacity-50
                        "
                    >
                        ✕
                    </button>

                </div>


                {/* Body */}

                <div className="p-4 sm:p-5">

                    {/* Preview */}

                    <div
                        className="
                            relative
                            mx-auto
                            aspect-square
                            w-full
                            max-w-md
                            overflow-hidden
                            rounded-xl
                            bg-slate-100
                        "
                    >

                        {
                            previewUrl
                                ? (

                                    <Image
                                        src={
                                            previewUrl
                                        }
                                        alt={
                                            product.name
                                        }
                                        fill
                                        sizes="(max-width: 640px) calc(100vw - 32px), 448px"
                                        className="object-cover"
                                        unoptimized={
                                            previewUrl.startsWith(
                                                "blob:"
                                            )
                                        }
                                        onLoad={
                                            handleImageLoad
                                        }
                                    />

                                )
                                : (

                                    <div className="flex h-full items-center justify-center text-sm text-slate-400">
                                        No image selected
                                    </div>

                                )
                        }


                        {
                            cropMode
                            && previewUrl
                            && (

                                <div className="pointer-events-none absolute inset-0">

                                    <div className="absolute inset-0 border-2 border-white/80 shadow-[inset_0_0_0_9999px_rgba(0,0,0,0.25)]" />

                                    <div className="absolute left-1/2 top-1/2 h-[80%] w-[80%] -translate-x-1/2 -translate-y-1/2 border-2 border-white" />

                                </div>

                            )
                        }

                    </div>


                    {/* Image information */}

                    {
                        selectedFile
                        && (

                            <div className="mt-3 text-center text-xs text-slate-500">

                                {
                                    selectedFile.name
                                }

                                {
                                    imageNaturalWidth
                                    >
                                    0
                                    &&
                                    imageNaturalHeight
                                    >
                                    0
                                    && (

                                        <>
                                            {" • "}
                                            {
                                                imageNaturalWidth
                                            }
                                            ×
                                            {
                                                imageNaturalHeight
                                            }
                                            px
                                        </>

                                    )
                                }

                            </div>

                        )
                    }


                    {/* Crop controls */}

                    {
                        selectedFile
                        && (

                            <div className="mt-4 rounded-xl border border-slate-200 bg-slate-50 p-3 sm:p-4">

                                <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">

                                    <div>

                                        <p className="text-sm font-semibold text-slate-800">
                                            Crop image
                                        </p>


                                        <p className="mt-0.5 text-xs text-slate-500">
                                            Use a square crop for a
                                            consistent menu appearance.
                                        </p>

                                    </div>


                                    <button
                                        type="button"
                                        onClick={
                                            () => {

                                                setCropMode(
                                                    value =>
                                                        !value
                                                );

                                                resetCrop();

                                            }
                                        }
                                        disabled={
                                            uploading
                                        }
                                        className="
                                            rounded-lg
                                            border
                                            border-slate-200
                                            bg-white
                                            px-3
                                            py-2
                                            text-xs
                                            font-semibold
                                            text-slate-700
                                            transition

                                            hover:bg-slate-100
                                        "
                                    >
                                        {
                                            cropMode
                                                ? "Hide Crop"
                                                : "Enable Crop"
                                        }
                                    </button>

                                </div>


                                {
                                    cropMode
                                    && (

                                        <div className="mt-4 space-y-4">

                                            <div>

                                                <div className="mb-1.5 flex items-center justify-between">

                                                    <label
                                                        htmlFor="crop-zoom"
                                                        className="text-xs font-semibold text-slate-700"
                                                    >
                                                        Zoom
                                                    </label>


                                                    <span className="text-xs text-slate-500">
                                                        {
                                                            cropZoom.toFixed(
                                                                1
                                                            )
                                                        }×
                                                    </span>

                                                </div>


                                                <input
                                                    id="crop-zoom"
                                                    type="range"
                                                    min="1"
                                                    max="3"
                                                    step="0.1"
                                                    value={
                                                        cropZoom
                                                    }
                                                    onChange={
                                                        event =>
                                                            setCropZoom(
                                                                Number(
                                                                    event.target.value
                                                                )
                                                            )
                                                    }
                                                    className="w-full"
                                                />

                                            </div>


                                            <div>

                                                <p className="mb-2 text-xs font-semibold text-slate-700">
                                                    Position
                                                </p>


                                                <div className="grid grid-cols-3 gap-2">

                                                    <div />


                                                    <button
                                                        type="button"
                                                        onClick={
                                                            () =>
                                                                setCropY(
                                                                    value =>
                                                                        Math.max(
                                                                            -1,
                                                                            value
                                                                            -
                                                                            0.1
                                                                        )
                                                                )
                                                        }
                                                        className="rounded-lg border border-slate-200 bg-white py-2 text-sm transition hover:bg-slate-100"
                                                    >
                                                        ↑
                                                    </button>


                                                    <div />


                                                    <button
                                                        type="button"
                                                        onClick={
                                                            () =>
                                                                setCropX(
                                                                    value =>
                                                                        Math.max(
                                                                            -1,
                                                                            value
                                                                            -
                                                                            0.1
                                                                        )
                                                                )
                                                        }
                                                        className="rounded-lg border border-slate-200 bg-white py-2 text-sm transition hover:bg-slate-100"
                                                    >
                                                        ←
                                                    </button>


                                                    <button
                                                        type="button"
                                                        onClick={
                                                            resetCrop
                                                        }
                                                        className="rounded-lg border border-slate-200 bg-white py-2 text-xs font-semibold text-slate-600 transition hover:bg-slate-100"
                                                    >
                                                        Reset
                                                    </button>


                                                    <button
                                                        type="button"
                                                        onClick={
                                                            () =>
                                                                setCropX(
                                                                    value =>
                                                                        Math.min(
                                                                            1,
                                                                            value
                                                                            +
                                                                            0.1
                                                                        )
                                                                )
                                                        }
                                                        className="rounded-lg border border-slate-200 bg-white py-2 text-sm transition hover:bg-slate-100"
                                                    >
                                                        →
                                                    </button>


                                                    <div />


                                                    <button
                                                        type="button"
                                                        onClick={
                                                            () =>
                                                                setCropY(
                                                                    value =>
                                                                        Math.min(
                                                                            1,
                                                                            value
                                                                            +
                                                                            0.1
                                                                        )
                                                                )
                                                        }
                                                        className="rounded-lg border border-slate-200 bg-white py-2 text-sm transition hover:bg-slate-100"
                                                    >
                                                        ↓
                                                    </button>


                                                    <div />

                                                </div>

                                            </div>

                                        </div>

                                    )
                                }

                            </div>

                        )
                    }


                    {/* File picker */}

                    <label className="mt-4 block cursor-pointer rounded-xl border-2 border-dashed border-slate-200 bg-slate-50 p-4 text-center transition hover:border-slate-400 hover:bg-white sm:p-5">

                        <input
                            type="file"
                            accept="image/jpeg,image/png,image/webp"
                            onChange={
                                handleFileChange
                            }
                            disabled={
                                uploading
                            }
                            className="sr-only"
                        />


                        <div className="text-sm font-semibold text-slate-700">
                            Choose image
                        </div>


                        <div className="mt-1 text-xs text-slate-500">
                            JPG, PNG or WebP · Maximum 5 MB
                        </div>


                        {
                            selectedFile
                            && (

                                <div className="mt-3 truncate text-xs font-medium text-slate-700">

                                    Selected:{" "}

                                    {
                                        selectedFile.name
                                    }

                                </div>

                            )
                        }

                    </label>


                    {/* Error */}

                    {
                        error
                        && (

                            <div
                                role="alert"
                                className="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
                            >
                                {
                                    error
                                }
                            </div>

                        )
                    }

                </div>


                {/* Footer */}

                <div className="flex flex-col-reverse gap-2 border-t border-slate-200 bg-slate-50 px-4 py-4 sm:flex-row sm:justify-end sm:gap-3 sm:px-5">

                    <button
                        type="button"
                        onClick={
                            onClose
                        }
                        disabled={
                            uploading
                        }
                        className="
                            w-full
                            rounded-xl
                            border
                            border-slate-200
                            bg-white
                            px-4
                            py-2.5
                            text-sm
                            font-semibold
                            text-slate-700
                            transition

                            hover:bg-slate-50

                            disabled:opacity-50

                            sm:w-auto
                        "
                    >
                        Cancel
                    </button>


                    <button
                        type="button"
                        onClick={
                            handleUpload
                        }
                        disabled={
                            uploading
                            ||
                            !selectedFile
                        }
                        className="
                            w-full
                            rounded-xl
                            bg-slate-900
                            px-5
                            py-2.5
                            text-sm
                            font-semibold
                            text-white
                            transition

                            hover:bg-slate-800

                            disabled:cursor-not-allowed
                            disabled:opacity-50

                            sm:w-auto
                        "
                    >
                        {
                            uploading
                                ? "Uploading..."
                                : product.imageUrl
                                  ? "Replace Image"
                                  : "Upload Image"
                        }
                    </button>

                </div>

            </div>

        </div>
    );
}