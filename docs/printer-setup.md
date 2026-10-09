# Physical printer setup

The existing backend creates automatic **KOT** jobs, grants a 60-second claim lease,
tracks heartbeats and accepts printed/failed reports. `print-agent/agent.py` supplies
the missing local worker. No inbound port or browser printer permissions are needed.
The Render instance cannot access the shop's USB/Bluetooth hardware directly.

## Supported setup

| Printer connection | Print station | Device configuration |
| --- | --- | --- |
| USB ESC/POS | Windows, vendor driver exposing a RAW queue | Exact Windows printer name |
| Bluetooth Classic/SPP ESC/POS | Windows with paired outgoing COM port | COM port and manufacturer baud rate |
| Network ESC/POS (existing support) | Computer running Python | Printer IP/hostname and TCP port, usually 9100 |
| Android only, Bluetooth Classic or USB OTG | Not implemented by this desktop agent | Requires a native Android bridge, permissions and hardware-specific testing |
| BLE-only, proprietary/GDI-only printers | Not supported | Requires a manufacturer-specific transport/renderer |

Staff can use the webapp on **Android and desktop** with the Windows station printing
from the shared queue. This does not mean an Android phone can run this Python agent.
Prefer an always-on Windows station for the first rollout. Before buying, obtain the
exact model, ESC/POS support, 58/80 mm width, USB RAW driver and/or Bluetooth **Classic
SPP** support. A Bluetooth logo alone is insufficient. Verify cutter support before
enabling it. This renderer uses ASCII/default English text; non-ASCII KOT text is
rejected before writing, rather than silently losing product names. Native language
printing needs a model-specific code page or raster renderer.

## Prepare before hardware arrives

1. Open **Admin → Printer queue → Set up a USB or Bluetooth printer**. Download a
   profile after choosing the connection, branch ID, station and printer code.
   The form stores no credential and does not register a server printer.
2. On the print station install Python 3.12+ and obtain the repository's `print-agent`
   folder. Open PowerShell in that folder:

   ```powershell
   python -m venv .venv
   .\.venv\Scripts\python.exe -m pip install -r requirements.txt
   Copy-Item config.example.json config.local.json
   ```

   Replace the example with the downloaded profile, or edit it manually. Set the
   actual backend HTTPS **origin**, branch ID and a stable unique agent ID. Do not
   use the storefront URL. Keep DEV and production profiles/secrets separate.
3. Run these with the venv Python (abbreviated to `python` below):

   ```powershell
   python agent.py validate
   python agent.py preview
   ```

   These work without a printer, API key or live backend and do not claim jobs.

## Connect and test hardware

USB: install the manufacturer's Windows driver, connect power/USB and verify a
Windows test print. Bluetooth: pair in Windows settings, enable its serial service,
and find the **outgoing** COM port. BLE GATT characteristics are not COM ports.

```powershell
python agent.py devices
python agent.py test
```

Copy the exact queue name or COM port into `target`. Bluetooth uses 8-N-1 with no
flow control; use the manufacturer's baud rate. Confirm the setup ticket has
readable text, correct wrapping, feed and (if enabled) cut. A successful command
means bytes were accepted, not a sensor-confirmed paper print. USB spooler writes
may block or spool while the printer is offline; check its Windows queue. Transport
runs in a child process with a 30-second deadline, including serial flush. A timeout
cannot retract data already accepted by a printer/spooler and requires paper inspection.

## Register the backend printer and start the agent

Deploy the backend version containing `ESC_POS_USB` / `ESC_POS_BLUETOOTH` **before**
registering those protocols; older Java versions cannot read these enum values.
There is no schema migration: existing `host` stores the local queue/COM identifier;
`port=9100` satisfies the existing schema and is ignored for USB/Bluetooth.

```powershell
python agent.py registration-sql
```

An administrator must review and execute the generated transaction in the correct
database. It deactivates other printers for that branch/station and upserts this
printer. Stop every agent for that station before switching; existing claims must
finish or be reconciled first. This command only prints SQL; it never opens a
database connection. Register **one active printer and one running agent per
branch/station**, since the server currently picks the first active device.

