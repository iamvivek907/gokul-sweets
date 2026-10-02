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
    if (/[\u0900-\u097f\ua8e0-\ua8ff]/u.test(character)) return "devanagari";
    if (/[\u0b80-\u0bff]/u.test(character)) return "tamil";
    return "latin";
}

/** Embedded, shaped Unicode text and an ordered heading/paragraph tree, rather than page images. */
export async function createInvoicePdf(lines: string[], orderNumber: string, fonts?: InvoiceFonts): Promise<Blob> {
    const embedded = fonts ?? await loadFonts();
    // Suppress the built-in default font: every displayed font is embedded.
    const doc = new PDFDocument({size: "A4", margin: 48, font: "", autoFirstPage: false,
        pdfVersion: "1.7", tagged: true, lang: "en-IN", displayTitle: true,
        info: {Title: `Gokul Sweets invoice ${orderNumber}`, Author: "Gokul Sweets"}});
    for (const script of ["latin", "devanagari", "tamil"] as const) doc.registerFont(script, embedded[script]);
    doc.font("latin"); doc.addPage();
    const chunks: Uint8Array<ArrayBuffer>[] = [];
    const output = new Promise<Blob>((resolve, reject) => {
        doc.on("data", chunk => chunks.push(new Uint8Array(chunk)));
        doc.on("end", () => resolve(new Blob(chunks, {type: "application/pdf"})));
        doc.on("error", reject);
    });
    const root = doc.struct("Document"); doc.addStructure(root);
    // Bound each marked paragraph so its ActualText never repeats across a page break.
    const paragraphs = lines.flatMap(line => {
        const result: string[] = []; let current = "";
        for (const {segment} of new Intl.Segmenter("en", {granularity: "grapheme"}).segment(line)) {
            if (current.length + segment.length > 240) {result.push(current); current = "";}
            current += segment;
        }
        if (current) result.push(current);
        return result;
    });
    for (const [index, line] of paragraphs.entries()) {
        const type = line === "GOKUL SWEETS" ? "H1" : ["Order invoice", "Items", "Bill summary"].includes(line) ? "H2" : "P";
        const runs: {script: Script; text: string}[] = [];
        for (const character of `${line} `) {
            const script = /[\s\u200c\u200d]/u.test(character) && runs.length ? runs[runs.length - 1].script : scriptFor(character);
            if (runs.at(-1)?.script === script) runs[runs.length - 1].text += character;
            else runs.push({script, text: character});
        }
        const size = type === "H1" ? 22 : type === "H2" ? 15 : 11;
        doc.fontSize(size).fillColor("#173c39");
        let height = Math.max(...runs.map(run => doc.font(run.script).heightOfString(line, {lineGap: 3}))) + size * 3;
        const next = paragraphs[index + 1];
        // Keep headings with their first line, and item names with the following quantity/price.
        if (next && (type !== "P" || next.includes(" @ INR "))) {
            height += doc.font("latin").heightOfString(next, {lineGap: 3}) + size;
        }
        if (doc.y + height > doc.page.height - doc.page.margins.bottom) doc.addPage();
        const paragraph = doc.struct(type); root.add(paragraph);
        // Marked ActualText retains the original logical Unicode sequence for search/copy and readers.
        paragraph.add(doc.markStructureContent("Span", {actual: `${line} `}));
        runs.forEach((run, index) => {
            doc.markContent("Span", {lang: run.script === "devanagari" ? "hi-IN" : run.script === "tamil" ? "ta-IN" : "en-IN"});
            doc.font(run.script).text(run.text, {continued: index < runs.length - 1, lineGap: 3});
            doc.endMarkedContent();
        });
        doc.endMarkedContent();
        doc.moveDown(type === "P" ? .35 : .6);
        paragraph.end();
    }
    root.end(); doc.end();
    return output;
}
