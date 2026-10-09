# Restaurant printer setup

## One-time installation from Admin

Deploy the backend (including Flyway V125) and frontend from this PR before installing the managed agent. The backend administrator sets a private random `PRINT_AGENT_API_KEY` in the backend environment. This is a shop agent credential, not a frontend environment variable. Give it only to the trusted person installing the shop station.

1. Use the **Windows 10/11 computer physically connected to the printer**, signed in as the account that will stay signed in during restaurant operations. Staff can subsequently use Admin from Android, another computer or this computer.
2. For **USB**, install the manufacturer's Windows driver and confirm its local printer queue accepts RAW ESC/POS. For **Bluetooth**, pair a **Classic/SPP** printer in Windows and identify its outgoing COM port and manufacturer's baud rate. BLE-only, proprietary and GDI-only printers need another bridge.
3. Open **Admin → Printer Queue → Printer setup**. Select the branch and **KITCHEN**, enter a unique agent ID/printer code and the connection details. For an **80 mm printer**, select **80 mm**. Leave the cutter off unless the model supports it. Choose **Save printer settings**. The server registers the printer and starts paused; **no SQL is needed**.
4. Choose **Download Windows installer** and **Download saved printer profile**. Use the matching backend HTTPS origin (e.g. DEV's backend, not the customer storefront). Extract the installer ZIP on the Windows computer and double-click `install.cmd`. Select the downloaded `config.local.json` when prompted.
5. The installer uses Windows Package Manager to install a user-scoped Python 3.13 runtime if needed, creates an isolated environment and installs the pinned dependencies. Internet access and Microsoft's App Installer/WinGet are required. Supply the **same private backend agent key** once when prompted; input is hidden. The key is encrypted using Windows DPAPI for that Windows account. It is not in the downloaded profile, browser, task command line or Git.
6. The installer creates a scheduled background task under that account and starts it immediately. It also starts automatically **after that account signs in to Windows**. This preserves the account's USB queue and Bluetooth pairing. This is a startup task, not a machine service that runs before sign-in. Keep the account signed in, the PC awake, connected and powered on. No daily PowerShell command or open terminal is required.
7. Return to Admin setup and wait for **Paused** with an online station. Choose **Print test ticket** and check the actual paper for readable text, wrapping, feed and cutter behavior. Then choose **Start printing**.
8. Open a **paid DEV order** and choose **Prepare / KOT / Start preparation**. Check the correct physical ticket and the server Printer Queue. Customer checkout alone does not start preparation. Validate the actual printer on DEV before configuring production separately.

If the exact queue or COM port was not known initially, use a temporary target when saving the paused profile. Once the agent reports online, its detected USB queues and COM ports appear as suggestions in the target field. Save the correct device while paused, then test. Keep the installed agent ID and printer code unchanged.

The installer normally runs as the shop account without elevation. Windows policies may require administrator rights to register a task: use the **same Windows account** with elevation. Do not install under another administrator's account, because DPAPI and the device pairing belong to the installing account. An interrupted install can be rerun before it completes task registration; an existing complete installation requires an administrator to handle software upgrades. Do not delete an agent journal during upgrades.

## Daily operation from Admin

| Control | Behavior |
| --- | --- |
| Start printing | Allows this installed agent to claim queued tickets. An offline station resumes only after reconnecting. |
| Pause printing | Stops new claims. An already claimed ticket finishes; data accepted by the Windows spooler cannot be retracted. Queued KOTs remain queued. |
| Print test ticket | Available when online, paused and without a pending KOT. Sends one setup ticket. Repeated/lost command replies do not automatically print another copy. |
| Save printer settings | Registers or updates the connection while paused and with no unresolved/in-flight work. The agent picks up connection changes remotely. |
| Ticket already printed | After inspecting paper and spooler, acknowledges the exact pending job without resending printer data. |
| Approve another attempt | After inspecting paper and cancelling any unfinished spooler copy, reports the exact pending claim as failed. Normal backend retry delays and attempt limits still apply. Start printing when ready; a permanently failed job also needs queue Retry. |

Configuration requires `BRANCH_MANAGE`; Start/Pause/tests/recovery require `ORDER_START_PREPARATION`; viewing requires `ORDER_VIEW`. Every Admin operation enforces branch access and the existing staff session/CSRF protections. The agent mailbox uses the private agent key and checks the configured branch, station and agent ID. Only one agent and one active printer per branch/station are supported. A different station does not add routing or invoice support.

The agent polls approximately every two seconds. The Admin panel refreshes every five seconds. Network delays and an in-progress ticket add time. **Offline** means no fresh managed report within 60 seconds; **Running/Paused** include the requested mode, while **Needs attention** shows a pending ticket or runtime problem. Device discovery refreshes approximately every minute. An online heartbeat confirms process connectivity, not paper, stock of rolls or successful physical output.

## Safe recovery

Physical transport is bounded to 30 seconds and isolated in a child process. The agent records an uncertain ticket before sending data and retains the journal if the transport or acknowledgement fails. It keeps reporting online, but claims no further KOTs while a pending ticket exists. **Do not delete the journal or blindly restart/retry to clear it.**

Pause printing in Admin, inspect the paper and Windows spooler, then choose the appropriate recovery button. The checkbox means you checked this specific ticket. Tests are blocked while a KOT is unresolved. If the backend already committed a printed acknowledgement but its response was lost, the agent reconciles its authenticated, scoped `PRINTED` status without printing again. If another agent reclaimed the job or the claim changed, recovery remains blocked: your administrator must reconcile the queue and paper. A failed recovery request does not clear the local journal.

Operator commands expire after 60 seconds if they have not begun, so an old test or recovery request is not executed unexpectedly after a long disconnection. A command already in progress finishes normally.

Test commands also have durable local receipts: a crash or lost response will not replay the same test automatically. A transport-accepted test is not sensor-confirmed paper output. Inspect paper before issuing a new test after an uncertain result.

The agent is not able to power on the Windows computer, sign in to Windows, pair hardware, replace paper or repair a driver remotely. These still require someone at the restaurant. An invalid/rotated private key or an agent software upgrade requires local administrator provisioning; it is deliberately not exposed in the webpage. Shared private-key rotation must be coordinated across trusted shop stations.

## Files on the shop station

The installer uses `%LOCALAPPDATA%\GokulPrint\<branch>-<station>`. It contains the isolated Python environment, agent sources, encrypted `key.dpapi`, bootstrap `config.local.json`, bounded diagnostic logs and the durable SQLite journal/command receipts. The task is named `GokulPrint-<branch>-<station>`. The scheduled task ignores duplicate starts, and the agent also holds an exclusive profile lock.

Only the backend origin and installed identity remain local. Connection settings are read from the saved Admin profile. A fresh installation starts paused; the chosen Start/Pause setting persists across restarts and deployments. Existing agents at stations without a managed control keep their existing behavior. Stop legacy foreground agents before installing a managed station, reconcile any claims, and never run a second computer with the same identity. The installer does not migrate a legacy journal automatically.

## Supported output and actual hardware acceptance

English/ASCII ESC/POS KOTs, 58 mm (32 columns) and 80 mm (48 columns), weighted quantities, scheduled pickup/delivery windows and optional cutting are supported. Non-ASCII names are rejected rather than silently lost. Invoice/BILLING output, native Android-only printing and other languages are separate work. Server success means transport acceptance and acknowledgement, not a paper sensor reading.

Before production, test the real USB/COM driver and model, then validate: 80 mm long names; weighted quantities; cutter off/on as appropriate; offline reconnect; Pause while a ticket finishes; browser closed; Windows sign-out/sign-in and automatic startup; paper/connection failure; pending recovery; and no duplicate KOT after an interrupted acknowledgement. Software tests cannot establish these physical guarantees without the printer.

## Manual technician fallback

The original command-line tools remain available for diagnosis: `devices`, `validate`, `preview`, `test`, `registration-sql`, `run`, `resolve printed`, and `resolve retry`. `managed` runs the Admin-controlled loop. Put `--config <path>` before the command when overriding the default profile. Never run foreground commands concurrently with the installed task. Normal restaurant operation uses the Admin controls above.
