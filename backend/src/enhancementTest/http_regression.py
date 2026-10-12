"""Run only against disposable, seeded loopback servers (ON :18309, OFF :18310).
Requires resources/seed.sql in a fresh gokul_enhancements database.
No payment gateway or object-storage calls are made.
"""
import base64
import concurrent.futures
import datetime
import json
import urllib.error
import urllib.request
import uuid

ON = "http://127.0.0.1:18309"
OFF = "http://127.0.0.1:18310"


def call(path, body=None, method=None, user=None, base=ON):
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


def expect(status, result, message):
    assert result[0] == status, (message, result)
    return result[1]

def upload(campaign_id, content, content_type):
    boundary = "synthetic-test-" + uuid.uuid4().hex
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"test.png\"\r\n"
            f"Content-Type: {content_type}\r\n\r\n").encode() + content + f"\r\n--{boundary}--\r\n".encode()
    request = urllib.request.Request(ON + f"/api/admin/homepage-campaigns/{campaign_id}/media",
                                     data=body, headers={
                                         "Content-Type": "multipart/form-data; boundary=" + boundary,
                                         "Authorization": "Basic " + base64.b64encode(b"test-owner:test-only").decode()
                                     })
    try:
        response = urllib.request.urlopen(request, timeout=30)
    except urllib.error.HTTPError as error:
        response = error
    return response.status, json.loads(response.read())


def order(product, slot, quantity=None, weight=None, base=ON):
    item = {"productId": product, "quantity": quantity, "weightGrams": weight}
    return call("/api/orders", {"branchId": 1001, "pickupSlotId": slot, "customerName": "Synthetic Test",
                               "customerPhone": "9000000000", "pickupType": "NORMAL", "items": [item]}, base=base)


today = datetime.date.fromisoformat(expect(200, call("/api/storefront/features"), "features")["today"])
tomorrow = (today + datetime.timedelta(days=1)).isoformat()
later = (today + datetime.timedelta(days=2)).isoformat()
flags = expect(200, call("/api/storefront/features", base=OFF), "default flags")
assert all(flags[name] is False for name in ["smartAvailability", "smartPickupSelection", "inventoryAutomationV2",
                                           "customerHomeV2", "homepageCampaigns"]), flags
preview = {"startDate": tomorrow, "days": 2, "items": [{"productId": 1002, "weightGrams": 250}]}
expect(404, call("/api/branches/1001/availability", preview, base=OFF), "off endpoint")
expect(200, call("/api/branches/1001/pickup-slots?date=" + tomorrow, base=OFF), "legacy slots")
expect(401, call("/api/admin/tax-categories"), "anonymous tax access")
expect(403, call(f"/api/admin/branches/1002/pickup-slots?startDate={tomorrow}&endDate={tomorrow}", user="test-manager"), "cross-branch access")
generation = {"startDate": later, "endDate": later, "startTime": "14:00", "endTime": "15:00",
              "slotDurationMinutes": 30, "capacity": 3, "priorityEnabled": True, "priorityCapacity": 1, "priorityCharge": 10}
expect(403, call("/api/admin/branches/1002/pickup-slots", generation, user="test-manager"), "cross-branch generation")
assert len(expect(200, call("/api/admin/branches/1001/pickup-slots", generation, user="test-manager"), "generate")) == 2
assert expect(200, call("/api/admin/branches/1001/pickup-slots", generation, user="test-manager"), "repeat generation") == []
tax = {"code": "TEST_NEW", "name": "Synthetic tax", "hsnSacCode": "0000", "cgstRate": 2.5, "sgstRate": 2.5, "igstRate": 5, "active": True}
saved_tax = expect(200, call("/api/admin/tax-categories", tax, user="test-owner"), "tax create")
tax["name"] = "Updated synthetic tax"
expect(200, call(f"/api/admin/tax-categories/{saved_tax['id']}", tax, method="PUT", user="test-owner"), "tax edit")
assert expect(200, call(f"/api/admin/tax-categories/{saved_tax['id']}/active", {"active": False}, method="PATCH", user="test-owner"), "tax disable")["active"] is False

