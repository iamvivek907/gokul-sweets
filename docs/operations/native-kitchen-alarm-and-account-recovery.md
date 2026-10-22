# PR 210: shop sound and customer accounts

OTP login now keeps one internal account for the same provider-verified phone in each environment. Each device receives its own session; signing out revokes only that session. Reverification with an existing cookie replaces that device's token. Proof replay protection and session expiry remain enforced.

V129 reconnects past verified orders using the recorded PHONE_REVERIFICATION chain. It never claims guest orders by matching their typed phone. The immutable coin ledger, lots, reservations and expiry dates are preserved. Read projections expose past coin events under the current account and prevent credit/welcome/reversal replay. Old revoked sessions, consent, addresses and push endpoints are not migrated. Existing optional consent on the stable account remains valid on later logins. Previously earned coins that have legitimately expired remain expired.

## Windows shop computer

Deploy the backend and frontend from this PR. In Admin → Printing → Set up printer, register the KITCHEN station, download the Windows installer and profile, and install as the shop's Windows account. Existing stations can run the updated installer with the same profile/account to upgrade; it preserves the encrypted key, configuration and print journal. Pause printing and resolve any pending physical ticket before upgrading.

Use **Test computer sound**, confirm the speakers are audible, then **Enable computer alarm**. These controls require branch management permission. Sound is independent of printing mode and of browser login, tabs, refresh and page closure. The Windows task starts at shop-account sign-in; the alarm loop prevents automatic computer sleep while enabled. It does not run before that account signs in. Only registered KITCHEN agents can poll the private endpoint using the existing backend agent key.

The alarm polls the same preparation-board calculation every five seconds. It repeats for confirmed orders eligible/overdue for preparation, and for overdue preparing orders needing Ready. Reading a notification or receiving an alert does not complete an order. Normal KOT/Ready actions clear the relevant due condition. Printing delays, disconnected printers and uncertain KOT journals do not block the independent alarm loop.

A distinct lower tone reports a failed order check while enabled, including after a process restart. Cached mode is scoped to backend, branch and installed agent. A verified zero-due response stops the order tone. Explicit disable stops it after the next successful poll; an offline computer cannot receive a remote disable until it reconnects. Rejected credentials or removed registration clear local enabled state. Sound tests expire after one minute and their receipt records Windows API acceptance, not proof of audible speakers.

Admin shows alarm contact time, offline/error state and test receipt separately from printing health. A process crash, powered-off computer or disconnected speakers still requires operator intervention; this PR does not configure an external monitoring provider, SMS escalation or an Android native companion. Staff phone/PWA notifications and browser alarm recovery remain available, with browser/OS background restrictions. Use the computer as the primary branch alarm, and test with real DEV orders after installation.

## Verification

Coverage includes stable and concurrent fresh-device OTP logins; single-device revocation; audited multi-generation migration with unchanged ledger/lots/holds and environment isolation; recovered coin balance/expiry and replay/refund behavior; native agent interruption/restart/revocation/tests; and Admin controls/permissions/offline state at phone and desktop widths. Physical Windows speaker output and task restart need a DEV device check.

## MacBook / macOS installation

Admin provides **Download macOS alarm installer** beside the Windows download. It supports Apple Silicon Macs and plays kitchen alarms; it does not print tickets. Deploy the backend/frontend first, then save/download a KITCHEN station profile. If there is no printer, register a kitchen profile with USB target `Alarm only` and leave printing paused; that target is not used by sound. Use one primary computer per registered KITCHEN profile, rather than sharing its identity between a running Mac and Windows computer.

1. The installer reuses a working Python 3.12+ runtime. If none is found, it downloads the pinned official Python 3.13.16 universal installer, checks its published SHA-256 and Python Software Foundation package signature, then installs it. macOS asks for your administrator password only for Python installation; alarm/Keychain/startup setup continues as your normal account. Source: https://www.python.org/downloads/release/python-31316/. Failed verification or declined installation stops setup before creating an alarm service.
2. Extract the ZIP. In Terminal type `bash `, drag `install-macos.command` into Terminal, then press Return. This works even if Finder does not retain executable permission.
3. Drag the downloaded `config.local.json` into Terminal when prompted. Enter the backend `PRINT_AGENT_API_KEY` at the hidden prompt. It is stored in the installing account's login Keychain, never in the profile or startup plist. Allow Gokul credential access if macOS prompts.
4. In Admin select **Test computer sound**, confirm it is audible, then **Enable computer alarm**. Check alarm contact separately from printer status: printing remains offline on an alarm-only Mac.

The per-user LaunchAgent restarts the process and starts at sign-in. Idle sleep is prevented while it runs, including while watching for new orders. The installer exports macOS system root certificates for Python HTTPS checks; it never disables TLS certificate validation. Keep the Mac plugged in, signed in, lid open, speakers audible and login Keychain accessible. Lid closure, explicit sleep, logout, shutdown and muted speakers still interrupt sound; no external manager escalation is configured.

For upgrades run the installer/profile from the same account. It stops the old LaunchAgent before replacing files and preserves Keychain credentials and cached mode. To uninstall, disable sound in Admin, run `launchctl bootout gui/$(id -u)/in.gokulsweets.alarm.BRANCH_ID` with your branch number, then remove that branch's plist from `~/Library/LaunchAgents` and folder from `~/Library/Application Support/GokulAlarm`. Remove the credential separately in Keychain Access if desired.

## Upgrade failure recovery

An exclusive installation lock covers Windows first-time setup and upgrades, and Mac setup and upgrades. It prevents overlapping installers from mixing backups or interrupting rollback. A second installer reports that setup is already running; wait for the first to finish and retry. The operating system releases the lock if an installer crashes; do not delete the lock file. Both installers stage and validate new files and back up the existing code before stopping the running agent. Replacement or activation failures restore the previous files and restart the previous task/LaunchAgent. Keys, Windows print journals and alarm caches are not replaced by an upgrade. If rollback itself fails, the installer reports the retained recovery-folder path for manual repair rather than discarding the backup. Mac audio stays in the LaunchAgent process group, stops on normal shutdown and checks that its parent still exists before repeating; crash recovery cannot leave a separate repeating alarm loop behind.

## Choose the order alarm sound

In Admin → Printing → Set up printer → KITCHEN, select Kitchen buzzer, Bell or Repeating chime and choose Save sound. Test computer sound plays the saved selection on the installed computer. Sound settings are per branch and survive webpage refreshes and agent restarts; they do not enable/disable sound or complete any orders. The agent picks up a changed selection on its next successful check. Network failures keep the separate lower warning tone.

Existing Windows and Mac agents must be upgraded once using the new installer and the same profile/account to support these choices. Admin shows an upgrade notice until a sound-capable agent reports. Older agents remain compatible and use their original fixed tone; deploy the backend and frontend before upgrading the agent. Mac users run the new extracted install-macos.command with their saved KITCHEN profile. Existing Keychain credentials are reused. After that upgrade, changing the sound in Admin does not require another installation.

## Stop the macOS alarm locally

The extracted installer includes **disable-alert-macos.command**. Double-click it in Terminal, or run `bash disable-alert-macos.command`. It stops all Gokul alarm LaunchAgents installed for the current Mac account and disables their automatic startup at sign-in. It works while DEV or the database is unavailable, preserves installed profiles and Keychain credentials, and leaves other applications alone. Running it again is safe when the alarm is already stopped. It prints the exact `launchctl enable` and `launchctl bootstrap` commands to restore each installed branch later. Physical macOS stop/start verification remains a device check; automated tests use fake launchctl responses.
