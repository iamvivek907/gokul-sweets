export interface PayrollOpeningBalance {

    id: number;

    staffUserId: number;

    asOfDate: string;

    earnedAmount: number;

    takenAmount: number;

    netOpeningBalance: number;

    note: string | null;

    createdByName: string;

    createdAt: string;
}


export interface SetPayrollOpeningBalancePayload {

    asOfDate: string;

    earnedAmount: number;

    takenAmount: number;

    note: string | null;
}
