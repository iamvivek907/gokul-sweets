export type PrintAgentHealthStatus =
    | "ONLINE"
    | "STALE"
    | "OFFLINE";


export type PrinterHealthStatus =
    | "READY"
    | "DEGRADED"
    | "OFFLINE"
    | "NOT_CONFIGURED"
    | "UNKNOWN";


export type PrinterStation =
    | "KITCHEN"
    | "SWEETS"
    | "BEVERAGE"
    | "FAST_FOOD"
    | "BILLING";


export interface AdminPrintingHealth {

    branchId: number;

    station: PrinterStation;

    agent: {

        agentId: string | null;

        status: PrintAgentHealthStatus;

        firstSeenAt: string | null;

        lastSeenAt: string | null;

        heartbeatCount: number;

        secondsSinceHeartbeat: number | null;
    };

    printer: {

        printerId: number | null;

        code: string | null;

        name: string | null;

        protocol: string | null;

        host: string | null;

        port: number | null;

        active: boolean;

        status: PrinterHealthStatus;
    };

    queue: {

        queued: number;

        claimed: number;

        failed: number;

        permanentlyFailed: number;
    };

    activity: {

        lastSuccessfulPrintAt: string | null;

        lastSuccessfulPrintJobId: number | null;

        lastFailureAt: string | null;

        lastFailedPrintJobId: number | null;

        lastFailureCode: string | null;

        lastFailureMessage: string | null;
    };
}
