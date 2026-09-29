# SCRUM-35 occasion food journey

Owner: Gokul Sweets. `GOKUL_FEATURES_OCCASION_ENQUIRIES=false` is the safe default. The backend exposes the occasion route only when verified customer identity, environment isolation and strict environment CORS are configured. Keep existing inventory enforcement and the authoritative pickup commitment enabled for online checkout. Flag OFF leaves the existing menu, cart and checkout path available.

Two distinct customer choices:

1. **Reviewed custom request.** A verified customer submits a branch, occasion, IST service date, guest count, fulfilment request, products and quantities. Staff can decline or issue a dated quote. The request and quote do not hold stock or a pickup slot, and cannot be paid or displayed as a confirmed order. An expired quote cannot be accepted. Delivery is only an enquiry; it is not a delivery promise.
2. **Online pickup.** The occasion items enter the existing branch cart. The requested date carries to pickup selection, where the customer chooses a real available slot. Existing order creation reserves slot and inventory, then the provider payment flow owns paid and confirmed states. The current server price is authoritative. Online pickup requires full payment; it is not an acceptance of any custom manager quote.

Enquiry submission is limited to three requests per verified subject per 24 hours, serialized by a transaction-scoped PostgreSQL advisory lock. An identical request in the previous 15 minutes returns the original enquiry without consuming the limit. Staff permissions and branch access protect the queue. The customer's session is scoped to the originating environment and subject.

## Remaining before story QA

Custom quote acceptance requires a price and tax model that can be reconciled with the authoritative order total, an actual capacity/stock hold, a provider verified payment and a confirmed order link. Do not enable the custom quote journey as a payable booking or move the story to QA before that work and DEV flag ON/OFF smoke are complete. A venue or banquet deposit belongs to SCRUM-45 and must not be inferred from these food enquiries.
