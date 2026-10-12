"""Disposable revision DB only: catalogue/auth SQL, then real admin setup -> generation -> cart -> checkout."""
import base64
import concurrent.futures
import datetime
import json
import urllib.error
import urllib.request
import uuid

BASE = "http://127.0.0.1:18409"


def call(path, body=None, method=None, user=None, base=BASE):
    headers = {"Content-Type": "application/json", "Idempotency-Key": str(uuid.uuid4())}
    if user:
        headers["Authorization"] = "Basic " + base64.b64encode(f"{user}:test-only".encode()).decode()
    request = urllib.request.Request(base + path, data=json.dumps(body).encode() if body is not None else None,
                                     headers=headers, method=method or ("POST" if body is not None else "GET"))
    try:
        response = urllib.request.urlopen(request, timeout=30)
    except urllib.error.HTTPError as error:
        response = error
    data = response.read()
    return response.status, json.loads(data) if data else None


def expect(status, result):
    assert result[0] == status, result
    return result[1]


def admin(path, body=None, method=None):
    return expect(200, call(path, body, method, "revision-owner"))


today = datetime.date.fromisoformat(expect(200, call("/api/storefront/features"))["today"])
off = expect(200, call("/api/storefront/features", base="http://127.0.0.1:18410"))
assert all(off[name] is False for name in ("smartAvailability", "smartPickupSelection", "inventoryAutomationV2", "customerHomeV2", "homepageCampaigns"))
day = (today + datetime.timedelta(days=1)).isoformat()
later = (today + datetime.timedelta(days=2)).isoformat()
last = (today + datetime.timedelta(days=3)).isoformat()
policies = {}
for product in range(2001, 2006):
    weighted = product in (2002, 2004)
    policies[product] = {"controlMode": "DAILY_PRODUCTION", "inventoryUnit": "GRAM" if weighted else "PIECE",
                        "onlineEnabled": True, "readyStockRequired": product == 2003, "defaultSafetyBuffer": 100 if weighted else 0,
                        "maximumDailyAllocation": 1000 if weighted else 20, "bookingHorizonDays": 3,
                        "productionLeadMinutes": 600}
    admin(f"/api/admin/inventory/policies/{product}", policies[product], "PUT")
    admin(f"/api/admin/inventory/branches/2001/automation/rules/{product}",
          {"automationMode": "CREATE_DRAFT" if product == 2004 else "AUTO_APPROVE_GUARANTEED",
           "guaranteedQuantity": 1000 if weighted else 10, "forecastEnabled": False, "lookbackWeeks": 8,
           "minimumHistoryDays": 3, "demandMultiplier": 1, "maximumSuggestedQuantity": None,
           "availableDaysMask": 127, "seasonalMode": "ALWAYS", "generationHorizonDays": 3, "active": True, "windows": []}, "PUT")
# Explicit manual allocation must survive automated refresh.
admin(f"/api/admin/inventory/allocations/2005/{day}",
      {"approvedQuantity": 7, "safetyBufferQuantity": 0, "expectedReadyAt": day + "T15:00:00"}, "PUT")
generated = admin("/api/admin/inventory/branches/2001/automation/generate", {"fromDate": today.isoformat(), "throughDate": last})
assert generated["createdCount"] == 19 and generated["skippedCount"] == 1, generated
explanation = admin(f"/api/admin/inventory/branches/2001/automation/runs/{generated['id']}/explanation")
assert any(x["outcome"] == "SKIPPED" and "manually" in x["message"] for x in explanation), explanation
expect(403, call(f"/api/admin/inventory/branches/2002/automation/runs/{generated['id']}/explanation", user="revision-clerk"))
expect(401, call("/api/admin/homepage-campaigns"))
expect(403, call("/api/admin/homepage-campaigns", user="revision-clerk"))
allocations = admin(f"/api/admin/inventory/allocations?branchId=2001&serviceDate={day}")
assert len(allocations) == 5
slots = admin("/api/admin/branches/2001/pickup-slots", {
    "startDate": today.isoformat(), "endDate": last, "startTime": "09:00", "endTime": "21:00",
    "slotDurationMinutes": 30, "capacity": 20, "priorityEnabled": False, "priorityCapacity": 0, "priorityCharge": 0})
