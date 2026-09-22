export interface AdminStaff {

    id: number;

    username: string;

    fullName: string;

    phone: string | null;

    active: boolean;

    roleName: string;

    branchIds: number[];
}


export interface AdminStaffRoleOption {

    name: string;

    description: string | null;

    permissions: string[];
}


export interface AdminStaffBranchOption {

    id: number;

    code: string;

    name: string;

    active: boolean;
}


export interface AdminStaffManagementOptions {

    roles: AdminStaffRoleOption[];

    branches: AdminStaffBranchOption[];
}


export interface CreateAdminStaffRequest {

    username: string;

    password: string;

    fullName: string;

    phone: string | null;

    roleName: string;

    branchIds: number[];
}


export interface UpdateAdminStaffRequest {

    fullName: string;

    phone: string | null;

    roleName: string;

    branchIds: number[];

    active: boolean;
}
