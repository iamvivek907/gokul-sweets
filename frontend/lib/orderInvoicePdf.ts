import PDFDocument from "pdfkit";

type Script = "latin" | "devanagari" | "tamil";
export type InvoiceFonts = Record<Script, Uint8Array>;
let fontsRequest: Promise<InvoiceFonts> | undefined;

function loadFonts(): Promise<InvoiceFonts> {
    if (!fontsRequest) {
        fontsRequest = Promise.all((["latin", "devanagari", "tamil"] as const).map(async script => {
            const response = await fetch(`/fonts/invoice-v1/${script}.woff`, {signal: AbortSignal.timeout(15_000)});
            if (!response.ok) throw new Error("Invoice font unavailable");
            return [script, new Uint8Array(await response.arrayBuffer())] as const;
        })).then(entries => Object.fromEntries(entries) as InvoiceFonts).catch(error => {
            fontsRequest = undefined;
            throw error;
        });
    }
    return fontsRequest;
}

function scriptFor(character: string): Script {
    if (character === "₹" || /[\u0900-\u097f\ua8e0-\ua8ff]/u.test(character)) return "devanagari";
    if (/[\u0b80-\u0bff]/u.test(character)) return "tamil";
    return "latin";
}

type Invoice = {
    orderNumber: string;
    placed: string;
    branch: string[];
    customer: string[];
    items: {name: string; quantity: string; rate: string; amount: string; note?: string}[];
    charges: {label: string; amount: string; note?: string}[];
    paidTotal: string;
};

