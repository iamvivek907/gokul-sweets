export interface AdminProfile {
    staffId: number;

    username: string;

    fullName: string;

    phone: string | null;

    roleName: string;

    permissions: string[];

    branchIds: number[];
}
