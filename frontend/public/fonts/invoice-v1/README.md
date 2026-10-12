# Invoice fonts

Noto Sans (Latin), Noto Sans Devanagari and Noto Sans Tamil, regular weight,
from the corresponding `@fontsource` packages at version 5.2.5. The adjacent
license files contain the SIL Open Font License 1.1 and copyright notices.

These WOFF files are loaded from this application's origin only when an invoice
is requested, then embedded and subset by PDFKit. They provide shaping and
Unicode mappings independently of fonts installed on the customer's device.
Keep the versioned directory immutable; use a new directory for font updates.
