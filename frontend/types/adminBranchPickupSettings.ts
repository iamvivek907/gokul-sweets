export interface AdminBranchPickupSettings {

    id: number | null;

    branchId: number;

    slotDurationMinutes: number;

    defaultCapacity: number;

    advanceBookingDays: number;

    openingTime: string;

    closingTime: string;

    enabled: boolean;

    priorityEnabled: boolean;

    defaultPriorityCapacity: number;

    defaultPriorityCharge: number;
}


export interface UpdateAdminBranchPickupSettingsRequest {

    slotDurationMinutes: number;

    defaultCapacity: number;

    advanceBookingDays: number;

    openingTime: string;

    closingTime: string;

    enabled: boolean;

    priorityEnabled: boolean;

    defaultPriorityCapacity: number;

    defaultPriorityCharge: number;
}
