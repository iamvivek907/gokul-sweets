export interface Branch {
    id: number;
    code: string;
    name: string;

    address: string | null;
    city: string | null;
    state: string | null;
    pincode: string | null;
    phone: string | null;
    fssaiLicenceNumber: string | null;

    latitude: number | null;
    longitude: number | null;

    openingTime: string | null;
    closingTime: string | null;

    active: boolean;
    operational?: boolean;
    coverImageUrl?: string | null;
    mobileCoverImageUrl?: string | null;
    coverAltText?: string | null;
    description?: string | null;
    pickupAvailable?: boolean;
}