/** Generate a tagged PDF with embedded fonts and selectable multilingual text. */
export async function createInvoicePdf(invoice: Invoice, fonts?: InvoiceFonts): Promise<Blob> {
    const embedded = fonts ?? await loadFonts();
    // Suppress the built-in default font: every displayed font is embedded.
    const doc = new PDFDocument({size: "A4", margin: 42, font: "", autoFirstPage: false,
        pdfVersion: "1.7", tagged: true, lang: "en-IN", displayTitle: true,
        info: {Title: `Gokul Sweets invoice ${invoice.orderNumber}`, Author: "Gokul Sweets"}});
    for (const script of ["latin", "devanagari", "tamil"] as const) doc.registerFont(script, embedded[script]);
    doc.on("pageAdded", () => {
        doc.markContent("Artifact");
        doc.save().rect(0, 0, doc.page.width, 6).fill("#841638");
        doc.restore(); doc.endMarkedContent();
    });
    doc.font("latin"); doc.addPage();
    const chunks: Uint8Array<ArrayBuffer>[] = [];
    const output = new Promise<Blob>((resolve, reject) => {
        doc.on("data", chunk => chunks.push(new Uint8Array(chunk)));
        doc.on("end", () => resolve(new Blob(chunks, {type: "application/pdf"})));
        doc.on("error", reject);
    });
    const root = doc.struct("Document"); doc.addStructure(root);
    const wine = "#841638", ink = "#241f23", muted = "#635962", rule = "#e7dfe3";
    const left = 42, right = doc.page.width - left, bottom = doc.page.height - left;
    const width = right - left;
    let y = 48;

    function runs(text: string) {
        const result: {script: Script; text: string}[] = [];
        for (const character of text) {
            const last = result.at(-1);
            const script = /[\s\u200c\u200d]/u.test(character) && last ? last.script : scriptFor(character);
            if (last?.script === script) last.text += character;
            else result.push({script, text: character});
        }
        return result;
    }

    function measure(text: string, size: number) {
        return runs(text).reduce((sum, run) => sum + doc.font(run.script).fontSize(size).widthOfString(run.text), 0);
    }

    function wrap(text: string, maxWidth: number, size: number) {
        const lines: string[] = [];
        for (const paragraph of text.split(/\r?\n/u)) {
            let line = "";
            for (const word of paragraph.trim().split(/\s+/u)) {
                const next = line ? line + " " + word : word;
                if (measure(next, size) <= maxWidth) { line = next; continue; }
                if (line) lines.push(line);
                line = "";
                for (const {segment} of new Intl.Segmenter("en", {granularity: "grapheme"}).segment(word)) {
                    if (line && measure(line + segment, size) > maxWidth) { lines.push(line); line = ""; }
                    line += segment;
                }
            }
            lines.push(line);
        }
        return lines;
    }

    function text(lines: string[], x: number, top: number, maxWidth: number, size: number,
        color = ink, alignRight = false, tag = "P", parent = root) {
        if (!lines.some(Boolean)) return;
        const element = doc.struct(tag); parent.add(element);
        lines.forEach((line, i) => {
            if (!line) return;
            let cursor = alignRight ? x + maxWidth - measure(line, size) : x;
            // Keep logical text on the structure element. ActualText on the content
            // stream collapses glyph coordinates in geometry-based PDF extractors.
            const span = doc.struct("Span", {actual: line}); element.add(span);
            for (const run of runs(line)) {
                span.add(doc.markStructureContent("Span", {
                    lang: run.script === "devanagari" ? "hi-IN" : run.script === "tamil" ? "ta-IN" : "en-IN",
                }));
                const draw = () => doc.font(run.script).fontSize(size).fillColor(color)
                    .text(run.text, cursor, top + i * (size + 5), {lineBreak: false, baseline: "alphabetic"});
                if (run.script === "latin" || run.text === "₹") draw();
                else {
                    // PDFKit 0.20.2 flips the graphics state for each text object. Keep
                    // ActualText inside BT/ET so extractors see the same coordinates
                    // as the glyphs, while retaining the original script sequence.
                    const add = doc.addContent;
                    doc.addContent = function (content: string) {
                        if (content === "ET") doc.endMarkedContent();
                        const result = add.call(doc, content);
                        if (content === "BT") doc.markContent("Span", {actual: run.text});
                        return result;
                    };
                    try { draw(); } finally { doc.addContent = add; }
                }
                doc.endMarkedContent();
                cursor += measure(run.text, size);
            }
            span.end();
        });
        element.end();
    }

    function artifact(draw: () => void) {
        doc.markContent("Artifact"); doc.save(); draw(); doc.restore(); doc.endMarkedContent();
    }

    function divider(at: number) {
        artifact(() => { doc.moveTo(left, at).lineTo(right, at).lineWidth(.65).stroke(rule); });
    }

    function newPage() {
        doc.addPage(); y = 52;
        text(["Gokul Sweets"], left, y, width / 2, 15, wine, false, "H2");
        text(["Order " + invoice.orderNumber + " · continued"], left, y, width, 10, muted, true);
        y += 28;
    }

    text(["Gokul Sweets"], left, 63, width, 29, wine, false, "H1");
    text(["ORDER INVOICE"], left, 52, width, 12, wine, true, "H2");
    text(["Order " + invoice.orderNumber], left, 72, width, 11, ink, true);
    text([invoice.placed], left, 90, width, 9.5, muted, true);
    divider(108);
    const half = (width - 28) / 2;
    const branch = wrap(invoice.branch.join("\n"), half, 10.5);
    const customer = wrap(invoice.customer.join("\n"), half, 10.5);
    text(["BRANCH"], left, 131, half, 9, wine, false, "H2");
    text(branch, left, 151, half, 10.5);
    text(["BILLED TO"], left + half + 28, 131, half, 9, wine, false, "H2");
    text(customer, left + half + 28, 151, half, 10.5);
    y = 151 + Math.max(branch.length, customer.length) * 15.5 + 10;

    const columns = [
        {x: left + 10, w: width - 255, title: "ITEM"},
        {x: right - 240, w: 64, title: "QTY / WT"},
        {x: right - 171, w: 78, title: "UNIT PRICE"},
        {x: right - 88, w: 78, title: "AMOUNT"},
    ];
    let table = root;
    function tableHeader() {
        table = doc.struct("Table"); root.add(table);
        const row = doc.struct("TR"); table.add(row);
        artifact(() => { doc.roundedRect(left, y, width, 27, 5).fill("#f5eff2"); });
        columns.forEach(({x, w, title}, i) => text([title], x, y + 17, w, 9, wine, i > 0, "TH", row));
        row.end(); y += 27;
    }
    tableHeader();
    for (const item of invoice.items) {
        const cells = [item.name, item.quantity, item.rate, item.amount]
            .map((value, i) => wrap(value, columns[i].w, 11));
        const note = item.note ? wrap(item.note, columns[0].w, 9) : [];
        const body = Math.max(...cells.map(lines => lines.length)) * 16;
        const height = Math.max(32, body + note.length * 14 + 12);
        if (y + height > bottom) { table.end(); newPage(); tableHeader(); }
        const row = doc.struct("TR"); table.add(row);
        cells.forEach((lines, i) => {
            const {x, w} = columns[i];
            if (i === 0 && note.length) {
                const cell = doc.struct("TD"); row.add(cell);
                text(lines, x, y + 17, w, 11, ink, false, "P", cell);
                text(note, x, y + 17 + lines.length * 16, w, 9, muted, false, "P", cell);
                cell.end();
            } else text(lines, x, y + 17, w, 11, ink, i > 0, "TD", row);
        });
        row.end();
        y += height; divider(y);
    }
    table.end();

    const summaryLeft = right - 255;
    const charges = invoice.charges.map(charge => ({
        ...charge, labelLines: wrap(charge.label, 158, 10.5),
        noteLines: charge.note ? wrap(charge.note, 158, 9) : [],
    }));
    const summaryHeight = 122 + charges.reduce((sum, row) => sum + row.labelLines.length * 15.5 + row.noteLines.length * 14 + 5, 0);
    if (y + summaryHeight > bottom) newPage();
    y += 22;
    for (const charge of charges) {
        text(charge.labelLines, summaryLeft, y, 158, 10.5, muted);
        text([charge.amount], right - 87, y, 77, 11, ink, true);
        y += charge.labelLines.length * 15.5;
        text(charge.noteLines, summaryLeft, y, 158, 9, muted);
        y += charge.noteLines.length * 14 + 5;
    }
    artifact(() => { doc.roundedRect(summaryLeft - 10, y, 265, 44, 6).fill(wine); });
    text(["TOTAL PAID"], summaryLeft, y + 27, 100, 10, "white", false, "H2");
    text([invoice.paidTotal], summaryLeft + 105, y + 28, 140, 19, "white", true);
    y += 67;
    text(["Payment confirmed"], left, y, width, 11, "#1a6047");
    divider(y + 13);
    text(["Thank you for ordering from Gokul Sweets."], left, y + 31, width, 10, muted);
    text(["This is a system-generated invoice."], left, y + 47, width, 9, muted);
    root.end(); doc.end();
    return output;
}
