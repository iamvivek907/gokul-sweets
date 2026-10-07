"""Compare complete event ranges; unknown ranges run frontend checks safely."""
import json
import os
import re
import subprocess
from pathlib import Path


def affects_frontend(path):
    return path.startswith(("frontend/", "tools/")) or path in {
        # Only frontend/shared CI inputs trigger the expensive frontend jobs.
        ".github/workflows/verify.yml",
        ".github/scripts/frontend-changes.py",
        ".github/scripts/test_frontend_changes.py",
        ".github/scripts/browser-suites.json",
        ".github/scripts/run-browser-suite.mjs",
        "vercel.json", "package.json", "package-lock.json", ".npmrc",
        ".nvmrc", ".node-version",
    }


def changed_paths(event_name, event):
    if event_name == "pull_request":
        base = event["pull_request"]["base"]["sha"]
        head = event["pull_request"]["head"]["sha"]
        comparison = "merge-base"
    elif event_name == "push":
        base, head = event.get("before", ""), event.get("after", "")
        comparison = "direct"
    else:
        return None
    if not all(re.fullmatch(r"[0-9a-f]{40}", sha) and sha != "0" * 40 for sha in (base, head)):
        return None
    # A force push may remove the old commit from the checkout. Never silently
    # skip verification when either endpoint is missing.
    for sha in (base, head):
        if subprocess.run(["git", "cat-file", "-e", f"{sha}^{{commit}}"], capture_output=True).returncode:
            return None
    if comparison == "merge-base":
        base = subprocess.check_output(["git", "merge-base", base, head], text=True).strip()
    # Disable rename detection so both old/new paths affect the decision.
    raw = subprocess.check_output(["git", "diff", "--name-only", "--no-renames", "-z", base, head])
    return raw.decode("utf-8", errors="surrogateescape").strip("\0").split("\0") if raw else []


def main():
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text())
    paths = changed_paths(os.environ["GITHUB_EVENT_NAME"], event)
    changed = paths is None or any(affects_frontend(path) for path in paths)
    value = str(changed).lower()
    print(f"Frontend inputs changed: {value}; compared {len(paths) if paths is not None else 'unknown'} paths")
    with open(os.environ["GITHUB_OUTPUT"], "a") as output:
        output.write(f"frontend={value}\n")


if __name__ == "__main__":
    main()
