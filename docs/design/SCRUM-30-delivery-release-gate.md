# SCRUM-30 delivery release gate

Owner manually deploys the merged `dev` commit to DEV after Sprint 3. Record the exact SHA, DEV URL, build and API evidence in SCRUM-30 and QA subtask SCRUM-67. This PR contains code and automated checks; it does not deploy or switch on customer delivery.

## Flag OFF

- Keep `GOKUL_FEATURES_DELIVERY_CHECKOUT=false` and all other delivery flags at their documented defaults. Verify pickup checkout, payments, KOT and pickup status flow still work, and delivery cannot create a customer order.
- Check an existing paid pickup order in staff and customer screens. Verify pickup expiry/no-show remains pickup-only.

## Controlled flag ON in DEV

- Configure a reviewed delivery boundary and an active branch/zone, permitted products, stock allocation and a rider window. Turn on the documented prerequisite flags in the DEV environment only. Test exact boundary, wrong locality/PIN, paused zone/window, changed address or cart, concurrent last-capacity requests and an IST midnight boundary with the server in a non-IST timezone.
- With a real payment test provider, verify one successful and one failed/expired delivery payment. A success commits rider and inventory holds once; a failure or timeout releases both once. Test late provider success against expired/cancelled orders and verify reconciliation without reviving the order.
- With branch staff, process `CONFIRMED → PREPARING → READY_FOR_DELIVERY → OUT_FOR_DELIVERY → DELIVERED`. Check branch isolation and the distinct dispatch/confirmation permissions; try to skip a step and try pickup-only statuses on delivery. Confirm customer status and completed purchase metrics, review eligibility, and inventory fulfillment once. Confirm paid cancellation requires the refund workflow; dispatched orders cannot use unpaid cancellation or late pickup collection.
- Claim a delivery KOT with the **actual external printer agent**. Confirm the paper ticket displays the delivery date and IST window, handles null pickup fields, prints the right branch and order, and reports success or failure correctly. This repository checks JSON shape but does not contain the external agent, so device behavior remains unverified until this smoke test.
- Verify narrow/mobile customer and staff views, manual location fallback and denied device permission, keyboard use, loading and retry recovery. Save screenshots and API responses with private customer details removed.

Keep the customer checkout flag OFF until the DEV evidence above and SCRUM-67 review pass. Roll back by turning the flag OFF; preserve existing paid orders and reconcile their rider/inventory reservations rather than deleting them.
