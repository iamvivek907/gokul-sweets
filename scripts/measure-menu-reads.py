#!/usr/bin/env python3
"""Bounded, read-only public-menu probe; never creates a cart, order, hold or payment."""
import argparse
import datetime as dt
import json
import time
import urllib.error
import urllib.parse
import urllib.request


def request(origin, path, body=None):
    raw = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(origin + path, data=raw, headers={"Content-Type": "application/json", "User-Agent": "Gokul-menu-read-diagnostics/1"})
    started = time.monotonic()
    try:
        response = urllib.request.urlopen(req, timeout=20)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        payload = response.read(4 * 1024 * 1024)
        try:
            data = json.loads(payload)
        except (ValueError, UnicodeError):
            data = None
        return {"status": response.status, "elapsedMs": round((time.monotonic() - started) * 1000),
                "bytes": len(payload), "cacheMode": response.headers.get("X-Gokul-Menu-Preview-Cache"),
                "cacheControl": response.headers.get("Cache-Control")}, data


def measure(origin, branch, day, samples, pause):
    records = []
    def read(label, path, body=None):
        evidence, data = request(origin, path, body)
        records.append({"request": label, **evidence})
        return evidence, data
    features, flags = read("features", "/api/storefront/features")
    state, state_data = read("menu-state", f"/api/branches/{branch}/menu-state?date={day}")
    availability, menu = read("catalog availability", f"/api/menu?branchId={branch}&view=availability")
    if availability["status"] != 200 or not isinstance(menu, (dict, list)):
        return {"records": records, "effectivePreviewCache": "unknown", "reason": "Public menu could not be read"}
    categories = menu if isinstance(menu, list) else None
    if categories is None and isinstance(menu.get("revision"), str):
        _, catalog = read("versioned catalog", f"/api/menu/catalog/{branch}/{urllib.parse.quote(menu['revision'], safe='')}")
        categories = catalog.get("categories", []) if isinstance(catalog, dict) else catalog
    products = [product for category in (categories or []) for product in category.get("products", []) if product.get("available")]
    if isinstance(menu, dict):
        allowed = {row["productId"] for row in menu.get("items", []) if row.get("available")}
        products = [product for product in products if product.get("id") in allowed]
    products = products[:10]
    body = {"startDate": day, "days": 1, "fulfilmentType": "PICKUP", "items": [
        {"productId": product["id"], **({"weightGrams": product.get("minimumWeightGrams") or 250} if product.get("saleMode") == "WEIGHT" else {"quantity": 1})}
        for product in products]}
    for index in range(samples if products else 0):
        if index:
            time.sleep(pause)
        read(f"unchanged preview {index+1}", f"/api/branches/{branch}/availability?menuPreview=true&compact=true&versioned=true", body)
    modes = {row["cacheMode"] for row in records if row["cacheMode"]}
    effective = "enabled" if "enabled" in modes or state["status"] == 200 and isinstance(state_data, dict) and isinstance(state_data.get("version"), str) and bool(state_data["version"]) else "disabled" if "disabled" in modes else "unverified (version endpoint unavailable)"
    return {"observedAt": dt.datetime.now(dt.timezone.utc).isoformat(), "origin": origin, "branchId": branch, "date": day,
            "effectivePreviewCache": effective, "smartAvailability": bool(isinstance(flags, dict) and flags.get("smartAvailability") is True),
            "sampleProducts": len(products), "records": records,
            "interpretation": "Sample API latency only; not a browser page-load benchmark or proof of cache hit rate. Compare private cache lookup metrics and database statement counts."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--origin", required=True)
    parser.add_argument("--branch", type=int, default=1)
    parser.add_argument("--date", default=dt.datetime.now(dt.timezone(dt.timedelta(hours=5, minutes=30))).date().isoformat())
    parser.add_argument("--samples", type=int, choices=range(1, 6), default=3)
    parser.add_argument("--pause", type=float, default=2)
    args = parser.parse_args()
    url = urllib.parse.urlsplit(args.origin)
    if url.scheme != "https" or not url.netloc or url.username or url.password or url.path not in ("", "/") or url.query or url.fragment:
        parser.error("Use an HTTPS origin without credentials or a path")
    if args.branch < 1 or not 2 <= args.pause <= 30:
        parser.error("Positive branch and pause between 2 and 30 seconds required")
    dt.date.fromisoformat(args.date)
    print(json.dumps(measure(args.origin.rstrip("/"), args.branch, args.date, args.samples, args.pause), indent=2))


if __name__ == "__main__":
    main()