menu = expect(200, call("/api/menu?branchId=2001"))
assert {category["name"] for category in menu} >= {"Sweets", "Snacks"}, menu


def preview(items, start=day, days=2):
    return expect(200, call("/api/branches/2001/availability", {"startDate": start, "days": days, "items": items}))


mixed = [{"productId": 2001, "quantity": 2}, {"productId": 2002, "weightGrams": 500}]
result = preview(mixed)
assert all(date["available"] for date in result["dates"]), result
too_much = preview([{"productId": 2001, "quantity": 11}, {"productId": 2002, "weightGrams": 950}])
assert {item["code"] for item in too_much["dates"][0]["items"]} == {"QUANTITY_TOO_LARGE"}, too_much
for product, code in [(2003, "READY_STOCK_REQUIRED"), (2004, "AWAITING_APPROVAL")]:
    item = {"productId": product, **({"weightGrams": 250} if product == 2004 else {"quantity": 1})}
    checked = preview(mixed + [item])
    assert not checked["dates"][0]["available"]
    assert next(x for x in checked["dates"][0]["items"] if x["productId"] == product)["code"] == code, checked
manual = preview([{"productId": 2005, "quantity": 1}])
assert manual["dates"][0]["items"][0]["availableQuantity"] == 7
assert manual["dates"][0]["slots"][0]["issues"][0]["code"] == "NOT_READY"
assert any(s["normalAvailable"] for s in manual["dates"][0]["slots"]), manual
early = manual["dates"][0]["slots"][0]["slot"]["id"]
legacy_order = {"branchId": 2001, "pickupSlotId": early, "pickupType": "NORMAL",
                "customerName": "Synthetic baseline", "customerPhone": "9000000000", "items": [{"productId": 2005, "quantity": 1}]}
expect(409, call("/api/orders", legacy_order))
expect(201, call("/api/orders", legacy_order, base="http://127.0.0.1:18410"))
expect(409, call("/api/orders", {**legacy_order, "items": [{"productId": 2004, "weightGrams": 250}]}, base="http://127.0.0.1:18410"))
expect(404, call("/api/branches/2001/availability", {"startDate": day, "days": 1, "items": mixed}, base="http://127.0.0.1:18410"))
admin(f"/api/admin/inventory/allocations/2003/{day}/readiness", {"status": "READY", "readyQuantity": 10}, "PATCH")
assert preview([{"productId": 2003, "quantity": 1}])["dates"][0]["available"]
slot = next(s["slot"] for s in result["dates"][0]["slots"] if s["normalAvailable"])


def order():
    return call("/api/orders", {"branchId": 2001, "pickupSlotId": slot["id"], "pickupType": "NORMAL",
                               "customerName": "Synthetic Revision", "customerPhone": "9000000000", "items": mixed})


with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
    race = list(pool.map(lambda _: order(), range(8)))
assert sum(status == 201 for status, _ in race) == 1, race  # 900 g net stock, 500 g each
assert all(status in (201, 409) for status, _ in race), race
order_number = next(body["orderNumber"] for status, body in race if status == 201)
offer_body = {"code": "REVISION10", "name": "Revision discount", "description": "Synthetic test",
              "scope": "GENERAL", "visibility": "PUBLIC", "rebateType": "PERCENTAGE",
              "rebateValue": 10, "minimumOrderAmount": 1, "maximumDiscountAmount": 50,
              "maxTotalUses": 10, "maxUsesPerCustomer": 1, "branchId": 2001,
              "validFrom": today.isoformat() + "T00:00:00", "validUntil": last + "T23:59:00",
              "customerPhones": [], "slabs": []}
