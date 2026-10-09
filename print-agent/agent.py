"""Local ESC/POS KOT agent. Credentials stay in PRINT_AGENT_API_KEY, never the browser."""

import argparse
import contextlib
import json
import multiprocessing
import os
from pathlib import Path
import socket
import sqlite3
import sys
import textwrap
import time
import unicodedata
from urllib import error, parse, request

PROTOCOLS = ("ESC_POS_USB", "ESC_POS_BLUETOOTH", "ESC_POS_TCP")
STATIONS = ("KITCHEN", "SWEETS", "BEVERAGE", "FAST_FOOD", "BILLING")


def load_config(path):
    cfg = json.loads(Path(path).read_text(encoding="utf-8"))
    return validate_config(cfg)


def validate_config(cfg):
    url = parse.urlsplit(cfg["api_url"])
    if (url.scheme != "https" or not url.hostname or url.username or url.password
            or url.query or url.fragment or url.path not in ("", "/")):
        raise ValueError("api_url must be an HTTPS backend origin without credentials or a path")
    if type(cfg["branch_id"]) is not int or cfg["branch_id"] < 1:
        raise ValueError("branch_id must be a positive integer")
    for key, limit in (("agent_id", 120), ("printer_code", 50), ("target", 255)):
        value = cfg[key]
        if not isinstance(value, str) or not value.strip() or len(value) > limit:
            raise ValueError(f"Invalid {key}")
        if any(unicodedata.category(c).startswith("C") for c in value):
            raise ValueError(f"Control characters are forbidden in {key}")
    if cfg["protocol"] not in PROTOCOLS or cfg["station"] not in STATIONS:
        raise ValueError("Unsupported protocol or station")
    if cfg["paper_width_mm"] not in (58, 80) or type(cfg["auto_cut"]) is not bool:
        raise ValueError("Select 58 or 80 mm paper and a boolean auto_cut")
    if type(cfg["port"]) is not int or not 1 <= cfg["port"] <= 65535:
        raise ValueError("Invalid port")
    if type(cfg["baud_rate"]) is not int or cfg["baud_rate"] not in (9600, 19200, 38400, 57600, 115200):
        raise ValueError("Unsupported baud rate")
    cfg["api_url"] = cfg["api_url"].rstrip("/")
    return cfg


