export interface PickupSlot {
    id: number;
    branchId: number;

    slotDate: string;

    startTime: string;
    endTime: string;

    capacity: number;
    bookedCount: number;
    remainingCapacity: number;

    active: boolean;

    priorityEnabled: boolean;
    priorityCapacity: number;
    priorityBookedCount: number;
    priorityRemainingCapacity: number;

    priorityCharge: number;
}


export type PickupType =
    | "NORMAL"
    | "PRIORITY";


export interface PickupSelection {
    date: string;
    slot: PickupSlot;
    pickupType: PickupType;
}