offer = expect(201, call("/api/admin/rebates", offer_body, user="revision-offers"))
expect(403, call("/api/admin/rebates", {**offer_body, "code": "DENIED", "branchId": 2002}, user="revision-offers"))
expect(403, call("/api/admin/rebates", {**offer_body, "code": "DENIEDALL", "branchId": None}, user="revision-offers"))
expect(403, call("/api/admin/rebates", user="revision-clerk"))
expect(400, call("/api/admin/rebates", {**offer_body, "code": "BADCAP", "maximumDiscountAmount": None}, user="revision-owner"))
available_offers = expect(200, call(f"/api/orders/{order_number}/available-rebates"))
assert any(value["code"] == "REVISION10" for value in available_offers), available_offers
applied = expect(200, call(f"/api/orders/{order_number}/rebate", {"code": "REVISION10"}))
assert applied["rebateAmount"] > 0 and applied["totalAmount"] < applied["amountBeforeRebate"], applied
expect(200, call(f"/api/orders/{order_number}/rebate", method="DELETE"))
admin(f"/api/admin/rebates/{offer['id']}/deactivate", method="PATCH")
assert not any(value["code"] == "REVISION10" for value in expect(200, call(f"/api/orders/{order_number}/available-rebates")))
updated = admin(f"/api/admin/rebates/{offer['id']}", {**offer_body, "name": "Edited discount"}, "PUT")
assert not updated["active"], updated
admin(f"/api/admin/rebates/{offer['id']}/activate", method="PATCH")
for suffix, extra in [
    ("FUTURE", {"validFrom": later + "T00:00:00"}),
    ("OTHERBRANCH", {"branchId": 2002}),
    ("TARGETED", {"scope": "CUSTOMER", "customerPhones": ["9111111111"]}),
    ("CODEONLY", {"visibility": "CODE_ONLY"}),
    ("EXPIRED", {"validFrom": (today - datetime.timedelta(days=2)).isoformat() + "T00:00:00",
                 "validUntil": (today - datetime.timedelta(days=1)).isoformat() + "T00:00:00"})
]:
    expect(201, call("/api/admin/rebates", {**offer_body, "code": suffix, **extra}, user="revision-owner"))
assert [value["code"] for value in expect(200, call(f"/api/orders/{order_number}/available-rebates"))] == ["REVISION10"]
print("PASS: existing rebate create/edit/activate/deactivate, real customer eligibility/application/removal, branch permissions, schedule/customer/code-only filters.")
after = preview(mixed)
assert not after["dates"][0]["available"] and after["dates"][1]["available"], after
fresh = admin("/api/admin/inventory/branches/2001/automation/generate", {"fromDate": today.isoformat(), "throughDate": last})
assert fresh["skippedCount"] >= 4, fresh  # manual, ready, held stock must remain untouched
assert not preview(mixed)["dates"][0]["available"]
out_of_window = preview(mixed, start=(today + datetime.timedelta(days=4)).isoformat(), days=1)
assert all(item["code"] == "PRODUCT_HORIZON" for item in out_of_window["dates"][0]["items"]), out_of_window
no_slots_date = (today + datetime.timedelta(days=3)).isoformat()
for slot_to_remove in [slot for slot in slots if slot["slotDate"] == no_slots_date]:
    # Existing admin deletion contract; no booked slots on this synthetic last date.
    response = call(f"/api/admin/pickup-slots/{slot_to_remove['id']}", method="DELETE", user="revision-owner")
    assert response[0] in (200, 204), response
without_slots = preview(mixed, start=no_slots_date, days=1)["dates"][0]
assert not without_slots["available"] and all(item["code"] == "NO_SLOTS" for item in without_slots["items"]), without_slots
print("PASS: real admin policies/rules/generation/menu; mixed category piece+weight; exact item reasons; guaranteed vs ready/draft/manual;")
print("expected readiness, horizon, all-cart alternatives, buffer math, held-stock refresh protection, 8-way final atomic stock race.")
