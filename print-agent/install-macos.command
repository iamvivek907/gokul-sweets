#!/bin/bash
set -e
cd -- "$(dirname -- "$0")"
if ! command -v python3 >/dev/null 2>&1; then
    echo 'Install Python 3.12 or later from https://www.python.org/downloads/macos/ then run this installer again.'
    read -r -p 'Press Return to close.'
    exit 1
fi
if ! python3 -c 'import sys; assert sys.version_info >= (3, 12)'; then
    echo 'Python 3.12 or later is required. Install the macOS universal installer from python.org.'
    read -r -p 'Press Return to close.'
    exit 1
fi
python3 macos.py --install
read -r -p 'Press Return to close.'
