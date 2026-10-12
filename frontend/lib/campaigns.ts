import type {HomepageCampaign} from "../types/campaign";

export function visibleCampaigns(campaigns: HomepageCampaign[], now: number, branchId?: number) {
    return campaigns.filter(campaign => campaign.active && campaign.mediaUrl
        && (branchId === undefined || !campaign.branchId || campaign.branchId === branchId)
        && (!campaign.startAt || Date.parse(campaign.startAt) <= now)
        && (!campaign.endAt || now < Date.parse(campaign.endAt)))
        .sort((a, b) => a.displayOrder - b.displayOrder || a.id - b.id);
}

export function toIndiaDateTimeInput(value: string | null) {
    if (!value) return "";
    const parts = new Intl.DateTimeFormat("en-CA", {
        timeZone: "Asia/Kolkata", year: "numeric", month: "2-digit", day: "2-digit",
        hour: "2-digit", minute: "2-digit", hourCycle: "h23"
    }).formatToParts(new Date(value));
    const part = (type: Intl.DateTimeFormatPartTypes) => parts.find(value => value.type === type)?.value;
    return `${part("year")}-${part("month")}-${part("day")}T${part("hour")}:${part("minute")}`;
}

export function fromIndiaDateTimeInput(value: string) {
    return value ? new Date(`${value}:00+05:30`).toISOString() : null;
}
