import type {
    MenuCategory,
    MenuProduct,
    ProductSaleMode
} from "@/types/menu";


function product(
    id: number,
    name: string,
    description: string,
    price: number,
    categoryId: number,
    categoryName: string,
    saleMode: ProductSaleMode
): MenuProduct {
    return {
        id,
        name,
        description,
        price,
        imageUrl: null,
        available: true,
        categoryId,
        categoryName,
        saleMode,
        minimumWeightGrams: saleMode === "WEIGHT" ? 250 : null,
        weightStepGrams: saleMode === "WEIGHT" ? 50 : null
    };
}


const MAIN_BRANCH_MENU: MenuCategory[] = [
    {
        id: 1,
        name: "Sweets",
        description: null,
        displayOrder: 1,
        products: [
            product(1, "Gulab Jamun", "Soft and delicious traditional gulab jamun.", 240, 1, "Sweets", "WEIGHT"),
            product(2, "Kaju Katli", "Premium cashew-based Indian sweet.", 900, 1, "Sweets", "WEIGHT"),
            product(3, "Motichoor Laddu", "Traditional motichoor laddu.", 480, 1, "Sweets", "WEIGHT"),
            product(4, "Besan Laddu", "Traditional besan laddu made with gram flour.", 420, 1, "Sweets", "WEIGHT")
        ]
    },
    {
        id: 2,
        name: "Namkeen",
        description: null,
        displayOrder: 2,
        products: [
            product(5, "Aloo Bhujia", "Crispy spicy potato-based namkeen.", 320, 2, "Namkeen", "UNIT"),
            product(6, "Khatta Meetha", "Sweet and spicy traditional namkeen mix.", 300, 2, "Namkeen", "UNIT")
        ]
    },
    {
        id: 3,
        name: "Bengali Sweets",
        description: null,
        displayOrder: 3,
        products: [
            product(7, "Rasgulla", "Soft Bengali-style rasgulla.", 280, 3, "Bengali Sweets", "WEIGHT"),
            product(8, "Sandesh", "Traditional Bengali milk-based sweet.", 450, 3, "Bengali Sweets", "WEIGHT")
        ]
    },
    {
        id: 4,
        name: "Cakes",
        description: null,
        displayOrder: 4,
        products: [
            product(9, "Black Forest Cake", "Classic chocolate cake with cream.", 650, 4, "Cakes", "UNIT"),
            product(10, "Pineapple Cake", "Fresh pineapple cream cake.", 600, 4, "Cakes", "UNIT")
        ]
    }
];


const CITY_BRANCH_MENU: MenuCategory[] =
    structuredClone(MAIN_BRANCH_MENU);


export function getMockMenu(branchId: number): MenuCategory[] {
    return structuredClone(
        branchId === 2
            ? CITY_BRANCH_MENU
            : MAIN_BRANCH_MENU
    );
}