"use client";

import Link from "next/link";
import {useEffect, useRef, useState, type FormEvent} from "react";
import {useAdminAuth} from "@/contexts/AdminAuthContext";
import {ADMIN_API_BASE_URL} from "@/lib/constants";
import {printStationRequest, PrintStationRequestError, type PrinterProfile, type PrintStation} from "@/services/printStationApi";

const ACTION_UNCONFIRMED_MESSAGE = "The action reply was lost. Checking server status before another action; inspect the paper before requesting another print.";
const ACTION_RECONCILED_MESSAGE = "Server status refreshed. Check the paper and action result before requesting another printer action.";
const STATUS_TIMEOUT_MESSAGE = "Printer status request timed out. Retrying automatically.";
const stations = ["KITCHEN", "SWEETS", "BEVERAGE", "FAST_FOOD", "BILLING"];
function exportProfile(profile: PrinterProfile, origin: string) {
    const url = new URL(origin);
    if (url.protocol !== "https:" || url.username || url.password || url.pathname !== "/" || url.search || url.hash)
        throw new Error("Enter the HTTPS backend origin without a path or credentials.");
    const content = {api_url: url.origin, branch_id: profile.branchId, station: profile.station,
        agent_id: profile.agentId, printer_code: profile.printerCode, protocol: profile.protocol,
        target: profile.target, port: profile.port, baud_rate: profile.baudRate,
        paper_width_mm: profile.paperWidthMm, auto_cut: profile.autoCut};
    const href = URL.createObjectURL(new Blob([JSON.stringify(content, null, 2) + "\n"], {type: "application/json"}));
    const anchor = document.createElement("a"); anchor.href = href; anchor.download = "config.local.json"; anchor.click();
    setTimeout(() => URL.revokeObjectURL(href), 1000);
}

