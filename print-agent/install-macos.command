#!/bin/bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
if [[ "$(uname -s)" != Darwin ]]; then
    echo 'This installer requires macOS.' >&2
    exit 1
fi
# Prefer an existing working runtime, including python.org installs outside PATH.
python_runtime=''
for candidate in /Library/Frameworks/Python.framework/Versions/{3.15,3.14,3.13,3.12}/bin/python3 /opt/homebrew/bin/python3 /usr/local/bin/python3 python3; do
    if command -v "$candidate" >/dev/null 2>&1 && "$candidate" -c 'import sys; assert sys.version_info >= (3,12)' >/dev/null 2>&1; then
        python_runtime="$candidate"
        break
    fi
done
if [[ -z "$python_runtime" ]]; then
    # Pinned official universal installer: https://www.python.org/downloads/release/python-31316/
    python_version='3.13.16'
    python_sha256='30666509020b4da0dd8bc2e773255f34d76b7bb80b66960a928d5f6daa0192d7'
    package_dir="$(mktemp -d "${TMPDIR:-/tmp}/gokul-python.XXXXXX")"
    trap 'rm -rf -- "$package_dir"' EXIT
    package_path="$package_dir/python.pkg"
    echo 'Python 3.12+ was not found. Downloading the official Python installer.'
    /usr/bin/curl --fail --location --proto '=https' --proto-redir '=https' --connect-timeout 15 --max-time 600 --retry 2 \
        "https://www.python.org/ftp/python/$python_version/python-$python_version-macos11.pkg" --output "$package_path"
    actual_sha256="$(/usr/bin/shasum -a 256 "$package_path")"
    if [[ "${actual_sha256%% *}" != "$python_sha256" ]]; then
        echo 'Python installer checksum verification failed. Nothing was installed.' >&2
        exit 1
    fi
    signature="$(/usr/sbin/pkgutil --check-signature "$package_path")"
    if ! printf '%s\n' "$signature" | /usr/bin/grep -Fq 'Developer ID Installer: Python Software Foundation'; then
        echo 'Python installer publisher verification failed. Nothing was installed.' >&2
        exit 1
    fi
    echo 'macOS requires your administrator password to install Python. The alarm itself runs as your normal account.'
    sudo /usr/sbin/installer -pkg "$package_path" -target /
    python_runtime='/Library/Frameworks/Python.framework/Versions/3.13/bin/python3'
    "$python_runtime" -c 'import sys; assert sys.version_info >= (3,12)'
fi
# Do not use sudo here: Keychain and sign-in startup belong to the staff account.
"$python_runtime" macos.py --install
read -r -p 'Press Return to close.' || true