Generate a random private key, for example `python -c "import secrets; print(secrets.token_urlsafe(32))"`.
Set it as `PRINT_AGENT_API_KEY` in the backend secret environment and restart the
backend. On the trusted print station, read it without showing the typed value:

```powershell
$secureKey = Read-Host 'Print agent API key' -AsSecureString
$env:PRINT_AGENT_API_KEY = [System.Net.NetworkCredential]::new('', $secureKey).Password
python agent.py run
```

Keep the key out of frontend environment variables, URLs, git and profile JSON.
The existing backend key is shared across branches: provision it only to trusted
shop administrators/computers. It is not a per-printer or staff-session credential.
Keep the computer awake and the process running. After manual acceptance, configure
Windows Task Scheduler to run the venv Python with the profile's absolute path and
the `print-agent` working directory. Provision its secret securely; do not put the
secret on the command line. Do not run a second agent from another profile/computer
for the same branch/station.

The agent polls every 2 seconds and heartbeats every 20 seconds. It requires the
server printer's code, station, protocol, target, port, width and cut settings to
match the local profile before writing. Backend/network errors before transport
are retried; authentication errors stop the process. Existing automatic routing
is unchanged: registering BEVERAGE/BILLING does not create split tickets or invoices.

## Uncertain prints and recovery

The durable `config.local.json.sqlite3` journal is written **before** transport.
Keep it with the profile; do not delete it, rename the profile to bypass it, or copy
the profile to a second running station. A profile lock prevents concurrent local
commands, not agents on other computers. The server can reclaim an expired lease;
paper printing and a database acknowledgement are not an atomic transaction.

If transport or the printed acknowledgement fails, or the machine restarts during
a print, this agent stops before claiming another job. It does not automatically
resend the uncertain ticket. Inspect paper and the Windows spooler first:

```powershell
# Ticket is present (or you accept its spooler delivery): acknowledgement only.
python agent.py resolve printed
# You checked/cancelled any spooler job and explicitly approve a new attempt.
python agent.py resolve retry
python agent.py run
```

`retry` tells the backend the old claim failed; normal retry delay/max-attempt
rules apply. Never approve retry just because the paper was delayed. If the
server rejects resolution (HTTP 409), another claim/status transition occurred:
stop all agents, reconcile the job in Printer Queue, and reconcile the local
journal before restarting. Escalate to the administrator; do not erase a journal
while an unresolved claim or spooler job exists. Other-language/profile validation
failures are reported as failed **before** sending and stop this process.

## DEV acceptance before production

- Local test on the actual model for 58/80 mm, long product names, weighted quantities
  and cutter off/on when supported.
- Start preparation on a DEV paid order; confirm one initial KOT, correct branch,
  order number, quantities, pickup date/window and IST label.
- Test a delivery order with null pickup fields; confirm delivery date/window.
- Test staff reprint and multiple copies separately; confirm audit/queue status.
- Disconnect/reconnect printer and internet, restart agent before/after a write,
  and reject a second process. Confirm uncertain jobs pause and operator recovery
  does not silently duplicate paper.
- Verify heartbeat/queue errors are visible; an ONLINE heartbeat only proves the
  agent can reach the server, not printer readiness. Acceptance must include paper.

Software tests use fake transports/API; actual Windows drivers, COM pairing,
offline spooler behavior and physical printing remain unverified until hardware
arrives. Purchase/production readiness is not established by these tests.

Protocol references: [Windows RAW printing](https://learn.microsoft.com/en-us/windows/win32/printdocs/sending-data-directly-to-a-printer),
[pySerial](https://pyserial.readthedocs.io/en/latest/pyserial_api.html),
[Chrome Bluetooth Classic on desktop](https://developer.chrome.com/blog/bluetooth-rfcomm-updates-web-serial),
[Android native Bluetooth](https://developer.android.com/develop/connectivity/bluetooth/connect-bluetooth-devices).
