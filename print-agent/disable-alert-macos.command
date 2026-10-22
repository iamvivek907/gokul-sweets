#!/bin/bash
# Standalone local control: no backend connection, Python runtime or credentials required.
set -euo pipefail
if [[ "$(uname -s)" != Darwin ]]; then
    echo 'This script requires macOS.' >&2
    exit 1
fi
domain="gui/$(id -u)"
found=0
failed=0
for plist in "$HOME"/Library/LaunchAgents/in.gokulsweets.alarm.*.plist; do
    [[ -f "$plist" ]] || continue
    found=1
    label="${plist##*/}"
    label="${label%.plist}"
    if ! launchctl disable "$domain/$label"; then
        echo "Could not disable $label." >&2
        failed=1
        continue
    fi
    if ! launchctl bootout "$domain/$label" 2>/dev/null; then
        if launchctl print "$domain/$label" >/dev/null 2>&1; then
            echo "Could not stop $label. Automatic startup is disabled." >&2
            failed=1
            continue
        fi
    fi
    echo "Stopped $label and disabled automatic startup."
    echo "Configuration and Keychain credentials are preserved."
    echo "To enable later, run:"
    printf 'launchctl enable %q\n' "$domain/$label"
    printf 'launchctl bootstrap %q %q\n' "$domain" "$plist"
done
if [[ "$found" == 0 ]]; then
    echo 'No installed Gokul alarm services were found for this Mac account.'
fi
exit "$failed"
