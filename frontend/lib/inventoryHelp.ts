export type InventoryHelpContext =
    | "SETUP"
    | "PRODUCTION"
    | "AUTOMATION";

export interface InventoryHelpDefinition {
    key: string;
    label: string;
    context: InventoryHelpContext[];
    definition: string;
    guidance: string;
}

export const INVENTORY_HELP: InventoryHelpDefinition[] = [
    {
        key: "status",
        label: "Status",
        context: ["SETUP"],
        definition: "The current state of this product's allocation for the selected branch and pickup date.",
        guidance: "Configure the policy first, approve an allocation, and mark stock ready only when it can actually be supplied."
    },
    {
        key: "approved",
        label: "Approved allocation",
        context: ["SETUP"],
        definition: "The maximum quantity deliberately opened for online orders on the selected pickup date.",
        guidance: "Enter only the quantity the branch can safely supply. For weight products, enter kilograms in the admin screen."
    },
    {
        key: "ready",
        label: "Ready quantity",
        context: ["SETUP", "PRODUCTION"],
        definition: "Physical quantity currently prepared and available at the branch for this service date.",
        guidance: "Update it after production or physical verification. Do not enter planned production here."
    },
    {
        key: "held",
        label: "Held quantity",
        context: ["SETUP", "PRODUCTION"],
        definition: "Temporary inventory reserved by checkouts that have not completed payment yet.",
        guidance: "The system manages this value. Expired checkout holds are released automatically."
    },
    {
        key: "committed",
        label: "Committed quantity",
        context: ["SETUP", "PRODUCTION"],
        definition: "Inventory belonging to confirmed paid orders that must be fulfilled.",
        guidance: "The system manages this value. Never reduce stock below confirmed commitments."
    },
    {
        key: "available",
        label: "Available online",
        context: ["SETUP"],
        definition: "The quantity customers may still reserve after safety buffer, active holds, and confirmed commitments are deducted.",
        guidance: "This is calculated by the system; it is not entered manually."
    },
    {
        key: "attention",
        label: "Attention",
        context: ["SETUP"],
        definition: "An operational warning such as missing policy, missing allocation, delay, or unavailable stock.",
        guidance: "Resolve these warnings before enabling inventory enforcement for live ordering."
    },
    {
        key: "controlMode",
        label: "Control mode",
        context: ["SETUP"],
        definition: "How the product's online quantity is controlled: ready stock, daily production, or manual allocation.",
        guidance: "Choose Ready stock when customers may order only prepared stock. Choose Daily production for products made against a daily plan. Choose Manual for exceptional products you open date by date."
    },
    {
        key: "inventoryUnit",
        label: "Inventory unit",
        context: ["SETUP"],
        definition: "The base unit used by the backend to reserve and reconcile this product.",
        guidance: "Select Grams for sweets sold by weight and Pieces for products sold as countable units. The screen lets you enter weight in kilograms and converts it safely."
    },
    {
        key: "safetyBuffer",
        label: "Safety buffer",
        context: ["SETUP"],
        definition: "Quantity deliberately protected from online sale to absorb weighing variance, damage, or operational uncertainty.",
        guidance: "Enter stock that must remain protected. Example: with 10 kg approved and a 1 kg buffer, at most 9 kg is exposed online before holds and commitments."
    },
    {
        key: "dailyMaximum",
        label: "Daily production capacity",
        context: ["SETUP"],
        definition: "Hard product-level ceiling that may be allocated online for one service date.",
        guidance: "Enter the highest quantity this branch can reliably supply for one pickup date. Example: enter 10 kg if the branch must never accept more than 10 kg online that day."
    },
    {
        key: "bookingHorizon",
        label: "Future ordering window",
        context: ["SETUP", "AUTOMATION"],
        definition: "How many days in advance customers may book this product.",
        guidance: "Enter the furthest allowed advance booking. Example: 2 means customers may order today, tomorrow, and up to the permitted date boundary enforced by the backend."
    },
    {
        key: "productionLead",
        label: "Production lead",
        context: ["SETUP"],
        definition: "Minimum time needed between order placement and pickup readiness.",
        guidance: "Enter total preparation, cooling, packing, and normal delay in hours. Example: 10 hours means an order placed at 8:00 AM cannot use a pickup time before 6:00 PM."
    },
    {
        key: "shelfLife",
        label: "Shelf life",
        context: ["SETUP"],
        definition: "How long the product remains acceptable after preparation.",
        guidance: "Enter the actual safe usable life in days. Example: enter 2 when a batch may be supplied for no more than two days after preparation."
    },
    {
        key: "onlineEnabled",
        label: "Online ordering enabled",
        context: ["SETUP"],
        definition: "Controls whether this branch product may participate in online inventory and ordering.",
        guidance: "Turn it off for discontinued, seasonal, temporarily blocked, or counter-only products. Stock should not be opened online while it is off."
    },
    {
        key: "readyStockRequired",
        label: "Ready stock required",
        context: ["SETUP"],
        definition: "Requires physical ready quantity before inventory can be exposed online under ready-stock operation.",
        guidance: "Keep it enabled for already-made sweets and packaged goods. Disable it only for a properly planned made-to-order flow where approved production—not current ready stock—controls acceptance."
    },
    {
        key: "confirmedOutstanding",
        label: "Confirmed outstanding",
        context: ["PRODUCTION"],
        definition: "Confirmed demand not yet fulfilled for the selected date.",
        guidance: "This is the minimum customer obligation production must protect."
    },
    {
        key: "physicalOnHand",
        label: "Physical on hand",
        context: ["PRODUCTION"],
        definition: "System record of usable prepared stock currently at the branch.",
        guidance: "Correct it after a physical count when the recorded amount differs from reality."
    },
    {
        key: "mustPrepare",
        label: "Must prepare",
        context: ["PRODUCTION"],
        definition: "Minimum additional production required to cover confirmed demand and protected stock rules.",
        guidance: "Prioritize this quantity before forecast top-up production."
    },
    {
        key: "suggestedTotal",
        label: "Suggested total",
        context: ["PRODUCTION"],
        definition: "Planning suggestion combining confirmed requirements with safe forecast demand.",
        guidance: "It is advice, not automatically sellable stock. Record actual production separately."
    },
    {
        key: "wastage",
        label: "Wastage",
        context: ["PRODUCTION"],
        definition: "Prepared quantity removed because of expiry, damage, quality failure, or another recorded reason.",
        guidance: "Always record a specific reason; this data improves future buffers and forecasts."
    },
    {
        key: "automationMode",
        label: "Automation mode",
        context: ["AUTOMATION"],
        definition: "Determines whether automation only suggests, creates a draft, or approves a guaranteed allocation.",
        guidance: "Begin with Suggest only. Use auto-approval only for stable products with a proven guaranteed capacity."
    },
    {
        key: "guaranteed",
        label: "Guaranteed online quantity",
        context: ["AUTOMATION"],
        definition: "Quantity the branch promises it can supply online on every active selling date covered by the rule, before the safety buffer is deducted.",
        guidance: "This is the online sales quota, not stock withheld from sale. The separate safety buffer protects stock. Use a conservative promise, never an average or forecast."
    },
    {
        key: "forecastMaximum",
        label: "Maximum forecast suggestion",
        context: ["AUTOMATION"],
        definition: "Ceiling applied to forecast recommendations so unusual history cannot create an excessive suggestion.",
        guidance: "Keep it at or below the real product and branch production ceiling."
    },
    {
        key: "availabilityType",
        label: "Availability type",
        context: ["AUTOMATION"],
        definition: "Whether the product is normally scheduled, restricted to date windows, or managed manually.",
        guidance: "Festival-only sweets must use date-window availability; do not leave them on an always-active rule."
    },
    {
        key: "lookbackWeeks",
        label: "Lookback weeks",
        context: ["AUTOMATION"],
        definition: "How far back the forecast reads historical sales and fulfilled demand for this product and branch.",
        guidance: "Start with 8 weeks. Use a shorter period only when the product or selling pattern changed recently."
    },
    {
        key: "minimumComparableDays",
        label: "Minimum comparable days",
        context: ["AUTOMATION"],
        definition: "Minimum number of matching historical weekdays required before the system treats its forecast as usable.",
        guidance: "Use 3 initially. A higher value is safer but delays forecasting for new products."
    },
    {
        key: "demandMultiplier",
        label: "Demand multiplier",
        context: ["AUTOMATION"],
        definition: "A controlled adjustment applied to the historical demand estimate.",
        guidance: "Use 1.00 for no adjustment, 1.10 for a 10% increase, or 0.90 for a 10% reduction. Do not use it to represent guaranteed capacity."
    },
    {
        key: "generationHorizon",
        label: "Automatic planning window (days)",
        context: ["AUTOMATION"],
        definition: "Number of future service dates for which this rule may generate allocation suggestions or drafts.",
        guidance: "Leave blank to use the product booking horizon. Never extend it beyond dates the branch can plan reliably."
    },
    {
        key: "sellingDays",
        label: "Selling days",
        context: ["AUTOMATION"],
        definition: "Weekdays on which this automation rule is allowed to run for the product.",
        guidance: "Select only genuine production and selling days. Festival-only products should also use date-window availability."
    },
    {
        key: "festivalWindows",
        label: "Festival/date windows",
        context: ["AUTOMATION"],
        definition: "Explicit start and end dates during which a seasonal product may receive automated allocations.",
        guidance: "Add one window per festival or selling period. Keep the rule inactive outside these windows."
    },
    {
        key: "forecastEnabled",
        label: "Forecast enabled",
        context: ["AUTOMATION"],
        definition: "Allows historical demand to contribute a production suggestion above confirmed and guaranteed demand.",
        guidance: "Enable after enough comparable history exists. Forecast quantity remains a suggestion unless the selected automation mode explicitly approves guaranteed stock."
    },
    {
        key: "automationActive",
        label: "Automation active",
        context: ["AUTOMATION"],
        definition: "Master switch deciding whether this saved rule participates in scheduled or manual generation runs.",
        guidance: "Keep it off while reviewing a new rule. Turn it on only after quantities, selling days, availability type, and date windows are correct."
    }
];

export function inventoryHelpDefinition(
    key: string
): InventoryHelpDefinition | undefined {
    return INVENTORY_HELP.find(item => item.key === key);
}
