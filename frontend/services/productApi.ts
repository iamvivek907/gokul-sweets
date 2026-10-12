import {adminFetch} from "@/services/adminApi";
async function imageRequest(path:string,init:RequestInit):Promise<ProductResponse>{const response=await adminFetch(path,"staff-session",init);if(!response.ok){let message="Unable to change product image. Check your permissions and retry.";try{message=(await response.json()).message||message;}catch{}throw new Error(message);}return response.json();}


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


    return imageRequest(
        `/api/admin/products/${productId}/image`,
        {
            method: "POST",
            body: formData
        }
    );
}


export async function removeProductImage(
    productId: number
): Promise<ProductResponse> {

    return imageRequest(
        `/api/admin/products/${productId}/image`,
        {
            method: "DELETE"
        }
    );
}