class NoRedirect(request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # Never forward the shared agent credential to a redirect destination.
        return None


class Api:
    def __init__(self, cfg):
        self.cfg = cfg
        self.key = os.environ.get("PRINT_AGENT_API_KEY", "")
        if not self.key:
            raise ValueError("Set PRINT_AGENT_API_KEY on this computer and the backend")
        self.opener = request.build_opener(NoRedirect())

    def post(self, path, body):
        req = request.Request(
            self.cfg["api_url"] + "/api/print-agent/" + path,
            data=json.dumps(body).encode(), method="POST",
            headers={"Content-Type": "application/json", "X-Print-Agent-Key": self.key})
        with self.opener.open(req, timeout=10) as response:
            if response.status == 204:
                return None
            return json.loads(response.read(1024 * 1024))

    def identity(self):
        return {"branchId": self.cfg["branch_id"], "agentId": self.cfg["agent_id"],
                "station": self.cfg["station"]}


def clean(value):
    # Printer commands, line breaks and bidi controls must never come from order text.
    return "".join(c if not unicodedata.category(c).startswith("C") else " "
                   for c in str(value or ""))


def ticket(cfg, kot=None):
    width = 32 if cfg["paper_width_mm"] == 58 else 48
    lines = ["GOKUL SWEETS", "PRINTER SETUP TEST" if kot is None else "KITCHEN ORDER TICKET"]
    if kot is None:
        lines += [cfg["printer_code"], cfg["protocol"], f"Paper: {cfg['paper_width_mm']} mm",
                  "1 x Test sweet", "500 g Test weight", "Check readable text and paper feed."]
    else:
        lines += [clean(kot.get("branchName")), clean(kot.get("branchAddress")),
                  "KOT: " + clean(kot["kotNumber"]),
                  "Order: " + clean(kot.get("customerOrderNumber") or kot["orderNumber"])]
        delivery = kot.get("fulfillmentType") == "DELIVERY"
        prefix = "delivery" if delivery else "pickup"
        lines += [("Delivery: " if delivery else "Pickup: ") + clean(kot.get(prefix + "Date")),
                  clean(kot.get(prefix + "StartTime")) + " - " + clean(kot.get(prefix + "EndTime")) + " IST"]
        if not delivery:
            lines.append("Pickup type: " + clean(kot.get("pickupType")))
        lines += ["Staff: " + clean(kot.get("startedByStaffName")), "-" * width]
        items = kot["items"]
        if not isinstance(items, list) or not 1 <= len(items) <= 200:
            raise ValueError("Invalid KOT item count")
        for item in sorted(items, key=lambda row: row.get("displayOrder") or 0):
            label = item.get("quantityLabel")
            if not label:
                label = (str(item.get("weightGrams")) + " g" if item.get("saleMode") == "WEIGHT"
                         else str(item["quantity"]) + " pcs")
            name = item["productName"]
            # Backend includes this suffix for older agents. Quantity is already separate.
            suffix = " · " + str(label)
            if item.get("saleMode") == "WEIGHT" and name.endswith(suffix):
                name = name[:-len(suffix)]
            lines.append(clean(label) + "  " + clean(name))
    # ASCII deliberately fails closed: do not silently lose non-English product names.
    text = "\n".join(part for line in lines for part in
                     (textwrap.wrap(line, width=width) or [""])) + "\n\n\n\n"
    data = b"\x1b@" + text.encode("ascii")
    if cfg["auto_cut"]:
        data += b"\x1dV\x00"
    if len(data) > 65536:
        raise ValueError("KOT exceeds ticket size limit")
    return data


def send(cfg, data):
    if cfg["protocol"] == "ESC_POS_USB":
        if sys.platform != "win32":
            raise ValueError("USB RAW printing requires a Windows print station")
        import win32print
        handle = win32print.OpenPrinter(cfg["target"])
        try:
            win32print.StartDocPrinter(handle, 1, ("Gokul KOT", None, "RAW"))
            win32print.StartPagePrinter(handle)
            if win32print.WritePrinter(handle, data) != len(data):
                raise IOError("Partial Windows spooler write")
            win32print.EndPagePrinter(handle)
            win32print.EndDocPrinter(handle)
        except BaseException:
            win32print.AbortPrinter(handle)
            raise
        finally:
            win32print.ClosePrinter(handle)
    elif cfg["protocol"] == "ESC_POS_BLUETOOTH":
        import serial
        # Use a paired OS serial device (e.g. COM5), not arbitrary pySerial URLs.
        with serial.Serial(cfg["target"], cfg["baud_rate"], timeout=5, write_timeout=10) as port:
            if port.write(data) != len(data):
                raise IOError("Partial Bluetooth serial write")
            port.flush()
    else:
        with socket.create_connection((cfg["target"], cfg["port"]), timeout=10) as connection:
            connection.sendall(data)


def transport_worker(cfg, data, result):
    try:
        send(cfg, data)
        result.send(True)
    except BaseException:
        result.send(False)
    finally:
        result.close()


def deliver(cfg, data):
    # Driver/spooler flushes can block despite a serial write timeout. Isolate them
    # so transport cannot outlive the server's 60-second claim lease indefinitely.
    ctx = multiprocessing.get_context("spawn")
    reader, writer = ctx.Pipe(duplex=False)
    worker = ctx.Process(target=transport_worker, args=(cfg, data, writer))
    try:
        worker.start()
        writer.close()
        worker.join(30)
        if worker.is_alive():
            raise TimeoutError("Printer transport timed out; inspect paper/spooler")
        if worker.exitcode != 0 or not reader.poll() or not reader.recv():
            raise IOError("Printer transport failed; inspect paper/spooler")
    finally:
        # Ctrl+C and unexpected failures must stop the transport before the caller
        # releases its profile lock. A live worker could otherwise overlap recovery.
        if worker.pid is not None and worker.is_alive():
            worker.terminate()
            worker.join(5)
            if worker.is_alive():
                worker.kill()
                worker.join()
        reader.close()
        writer.close()


@contextlib.contextmanager
def exclusive_profile(path):
    # All commands share this lock; a second process cannot race a pending print.
    with open(str(path) + ".lock", "a+b") as handle:
        handle.seek(0)
        handle.write(b"0")
        handle.flush()
        handle.seek(0)
        if sys.platform == "win32":
            import msvcrt
            msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
        else:
            import fcntl
            fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
        yield


def match_printer(cfg, job):
    printer = job["printer"]
    expected = {"code": cfg["printer_code"], "station": cfg["station"],
                "protocol": cfg["protocol"], "host": cfg["target"], "port": cfg["port"],
                "paperWidthMm": cfg["paper_width_mm"], "autoCut": cfg["auto_cut"]}
    if any(printer.get(key) != value for key, value in expected.items()):
        raise ValueError("Server printer differs from local profile; correct configuration first")
    copies = job["copies"]
    if type(copies) is not int or not 1 <= copies <= 10:
        raise ValueError("Invalid copy count")
    return ticket(cfg, job["kot"]) * copies


def save_pending(db, cfg, job, state):
    identity = {key: cfg[key] for key in ("api_url", "branch_id", "agent_id", "station", "printer_code")}
    entry = {"identity": identity, "job": {"printJobId": job["printJobId"],
             "claimToken": job["claimToken"]}, "state": state}
    with db:
        db.execute("INSERT OR REPLACE INTO pending VALUES (1, ?)", (json.dumps(entry),))


def acknowledge(api, job, outcome):
    body = {"agentId": api.cfg["agent_id"], "claimToken": job["claimToken"]}
    if outcome == "retry":
        body.update(errorCode="OPERATOR_APPROVED_RETRY", errorMessage="Operator checked paper and approved retry")
    path = f"jobs/{int(job['printJobId'])}/"
    try:
        api.post(path + ("failed" if outcome == "retry" else "printed"), body)
    except error.HTTPError as exc:
        if outcome != "printed" or exc.code != 409:
            raise
        # The server may have committed the first acknowledgement before its
        # response was lost. Only its scoped terminal status can resolve that case.
        status = api.post(path + "status", api.identity())
        if (not isinstance(status, dict)
                or status.get("printJobId") != job["printJobId"]
                or status.get("branchId") != api.cfg["branch_id"]
                or status.get("station") != api.cfg["station"]
                or status.get("status") != "PRINTED"):
            raise ValueError("Job is not confirmed printed in this branch/station; keep journal and reconcile in Printer Queue") from exc


def process_job(api, db, cfg, job):
    try:
        data = match_printer(cfg, job)
    except (ValueError, KeyError, UnicodeError, TypeError):
        try:
            api.post(f"jobs/{int(job['printJobId'])}/failed", {
                "agentId": cfg["agent_id"], "claimToken": job["claimToken"],
                "errorCode": "LOCAL_PROFILE_OR_TICKET_INVALID",
                "errorMessage": "Check local printer profile and ASCII KOT text"})
        finally:
            # Validation must hold printing even when reporting the failure loses
            # its response. No printer data was sent; never turn this into a
            # recoverable connection-only error in the managed loop.
            raise ValueError("Job validation failed; agent stopped before sending printer data")
    save_pending(db, cfg, job, "uncertain")
    deliver(cfg, data)
    save_pending(db, cfg, job, "sent")
    acknowledge(api, job, "printed")
    with db:
        db.execute("DELETE FROM pending")
    print(f"Job {job['printJobId']}: sent to transport and acknowledged (check paper)", flush=True)


def consume(cfg, path, resolve=None):
    api = Api(cfg)
    with exclusive_profile(path), contextlib.closing(sqlite3.connect(str(path) + ".sqlite3")) as db:
        db.execute("PRAGMA synchronous=FULL")
        db.execute("CREATE TABLE IF NOT EXISTS pending (id INTEGER PRIMARY KEY CHECK(id=1), payload TEXT NOT NULL)")
        row = db.execute("SELECT payload FROM pending").fetchone()
        if row:
            pending = json.loads(row[0])
            if any(cfg[key] != value for key, value in pending["identity"].items()):
                raise ValueError("Restore original profile before resolving its pending job")
            print(f"Pending job {pending['job']['printJobId']}: {pending['state']}", flush=True)
            if not resolve:
                raise ValueError("Printing paused. Check paper and use resolve printed or resolve retry")
            acknowledge(api, pending["job"], resolve)
            with db:
                db.execute("DELETE FROM pending")
            print("Pending job resolved; start run again")
            return
        if resolve:
            raise ValueError("No pending job to resolve")
        next_heartbeat = 0
        while True:
            try:
                if time.monotonic() >= next_heartbeat:
                    api.post("heartbeat", api.identity())
                    next_heartbeat = time.monotonic() + 20
                job = api.post("jobs/claim", api.identity())
            except error.HTTPError as exc:
                if exc.code not in (429, 502, 503, 504):
                    raise
                print(f"Backend HTTP {exc.code}; polling paused", flush=True)
                time.sleep(5)
                continue
            except (error.URLError, TimeoutError):
                # Retry only read/claim stage. Never retry a physical write automatically.
                print("Backend unavailable; polling paused", flush=True)
                time.sleep(5)
                continue
            if job:
                process_job(api, db, cfg, job)
            time.sleep(2)


def devices_snapshot():
    result = {"usb": [], "bluetooth": []}
    if sys.platform == "win32":
        import win32print
        result["usb"] = [str(row[2])[:255] for row in win32print.EnumPrinters(
            win32print.PRINTER_ENUM_LOCAL | win32print.PRINTER_ENUM_CONNECTIONS)][:30]
    from serial.tools import list_ports
    result["bluetooth"] = [str(row.device)[:255] for row in list_ports.comports()][:30]
    return result


def managed_profile(cfg, profile):
    # Only connection fields may change remotely. Backend origin and installed identity stay local.
    if (profile.get("branchId") != cfg["branch_id"] or profile.get("station") != cfg["station"]
            or profile.get("agentId") != cfg["agent_id"] or profile.get("printerCode") != cfg["printer_code"]):
        raise ValueError("Installed identity differs from Admin profile")
    updated = dict(cfg)
    for local, remote in (("protocol", "protocol"), ("target", "target"), ("port", "port"),
                          ("baud_rate", "baudRate"), ("paper_width_mm", "paperWidthMm"), ("auto_cut", "autoCut")):
        updated[local] = profile[remote]
    return validate_config(updated)


def managed_command(api, db, cfg, command):
    command_id = command["id"]
    receipt = db.execute("SELECT result FROM commands WHERE id=?", (command_id,)).fetchone()
    if receipt:
        return json.loads(receipt[0])
    if time.time() * 1000 > command.get("expiresAt", float("inf")):
        result = {"status": "EXPIRED", "message": "Printer action expired while disconnected. Check the station and request it again."}
        with db:
            db.execute("INSERT INTO commands VALUES (?,?)", (command_id, json.dumps(result)))
        return result
    row = db.execute("SELECT payload FROM pending").fetchone()
    pending = json.loads(row[0]) if row else None
    action = command["action"]
    if action == "TEST" and pending:
        raise ValueError("Resolve pending KOT before a test")
    if action in ("PRINTED", "RETRY"):
        if not pending or pending["job"]["printJobId"] != command.get("jobId"):
            raise ValueError("Recovery ticket changed; inspect paper and refresh")
        if any(cfg[key] != value for key, value in pending["identity"].items()):
            raise ValueError("Restore original profile before recovery")
    if action not in ("TEST", "PRINTED", "RETRY"):
        raise ValueError("Unknown printer command")
    if action == "TEST":
        # Record before physical output. A restart must never repeat an uncertain test.
        result = {"status": "NEEDS_ATTENTION", "message": "Test output uncertain. Inspect paper before requesting another test."}
        with db:
            db.execute("INSERT INTO commands VALUES (?,?)", (command_id, json.dumps(result)))
        deliver(cfg, ticket(cfg))
    else:
        acknowledge(api, pending["job"], "printed" if action == "PRINTED" else "retry")
    result = {"status": "DONE", "message": "Test sent: check paper." if action == "TEST" else "Pending ticket resolved. Start printing when ready."}
    with db:
        if action != "TEST":
            db.execute("DELETE FROM pending")
        db.execute("INSERT OR REPLACE INTO commands VALUES (?,?)", (command_id, json.dumps(result)))
    return result


def managed(cfg, path):
    api = Api(cfg)
    with exclusive_profile(path), contextlib.closing(sqlite3.connect(str(path) + ".sqlite3")) as db:
        db.execute("PRAGMA synchronous=FULL")
        db.execute("CREATE TABLE IF NOT EXISTS pending (id INTEGER PRIMARY KEY CHECK(id=1), payload TEXT NOT NULL)")
        db.execute("CREATE TABLE IF NOT EXISTS commands (id TEXT PRIMARY KEY, result TEXT NOT NULL)")
        completed_id, completed_result = None, None
        runtime_error = None
        connection_error = None
        hardware = {"usb": [], "bluetooth": []}
        next_devices = next_heartbeat = 0
        while True:
            try:
                row = db.execute("SELECT payload FROM pending").fetchone()
                pending = json.loads(row[0]) if row else None
                if time.monotonic() >= next_devices:
                    try:
                        hardware = devices_snapshot()
                    except Exception:
                        runtime_error = "Device discovery failed; check Windows drivers and pairing."
                    next_devices = time.monotonic() + 60
                runtime = {"status": "NEEDS_ATTENTION" if pending else "ERROR" if runtime_error or connection_error else "READY",
                           "message": runtime_error or connection_error, "devices": hardware,
                           "pendingJobId": pending["job"]["printJobId"] if pending else None,
                           "pendingState": pending["state"] if pending else None}
                state = api.post("control", dict(api.identity(), runtime=runtime,
                                 completedCommandId=completed_id, commandResult=completed_result))
                completed_id, completed_result = None, None
                connection_error = None
                if time.monotonic() >= next_heartbeat:
                    api.post("heartbeat", api.identity())
                    next_heartbeat = time.monotonic() + 20
                updated = managed_profile(cfg, state["profile"])
                if updated != cfg:
                    runtime_error = None
                cfg = updated
                api.cfg = cfg
                command = state.get("command")
                if command:
                    if state.get("enabled"):
                        raise ValueError("Pause printing before a printer action")
                    completed_id = command["id"]
                    try:
                        completed_result = managed_command(api, db, cfg, command)
                        if completed_result.get("status") == "DONE":
                            runtime_error = None
                    except Exception:
                        completed_result = {"status": "NEEDS_ATTENTION", "message": "Action failed. Inspect paper, connection and pending ticket before retrying."}
                        runtime_error = completed_result["message"]
                elif state.get("enabled") and not pending and not runtime_error:
                    job = api.post("jobs/claim", api.identity())
                    if job:
                        process_job(api, db, cfg, job)
                        runtime_error = None
            except (error.URLError, TimeoutError):
                # Durable pending/command receipts prevent resend after any connection failure.
                connection_error = "Backend connection interrupted; reconnecting. Pending tickets are held."
            except Exception:
                runtime_error = "Printing needs attention. Check Admin setup, credentials and hardware."
            time.sleep(2)


def devices():
    if sys.platform == "win32":
        import win32print
        print("Windows printer names (USB RAW):")
        for printer in win32print.EnumPrinters(win32print.PRINTER_ENUM_LOCAL | win32print.PRINTER_ENUM_CONNECTIONS):
            print("  " + printer[2])
    from serial.tools import list_ports
    print("Serial devices (paired Bluetooth outgoing COM port):")
    for port in list_ports.comports():
        print(f"  {port.device}: {port.description}")


def sql(cfg):
    quote = lambda value: "'" + value.replace("'", "''") + "'"
    return f"""-- Review branch/station first. Stop agents before switching active printers.
BEGIN;
UPDATE printer_devices SET active = FALSE, updated_at = CURRENT_TIMESTAMP
WHERE branch_id = {cfg['branch_id']} AND station = {quote(cfg['station'])};
INSERT INTO printer_devices (branch_id, code, name, station, protocol, host, port, paper_width_mm, auto_cut, active)
VALUES ({cfg['branch_id']}, {quote(cfg['printer_code'])}, {quote(cfg['printer_code'])}, {quote(cfg['station'])},
        {quote(cfg['protocol'])}, {quote(cfg['target'])}, {cfg['port']}, {cfg['paper_width_mm']}, {str(cfg['auto_cut']).upper()}, TRUE)
ON CONFLICT (branch_id, code) DO UPDATE SET name = EXCLUDED.name, station = EXCLUDED.station,
protocol = EXCLUDED.protocol, host = EXCLUDED.host, port = EXCLUDED.port,
paper_width_mm = EXCLUDED.paper_width_mm, auto_cut = EXCLUDED.auto_cut, active = TRUE, updated_at = CURRENT_TIMESTAMP;
COMMIT;"""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", default="config.local.json")
    parser.add_argument("command", choices=("devices", "validate", "preview", "test", "registration-sql", "run", "managed", "resolve"))
    parser.add_argument("outcome", nargs="?", choices=("printed", "retry"))
    args = parser.parse_args()
    if args.command == "devices":
        devices()
        return
    path = Path(args.config).resolve()
    cfg = load_config(path)
    if args.command == "validate":
        print("Profile valid. No hardware or backend connection tested.")
    elif args.command == "preview":
        print(ticket(cfg)[2:].split(b"\x1d")[0].decode("ascii"))
    elif args.command == "registration-sql":
        print(sql(cfg))
    elif args.command == "test":
        with exclusive_profile(path):
            if Path(str(path) + ".sqlite3").exists():
                with contextlib.closing(sqlite3.connect(str(path) + ".sqlite3")) as db:
                    if db.execute("SELECT 1 FROM pending").fetchone():
                        raise ValueError("Resolve pending KOT before a setup test")
            deliver(cfg, ticket(cfg))
            print("Test sent. Confirm the physical ticket yourself.")
    elif args.command == "managed":
        managed(cfg, path)
    else:
        if args.command == "resolve" and not args.outcome:
            parser.error("resolve requires printed or retry")
        consume(cfg, path, args.outcome if args.command == "resolve" else None)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("Stopped. Any pending KOT must be checked before restarting.", file=sys.stderr)
        sys.exit(130)
    except Exception as exc:
        # Avoid dumping HTTP bodies/headers, credentials, claims or customer payloads.
        print(f"Stopped: {type(exc).__name__}. Check setup and pending job before restart.", file=sys.stderr)
        if isinstance(exc, error.HTTPError):
            print(f"Backend HTTP {exc.code} (response body omitted)", file=sys.stderr)
        if isinstance(exc, ValueError):
            print(str(exc), file=sys.stderr)
        sys.exit(1)