available = expect(200, call("/api/branches/1001/availability", preview), "preview")
assert available["fulfilmentType"] == "PICKUP"
assert available["dates"][0]["slots"][0]["normalAvailable"] is False
assert available["dates"][0]["slots"][1]["normalAvailable"] is True
expect(400, call("/api/branches/1001/availability", {**preview, "fulfilmentType": "DELIVERY"}), "delivery not exposed")
expect(400, call("/api/branches/1001/availability", {**preview, "items": [{"productId": 1002, "weightGrams": 275}]}), "weight step")
expect(409, order(1002, 1001, weight=250), "final expected-ready validation")
expect(400, order(1001, 1005, quantity=1), "final rolling horizon")
expect(201, order(1003, 1001, weight=250, base=OFF), "flag-off original expected-ready behavior")
expect(409, order(1003, 1001, weight=250), "flag-on expected-ready behavior")
unit = expect(201, order(1001, 1003, quantity=10), "piece order")
assert unit["items"][0]["quantity"] == 10 and unit["items"][0]["taxAmount"] == 7.5 and unit["items"][0]["lineTotal"] == 157.5

with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:
    responses = list(pool.map(lambda _: order(1002, 1003, weight=250), range(12)))
winners = [body for status, body in responses if status == 201]
assert len(winners) == 2, responses
assert all(status in [201, 409] for status, _ in responses), responses
assert all(body["items"][0]["weightGrams"] == 250 and body["items"][0]["lineTotal"] == 78.75 for body in winners)
expect(409, call(f"/api/orders/{winners[0]['orderNumber']}/checkout",
                 {"pickupSlotId": 1001, "pickupType": "NORMAL", "items": [{"productId": 1002, "weightGrams": 250}]},
                 method="PUT"), "same-day owned hold cannot move before ready time")
after = expect(200, call("/api/branches/1001/availability", preview), "post-race preview")
assert after["dates"][0]["available"] is False and after["dates"][1]["available"] is True
with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
    capacity = list(pool.map(lambda _: order(1001, 1006, quantity=1), range(8)))
assert sum(status == 201 for status, _ in capacity) == 1, capacity
assert all(status in [201, 409] for status, _ in capacity), capacity

campaign = {"type": "HERO", "title": "Synthetic campaign", "active": False, "displayOrder": 1,
            "ctaLabel": "Explore", "ctaTarget": "/menu"}
saved_campaign = expect(200, call("/api/admin/homepage-campaigns", campaign, user="test-owner"), "campaign create")
expect(400, call("/api/admin/homepage-campaigns", {**campaign, "ctaTarget": "javascript:alert(1)"}, user="test-owner"), "CTA rejection")
expect(400, call(f"/api/admin/homepage-campaigns/{saved_campaign['id']}", {**campaign, "active": True},
                 method="PUT", user="test-owner"), "media required for activation")
expect(400, upload(saved_campaign["id"], b"not a real PNG image", "image/png"), "media signature rejection")
expect(413, upload(saved_campaign["id"], b"x" * (5 * 1024 * 1024 + 1), "image/png"), "multipart size limit")
assert expect(200, call("/api/storefront/campaigns", base=OFF), "campaigns off") == []

generated = expect(200, call("/api/admin/inventory/branches/1001/automation/generate",
                            {"fromDate": today.isoformat(), "throughDate": (today + datetime.timedelta(days=31)).isoformat()},
                            user="test-owner"), "rolling automation")
assert generated["createdCount"] == 31 and generated["errorCount"] == 0, generated
print("PASS: flag-off, permissions, tax, idempotent slot generation, quantity/weight tax math, readiness/horizon,")
print("12-way stock race (2/12 accepted), 8-way slot race (1/8 accepted), owned-hold transfer, alternatives, CTA/media gates, automation.")
print("Automation response:", json.dumps(generated))
