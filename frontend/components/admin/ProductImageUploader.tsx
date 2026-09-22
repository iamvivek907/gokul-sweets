"use client";

import {
    useEffect,
    useState
} from "react";

import Image from "next/image";

import {
    uploadProductImage
} from "@/services/productApi";


interface ProductImageUploaderProps {

    productId: number;

    currentImageUrl: string | null;

    onUploaded?: (
        imageUrl: string | null
    ) => void;
}


const MAX_FILE_SIZE =
    5 * 1024 * 1024;


const ALLOWED_TYPES = [
    "image/jpeg",
    "image/png",
    "image/webp"
];


export default function ProductImageUploader({
    productId,
    currentImageUrl,
    onUploaded
}: ProductImageUploaderProps) {

    const [
        selectedFile,
        setSelectedFile
    ] = useState<File | null>(null);


    const [
        previewUrl,
        setPreviewUrl
    ] = useState<string | null>(
        currentImageUrl
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


    /*
     * Revoke temporary blob URLs when they
     * are replaced or when the component unmounts.
     *
     * This effect does NOT call setState().
     */
    useEffect(() => {

        return () => {

            if (
                previewUrl?.startsWith(
                    "blob:"
                )
            ) {

                URL.revokeObjectURL(
                    previewUrl
                );
            }
        };

    }, [
        previewUrl
    ]);


    function handleFileChange(
        event: React.ChangeEvent<HTMLInputElement>
    ) {

        const file =
            event.target.files?.[0]
            ?? null;


        setError(null);


        /*
         * User cancelled the file picker.
         */
        if (!file) {

            setSelectedFile(null);

            setPreviewUrl(
                currentImageUrl
            );

            return;
        }


        /*
         * Validate file type.
         */
        if (
            !ALLOWED_TYPES.includes(
                file.type
            )
        ) {

            setError(
                "Only JPG, PNG and WebP images are allowed."
            );

            setSelectedFile(null);

            setPreviewUrl(
                currentImageUrl
            );

            return;
        }


        /*
         * Validate file size.
         */
        if (
            file.size > MAX_FILE_SIZE
        ) {

            setError(
                "Image must be 5 MB or smaller."
            );

            setSelectedFile(null);

            setPreviewUrl(
                currentImageUrl
            );

            return;
        }


        /*
         * Create a temporary local preview.
         */
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
    }


    async function uploadImage() {

        if (!selectedFile) {

            setError(
                "Please select an image."
            );

            return;
        }


        setUploading(true);

        setError(null);


        try {

            /*
             * Upload exactly once.
             *
             * uploadProductImage() creates the
             * multipart FormData and calls the backend.
             */
            const product =
                await uploadProductImage(
                    productId,
                    selectedFile
                );


            const imageUrl =
                product.imageUrl ?? null;


            /*
             * Replace the local blob preview
             * with the permanent R2 URL.
             */
            setPreviewUrl(
                imageUrl
            );


            setSelectedFile(
                null
            );


            onUploaded?.(
                imageUrl
            );

        } catch (exception) {

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


    return (
        <div className="space-y-4">

            <div
                className="
                    relative
                    h-48
                    overflow-hidden
                    rounded-2xl
                    border
                    border-[#eadfd6]
                    bg-[#fff7ef]
                "
            >

                {previewUrl ? (

                    <Image
                        src={previewUrl}
                        alt="Product preview"
                        fill
                        sizes="
                            (max-width: 768px) 100vw,
                            600px
                        "
                        className="
                            object-cover
                        "
                        unoptimized={
                            previewUrl.startsWith(
                                "blob:"
                            )
                        }
                    />

                ) : (

                    <div
                        className="
                            flex
                            h-full
                            items-center
                            justify-center
                            text-5xl
                        "
                    >
                        🍬
                    </div>
                )}

            </div>


            <input
                type="file"
                accept="
                    image/jpeg,
                    image/png,
                    image/webp
                "
                onChange={
                    handleFileChange
                }
                disabled={
                    uploading
                }
                className="
                    block
                    w-full
                    text-sm
                "
            />


            {error && (

                <p
                    className="
                        text-sm
                        font-medium
                        text-red-600
                    "
                >
                    {error}
                </p>
            )}


            <button
                type="button"
                onClick={
                    uploadImage
                }
                disabled={
                    uploading
                    || !selectedFile
                }
                className="
                    min-h-11
                    rounded-xl
                    bg-[#7a1625]
                    px-5
                    text-sm
                    font-bold
                    text-white
                    transition
                    hover:bg-[#5d0f1b]
                    disabled:cursor-not-allowed
                    disabled:opacity-50
                "
            >
                {uploading
                    ? "Uploading..."
                    : "Upload Image"}
            </button>

        </div>
    );
}