export default function PrinterSetupPage() {
    const {profile: staff, authorization, hasPermission} = useAdminAuth();
    const [branches, setBranches] = useState<{id: number; name: string}[]>([]);
    const [branchId, setBranchId] = useState<number | null>(null);
    const [station, setStation] = useState("KITCHEN");
    const [state, setState] = useState<PrintStation | null>(null);
    const [connection, setConnection] = useState("ESC_POS_USB");
    const backend = ADMIN_API_BASE_URL;
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState("");
    const [checkedPaper, setCheckedPaper] = useState(false);
    const [unconfirmedScope, setUnconfirmedScope] = useState<string | null>(null);
    const unconfirmedScopeRef = useRef<string | null>(null);
    const revision = useRef(0);
    const busyRef = useRef(false);
    const observedProfile = useRef("");
    const observedPending = useRef<number | undefined>(undefined);
    const currentScope = useRef("");


    useEffect(() => {
        if (!staff) return;
        const controller = new AbortController();
        fetch(`${ADMIN_API_BASE_URL}/api/branches`, {signal: controller.signal}).then(async response => {
            if (!response.ok) throw new Error("Unable to load branches.");
            const all = await response.json() as {id: number; name: string; active: boolean}[];
            const allowed = all.filter(branch => branch.active && (staff.roleName === "OWNER_ADMIN" || staff.branchIds.includes(branch.id)));
            setBranches(allowed); setBranchId(allowed[0]?.id ?? null);
        }).catch(error => {if (error.name !== "AbortError") setMessage(error.message);});
        return () => controller.abort();
    }, [staff]);

    useEffect(() => {

        if (!authorization || branchId === null) return;
        const controller = new AbortController();
        const scope = `${branchId}:${station}`;
        currentScope.current = scope;
        let refreshing = false;
        async function refresh() {
            if (refreshing || busyRef.current || controller.signal.aborted) return;
            refreshing = true;
            const version = ++revision.current;
            try {
                const signal = AbortSignal.any([controller.signal, AbortSignal.timeout(30000)]);
                const result = await printStationRequest(authorization!, branchId!, station, "", undefined, signal);
                if (version === revision.current && scope === currentScope.current) {
                    setState(result);
                    if (unconfirmedScopeRef.current === scope) {
                        unconfirmedScopeRef.current = null;
                        setUnconfirmedScope(null);
                        setCheckedPaper(false);
                        setMessage(ACTION_RECONCILED_MESSAGE);
                    } else {
                        setMessage(current => current === STATUS_TIMEOUT_MESSAGE ? "" : current);
                    }
                    const encoded = JSON.stringify(result.profile);
                    if (encoded !== observedProfile.current) {
                        observedProfile.current = encoded;
                        if (result.profile) setConnection(result.profile.protocol);
                    }
                    if (result.runtime?.pendingJobId !== observedPending.current) {
                        observedPending.current = result.runtime?.pendingJobId;
                        setCheckedPaper(false);
                    }
                }
            } catch (error) {
                if (!controller.signal.aborted && version === revision.current)
                    setMessage(error instanceof Error && error.name === "TimeoutError" ? STATUS_TIMEOUT_MESSAGE : error instanceof Error ? error.message : "Unable to load station.");
            } finally {
                refreshing = false;
            }
        }
        void refresh();
        const timer = setInterval(() => {if (!busyRef.current) void refresh();}, 5000);
        return () => {controller.abort(); clearInterval(timer);};
    }, [authorization, branchId, station]);

    async function mutate(operation: string, body: object) {
        if (!authorization || branchId === null) return;
        const scope = currentScope.current;
        if (busyRef.current || unconfirmedScopeRef.current === scope) return;
        ++revision.current; busyRef.current = true; setBusy(true); setMessage("");
        try {
            const result = await printStationRequest(authorization, branchId, station, operation, body, AbortSignal.timeout(30000));
            if (scope === currentScope.current) {
                setState(result); setCheckedPaper(false);
                setMessage(operation === "/profile" ? "Printer saved and paused. Install the agent or test the updated device, then Start printing." : "Request saved. Wait for the station to report the result.");
            }
        } catch (error) {
            if (scope === currentScope.current) {
                setCheckedPaper(false);
                if (error instanceof PrintStationRequestError && error.status < 500) {
                    setMessage(error.message);
                } else {
                    unconfirmedScopeRef.current = scope;
                    setUnconfirmedScope(scope);
                    setMessage(`${ACTION_UNCONFIRMED_MESSAGE} ${error instanceof Error ? error.message : "Printer action failed."}`);
                }
            }
        } finally {busyRef.current = false; setBusy(false);}
    }

    function save(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (branchId === null) return;
        const data = new FormData(event.currentTarget);
        void mutate("/profile", {branchId, station, agentId: String(data.get("agentId")).trim(),
            printerCode: String(data.get("printerCode")).trim(), protocol: connection,
            target: String(data.get("target")).trim(), port: 9100, baudRate: Number(data.get("baudRate")),
            paperWidthMm: Number(data.get("paperWidthMm")), autoCut: data.get("autoCut") === "on"});
    }

    if (!hasPermission("ORDER_VIEW")) return <p className="p-6">You need permission to view orders to use printer setup.</p>;
    const field = "mt-1 w-full rounded border border-stone-300 bg-white p-2";
    const button = "rounded bg-stone-800 px-4 py-3 font-semibold text-white disabled:opacity-40";
    const actionBlocked = busy || unconfirmedScope === `${branchId}:${station}`;
    const pending = state?.runtime?.pendingJobId;
    const canOperate = hasPermission("ORDER_START_PREPARATION");
    const canConfigure = hasPermission("BRANCH_MANAGE");
    const devices = connection === "ESC_POS_USB" ? state?.runtime?.devices?.usb : state?.runtime?.devices?.bluetooth;
    const status = !state?.configured ? "Not installed" : !state.online ? "Offline" : pending || state.runtime?.status === "ERROR" || state.commandResult?.status === "NEEDS_ATTENTION" ? "Needs attention" : state.enabled ? "Running" : "Paused";
    return <main className="mx-auto max-w-3xl space-y-6 p-5 text-stone-800">
        <Link href="/admin/printing" className="underline">Back to printer queue</Link>
        <h1 className="text-3xl font-bold">Printer setup</h1>
        <p>Install once on the shop&apos;s Windows computer. After installation, manage printing here from desktop or Android. Keep that Windows account signed in and the computer awake.</p>
        <div className="grid gap-4 sm:grid-cols-2">
            <label>Branch<select className={field} value={branchId ?? ""} disabled={busy} onChange={event => {currentScope.current = `${event.target.value}:${station}`; ++revision.current; setState(null); setCheckedPaper(false); setMessage(""); setBranchId(Number(event.target.value));}}><option value="" disabled>Select a branch</option>{branches.map(branch => <option key={branch.id} value={branch.id}>{branch.name} (ID {branch.id})</option>)}</select></label>
            <label>Station<select className={field} value={station} disabled={busy} onChange={event => {currentScope.current = `${branchId}:${event.target.value}`; ++revision.current; setState(null); setCheckedPaper(false); setMessage(""); setStation(event.target.value);}}>{stations.map(value => <option key={value}>{value}</option>)}</select></label>
        </div>
        <section aria-labelledby="station-controls" className="space-y-3 rounded-xl border border-stone-200 bg-stone-50 p-5">
            <h2 id="station-controls" className="text-xl font-bold">Daily printing</h2>
            <p role="status"><strong>{status}</strong>{state?.command?.id && " — printer action pending"}</p>
            <p>Pause stops new tickets; a ticket already in progress finishes. Start resumes queued tickets. An offline computer must be signed in and connected before it can print.</p>
            <div className="flex flex-wrap gap-3">
                <button className={button} disabled={actionBlocked || !canOperate || !state?.configured || !!pending || !!state.command?.id || !!state.enabled} onClick={() => void mutate("/mode", {enabled: true})}>Start printing</button>
                <button className={button} disabled={actionBlocked || !canOperate || !state?.configured || !state.enabled} onClick={() => void mutate("/mode", {enabled: false})}>Pause printing</button>
                <button className={button} disabled={actionBlocked || !canOperate || !state?.online || state.enabled || !!pending || !!state.command?.id} onClick={() => void mutate("/command", {action: "TEST"})}>Print test ticket</button>
            </div>
            {state?.runtime?.message && <p>{state.runtime.message}</p>}
            {state?.commandResult?.message && <p role="status">{state.commandResult.message}</p>}
            {!!pending && <div className="space-y-3 rounded border border-amber-300 bg-amber-50 p-3">
                <p><strong>Check ticket #{pending} on paper.</strong> Printing is held because its result is uncertain. Check the Windows spooler and cancel any unfinished copy before approving another attempt.</p>
                <label className="block"><input type="checkbox" checked={checkedPaper} onChange={event => setCheckedPaper(event.target.checked)} /> I checked the paper and spooler for this ticket.</label>
                <div className="flex flex-wrap gap-3">
                    <button className={button} disabled={actionBlocked || !canOperate || !checkedPaper || !state?.online || state.enabled || !!state.command?.id} onClick={() => void mutate("/command", {action: "PRINTED", jobId: pending})}>Ticket already printed</button>
                    <button className={button} disabled={actionBlocked || !canOperate || !checkedPaper || !state?.online || state.enabled || !!state.command?.id} onClick={() => void mutate("/command", {action: "RETRY", jobId: pending})}>Approve another attempt</button>
                </div>
            </div>}
        </section>
        <section aria-labelledby="install-help" className="space-y-3 rounded-xl border border-amber-200 bg-amber-50 p-5">
            <h2 id="install-help" className="text-xl font-bold">One-time installation — including an 80 mm printer</h2>
            <ol className="list-decimal space-y-2 pl-6">
                <li>Connect and load the printer. Install the manufacturer&apos;s Windows USB driver, or pair a Bluetooth Classic/SPP printer and find its outgoing COM port. Choose an ESC/POS model; BLE-only and GDI-only printers need a different bridge.</li>
                <li>Save the printer settings below. Choose <strong>80 mm</strong> for an 80 mm roll. This registers the printer on the server while printing is paused.</li>
                <li>Download the Windows installer and your saved profile. Extract the ZIP, double-click <code>install.cmd</code> and select <code>config.local.json</code>. The installer installs its Python runtime and dependencies, then asks once for the private backend agent key. Your backend administrator supplies that key; never paste it into this webpage.</li>
                <li>Wait for <strong>Online/Paused</strong> here, then choose <strong>Print test ticket</strong>. Check readable text, paper feed and the cutter. Choose <strong>Start printing</strong>.</li>
                <li>Open a paid DEV order and choose Prepare / KOT or Start preparation. Check the ticket and Printer Queue. Customer checkout alone does not start preparation.</li>
            </ol>
            <p>Windows starts the agent in the background after the installing account signs in. No daily PowerShell commands are needed. Closing this webpage does not stop printing. Hardware pairing, paper changes and fixing a disconnected computer still happen at the shop.</p>
            <a href="/downloads/gokul-print-agent.zip" download className="inline-block rounded bg-stone-800 px-4 py-3 font-semibold text-white">Download Windows installer</a>
            <label className="block">Backend HTTPS address (this environment)<input className={field} type="url" value={backend} readOnly /></label>
            <button className={button} disabled={!state?.profile || busy} onClick={() => {
                try {if (state?.profile) exportProfile(state.profile, backend);}
                catch (error) {setMessage(error instanceof Error ? error.message : "Profile download failed.");}
            }}>Download saved printer profile</button>
        </section>
        <form key={`${branchId}:${station}:${JSON.stringify(state?.profile)}`} onSubmit={save} className="grid gap-4 rounded-xl border border-stone-200 p-5 sm:grid-cols-2">
            <h2 className="text-xl font-bold sm:col-span-2">Printer settings</h2>
            <p className="sm:col-span-2">Pause and finish or resolve the current ticket before changing settings. Branch management permission is required. Installed agent ID and printer code stay fixed.</p>
            <label>Agent ID<input className={field} name="agentId" defaultValue={state?.profile?.agentId ?? `shop-${branchId ?? 1}-${station.toLowerCase()}`} readOnly={!!state?.configured} maxLength={120} required /></label>
            <label>Printer code<input className={field} name="printerCode" defaultValue={state?.profile?.printerCode ?? `${station}_PRINTER`} readOnly={!!state?.configured} maxLength={50} required /></label>
            <label>Connection<select className={field} value={connection} onChange={event => setConnection(event.target.value)}><option value="ESC_POS_USB">USB (Windows printer queue)</option><option value="ESC_POS_BLUETOOTH">Bluetooth Classic (serial port)</option></select></label>
            <label>{connection === "ESC_POS_USB" ? "Exact Windows printer name" : "Paired outgoing COM port"}<input className={field} name="target" list="detected-devices" defaultValue={state?.profile?.target ?? ""} maxLength={255} required placeholder={connection === "ESC_POS_USB" ? "Manufacturer's Windows queue name" : "COM5"} /><datalist id="detected-devices">{devices?.map(device => <option key={device} value={device} />)}</datalist></label>
            <label>Paper width<select className={field} name="paperWidthMm" defaultValue={state?.profile?.paperWidthMm ?? 80}><option value="58">58 mm</option><option value="80">80 mm</option></select></label>
            <label>Bluetooth baud rate<select className={field} name="baudRate" defaultValue={state?.profile?.baudRate ?? 9600}>{[9600, 19200, 38400, 57600, 115200].map(value => <option key={value}>{value}</option>)}</select></label>
            <label><input name="autoCut" type="checkbox" defaultChecked={state?.profile?.autoCut ?? false} /> Enable cutter only if the printer has one</label>
            <button className={button} type="submit" disabled={actionBlocked || !canConfigure || !state || branchId === null || !!state.enabled || !!pending || !!state.command?.id}>Save printer settings</button>
        </form>
        {message && <p role="status" className="rounded border border-stone-300 p-3">{message}</p>}
        <p>Only one agent and active printer per branch/station. English/ASCII KOTs are supported. Invoice printing and other languages need separate validation; selecting another station does not add routing rules.</p>
        <a className="inline-block underline" href="https://github.com/iamvivek907/gokul-sweets/blob/dev/docs/printer-setup.md" target="_blank" rel="noreferrer">Full installation and recovery guide</a>
    </main>;
}
