# Daily inventory for a large menu

Open Admin → Menu & Inventory → Unified workspace. Select the branch and pickup date first. Search or filter, then use **Select all matching (up to 500)** to include items across pages. Clear selection before choosing a different set.

Click **Quick inventory setup**. The popup combines policy, allocation and (when needed) physical readiness.

- **Made to order · daily capacity:** enter how much the branch can reliably supply for pickup. No physical-ready step is needed for this selling policy. Service hours, production lead time and checkout validation still apply.
- **Prepared stock:** enter the online allocation and the quantity physically prepared now. Planned production is not ready stock. Only the sellable prepared balance is offered.

Enter kg for weight products and whole pieces for countable products. The default-fill controls keep kg and piece quantities separate. Review every quantity before saving.

Existing policies are preserved by default. To standardise selected items, explicitly enable **Apply this selling method to existing policies too**. This changes control mode, ready-stock requirement and online enablement while retaining buffers, maximum daily allocations, lead times, shelf life and booking horizons. Existing holds and paid commitments are not cleared. Paused daily-capacity allocations require review in the advanced stock editor before reopening.

**Repeat this allocation daily** is optional and creates a guaranteed-quantity automation rule for new routines. It does not replace an existing automation rule or mark future food physically ready. The normal schedule runs at 01:15 IST, subject to server configuration. An idle backend may miss the scheduled run. Use Inventory → Automation → Generate allocations to generate dates immediately; confirm the generation result before assuming future dates are open. Allocation generation does not overwrite manually managed dates or dates with stock activity.

For an existing repeating rule, manage quantities, weekdays and seasonal windows on the Automation page. For one-off stock changes, leave Repeat off. The server rejects repeating setup when scheduling is disabled.

Saving processes at most four items concurrently. Successes are retained and skipped on retry. Failed rows show their own reason; fix them and retry. Conflict errors require closing and reopening with fresh versions. Refresh restores text drafts and the saved/failed results in the same staff session. Changing branch closes the popup.

Use the **i** buttons beside inventory fields and columns for definitions and guidance. The advanced setup, production and automation pages remain available for exceptions.
