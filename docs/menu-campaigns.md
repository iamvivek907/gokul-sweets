# Published menu promotions

Configure the promotion in **Admin → Menu & inventory workspace → Menu appearance → Edit banner**. Change its artwork, English/Hindi title and subtitle, button label/destination, framing, order, visibility and IST schedule. Save draft, then Review & publish. Homepage campaigns remain a separate placement.

The phone menu displays published banners below Offers & savings and before product rows across Food, Sweets, Bakery and Snacks. The built-in snack/sweet promotion is removed. Desktop keeps its existing banner and category-image layout. Category buttons jump to the actual category without changing the cart or pickup.

One uncached appearance request per branch/menu mount serves both placements and category ordering. A new menu visit or reload reads the latest publication. The backend supplies currently visible/scheduled banners. No active banners means no promotion; a failed read leaves ordering available without a replacement banner. Changing branches immediately discards the previous banner and category ordering, including when returning to the original branch. Failed or invalid reads leave that state empty; aborted reads cannot display another branch's content. Videos retain their static fallback for reduced motion.

## Dev acceptance

1. Deploy this PR to dev and publish a menu banner with the existing admin editor.
2. Open `/menu` at 320, 390, 640 and desktop widths. Confirm one promotion, readable text, no horizontal overflow and a working category action. Inspect all four phone menu families and Hindi.
3. Edit the artwork/copy in admin, publish, then reload the menu: the strip changes without another frontend deployment. Hide/remove/expire the banner and verify the strip disappears.
4. Load branch A’s banner, switch to B, then return to A with its appearance request pending and failing: A’s old banner must stay hidden and ordering remain usable. Repeat with a delayed response from the abandoned branch; it must not replace the current campaign.

Browser evidence: [phone](qa/pr224/diwali-menu-390.jpg) · [desktop](qa/pr224/diwali-menu-1280.jpg). These previews use mocked catalogue/availability data and generated Diwali artwork; they are not production deployment evidence.
