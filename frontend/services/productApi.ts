import {
    apiClient
} from "@/services/apiClient";


export interface ProductResponse {

    id: number;

    categoryId: number;

    categoryName: string;

    taxCategoryId: number | null;

    taxCategoryName: string | null;

    name: string;

    description: string | null;

    basePrice: number;

    active: boolean;

    imageUrl: string | null;
}


export async function uploadProductImage(
    productId: number,
    image: File
): Promise<ProductResponse> {

    const formData =
        new FormData();


    formData.append(
        "image",
        image
    );


    return apiClient<ProductResponse>(
        `/api/products/${productId}/image`,
        {
            method: "POST",
            body: formData
        }
    );
}


export async function removeProductImage(
    productId: number
): Promise<ProductResponse> {

    return apiClient<ProductResponse>(
        `/api/products/${productId}/image`,
        {
            method: "DELETE"
        }
    );
}