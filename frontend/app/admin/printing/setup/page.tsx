"use client";

import Link from "next/link";
import {useState, type FormEvent} from "react";
import {useAdminAuth} from "@/contexts/AdminAuthContext";

export default function PrinterSetupPage() {
    const {hasAnyPermission} = useAdminAuth();
    const [protocol, setProtocol] = useState("ESC_POS_USB");
    const [message, setMessage] = useState("");

    function download(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        const data = new FormData(event.currentTarget);
        const origin = String(data.get("api_url")).trim();
        let url: URL;
        try {
            url = new URL(origin);
        } catch {
            setMessage("Enter the HTTPS backend address.");
            return;
        }
        if (url.protocol !== "https:" || url.username || url.password || url.search || url.hash || url.pathname !== "/") {
            setMessage("Use the HTTPS backend origin without a path or credentials.");
            return;
        }
        const profile = {
            api_url: url.origin,
            branch_id: Number(data.get("branch_id")),
            agent_id: String(data.get("agent_id")).trim(),
            station: String(data.get("station")),
            printer_code: String(data.get("printer_code")).trim(),
            protocol,
            target: String(data.get("target")).trim(),
            port: 9100,
            baud_rate: Number(data.get("baud_rate")),
            paper_width_mm: Number(data.get("paper_width_mm")),
            auto_cut: data.get("auto_cut") === "on"
        };
        if (!Number.isSafeInteger(profile.branch_id) || profile.branch_id < 1
            || !profile.agent_id || !profile.printer_code || !profile.target) {
            setMessage("Enter a valid branch ID, agent ID, printer code and device name.");
            return;
        }
        const blob = new Blob([JSON.stringify(profile, null, 2) + "\n"], {type: "application/json"});
        const href = URL.createObjectURL(blob);
        const anchor = document.createElement("a");
        anchor.href = href;
        anchor.download = "config.local.json";
        anchor.click();
        setTimeout(() => URL.revokeObjectURL(href), 1000);
        setMessage("Profile downloaded. This does not save a server printer or connect hardware. Follow the installation steps below.");
    }

    if (!hasAnyPermission(["ORDER_VIEW"])) {
        return <p className="p-6">You need permission to view orders to use printer setup.</p>;
    }

    const field = "mt-1 w-full rounded border border-stone-300 bg-white p-2";
    return <main className="mx-auto max-w-3xl space-y-6 p-5 text-stone-800">
        <Link href="/admin/printing" className="underline">Back to printer queue</Link>
        <h1 className="text-3xl font-bold">Printer setup</h1>
        <p>Use a Windows computer at the shop as the print station. Staff can use the webapp on Android or a computer; KOTs reach the connected printer through the server queue. Keep the print station running.</p>
        <p>Supports ESC/POS thermal printers: USB through a Windows RAW printer queue, or Bluetooth Classic/SPP through a paired outgoing COM port. BLE-only printers and Android-only automatic printing need a different bridge. Confirm the model supports ESC/POS before buying.</p>
        <form onSubmit={download} className="grid gap-4 rounded-xl border border-stone-200 bg-stone-50 p-5 sm:grid-cols-2">
            <label>Backend HTTPS address<input className={field} name="api_url" type="url" required placeholder="https://your-backend.example.com" /></label>
            <label>Branch ID<input className={field} name="branch_id" type="number" min="1" step="1" required /></label>
            <label>Agent ID<input className={field} name="agent_id" maxLength={120} required placeholder="shop-1-kitchen" /></label>
            <label>Station<select className={field} name="station" defaultValue="KITCHEN">{["KITCHEN", "SWEETS", "BEVERAGE", "FAST_FOOD", "BILLING"].map(value => <option key={value}>{value}</option>)}</select></label>
            <label>Printer code<input className={field} name="printer_code" maxLength={50} required placeholder="KITCHEN_USB" /></label>
            <label>Connection<select className={field} value={protocol} onChange={event => setProtocol(event.target.value)}><option value="ESC_POS_USB">USB (Windows printer queue)</option><option value="ESC_POS_BLUETOOTH">Bluetooth Classic (serial port)</option></select></label>
            <label>{protocol === "ESC_POS_USB" ? "Exact Windows printer name" : "Paired outgoing COM port"}<input className={field} name="target" maxLength={255} required placeholder={protocol === "ESC_POS_USB" ? "Name from agent devices command" : "COM5"} /></label>
            <label>Paper width<select className={field} name="paper_width_mm" defaultValue="80"><option value="58">58 mm</option><option value="80">80 mm</option></select></label>
            <label>Serial baud rate (Bluetooth only)<select className={field} name="baud_rate" defaultValue="9600">{[9600, 19200, 38400, 57600, 115200].map(value => <option key={value}>{value}</option>)}</select></label>
            <label className="self-center"><input name="auto_cut" type="checkbox" /> Enable cutter only if the printer has one</label>
            <button className="rounded bg-stone-800 px-4 py-3 font-semibold text-white sm:col-span-2" type="submit">Download printer profile</button>
            {message && <p role="status" className="sm:col-span-2">{message}</p>}
        </form>
        <section className="space-y-3">
            <h2 className="text-xl font-bold">When the printer arrives</h2>
            <ol className="list-decimal space-y-2 pl-6">
                <li>Install Python 3.12+ and run <code>python -m pip install -r requirements.txt</code> in the repository&apos;s <code>print-agent</code> folder. Put the downloaded profile there.</li>
                <li>Install its Windows driver for USB, or pair Bluetooth in Windows and identify the outgoing COM port. Run <code>python agent.py devices</code> and use the exact device name in your profile.</li>
                <li>Run <code>python agent.py validate</code>, then <code>python agent.py preview</code> before any hardware test. Run <code>python agent.py test</code> and check the paper.</li>
                <li>Generate registration SQL with <code>python agent.py registration-sql</code>. Have the backend administrator review the branch/station and apply it to the matching environment. Deploy the new USB/Bluetooth protocol support first.</li>
                <li>Set the same private <code>PRINT_AGENT_API_KEY</code> on the backend and print station. Run <code>python agent.py run</code>, then check the Printer Queue for agent heartbeat and a DEV KOT.</li>
            </ol>
            <p>Transport success means data was accepted by the spooler or connection; it does not prove paper came out. If a write or acknowledgement is uncertain, the agent pauses. Inspect the paper before approving a retry.</p>
            <p>This setup prints KOTs with English/ASCII text. Invoice printing and other languages need separate validation. Selecting a station does not create new routing rules for it.</p>
            <a className="inline-block underline" href="https://github.com/iamvivek907/gokul-sweets/blob/dev/docs/printer-setup.md" target="_blank" rel="noreferrer">Full installation and recovery guide</a>
        </section>
    </main>;
}
