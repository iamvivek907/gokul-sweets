# SCRUM-35 occasion food journey

Owner: Gokul Sweets. `GOKUL_FEATURES_OCCASION_ENQUIRIES=false` is the safe default. The backend exposes the occasion route only when verified customer identity, environment isolation and strict environment CORS are configured. Keep existing inventory enforcement and the authoritative pickup commitment enabled for online checkout. Flag OFF leaves the existing menu, cart and checkout path available.

Two distinct customer choices:

1. **Reviewed custom request.** A verified customer submits a branch, occasion, IST service date, guest count, fulfilment request, products and quantities. Staff can decline or issue a dated quote. The request and quote themselves do not hold stock or a pickup slot. When the separately gated payment journey is enabled, the customer chooses a live pickup slot and starts a ten-minute deposit checkout backed by a fifteen-minute inventory hold. A verified deposit commits the inventory; a verified balance (if any) confirms the pickup. Expired, failed or late payments never confirm it. Delivery remains an enquiry without online payment or a delivery promise.
2. **Online pickup.** The occasion items enter the existing branch cart. The requested date carries to pickup selection, where the customer chooses a real available slot. Existing order creation reserves slot and inventory, then the provider payment flow owns paid and confirmed states. The current server price is authoritative. Online pickup requires full payment; it is not an acceptance of any custom manager quote.

Enquiry submission is limited to three requests per verified subject per 24 hours, serialized by a transaction-scoped PostgreSQL advisory lock. An identical request in the previous 15 minutes returns the original enquiry without consuming the limit. Staff permissions and branch access protect the queue. The customer's session is scoped to the originating environment and subject.

## Payment gate and remaining before story QA

`GOKUL_FEATURES_OCCASION_PAYMENTS=false` independently gates custom quote checkout. Keep it OFF until a DEV PhonePe webhook and status-polling smoke, late success/refund review, pickup capacity release, inventory commitment and concurrent attempt checks have passed. A failed provider initiation leaves a single pending attempt for reconciliation; it must not create a second provider order. An expired deposit hold releases capacity. Verified late charges are marked `REFUND_PENDING` for branch finance review; refund execution is not automated. Do not turn the payment gate off while an in-flight payment has not been reconciled.

The separate occasion record does not yet create a standard order, KOT, tax invoice or per-item tax breakdown. A manager-entered amount is a gross quote rather than an authoritative computed checkout price. Finance must approve a tax and invoicing model and the operational order link before payment gate activation and before SCRUM-35 moves to QA. The existing online pickup route remains the production-ready full-payment alternative. A venue or banquet deposit belongs to SCRUM-45.
