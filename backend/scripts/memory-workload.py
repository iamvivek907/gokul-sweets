"""CI-only synthetic traffic against the disposable local database. Never targets production."""
import concurrent.futures
import json
import datetime
from zoneinfo import ZoneInfo
import statistics
import time
import urllib.request

BASE = 'http://127.0.0.1:10000'
with urllib.request.urlopen(BASE + '/api/menu?branchId=10001') as response:
    menu = json.load(response)
ITEMS = [dict(productId=p['productId'] if 'productId' in p else p['id'], quantity=1)
         for category in menu for p in category['products']][:5]
TODAY = datetime.datetime.now(ZoneInfo('Asia/Kolkata')).date()


def request(user, iteration):
    staff = user % 5 == 0
    if staff:
        path = '/api/admin/orders/queue/counts?branchId=10001' if iteration % 2 else '/api/admin/orders?branchId=10001&page=0&size=20'
        headers = {'Cookie': 'gokul_staff=memory_ci_' + str(user + 1).zfill(33)}
    else:
        path = '/api/menu?branchId=10001'
        headers = {}
    payload = None
    if not staff and iteration % 3 == 1:
        path = '/api/branches/10001/inventory/check'
        payload = json.dumps(dict(serviceDate=TODAY.isoformat(), items=ITEMS)).encode()
        headers['Content-Type'] = 'application/json'
    elif not staff and iteration % 3 == 2:
        path = '/api/branches/10001/availability'
        payload = json.dumps(dict(startDate=(TODAY + datetime.timedelta(days=1)).isoformat(), days=7, items=ITEMS)).encode()
        headers['Content-Type'] = 'application/json'
    started = time.monotonic()
    try:
        with urllib.request.urlopen(urllib.request.Request(BASE + path, data=payload, headers=headers), timeout=10) as response:
            data = json.load(response)
            if not staff and payload is None and sum(len(category['products']) for category in data) != 379:
                raise ValueError('Incomplete menu')
            if path.endswith('/inventory/check') and data.get('suggestedServiceDate', data.get('suggestedDate')) != (TODAY + datetime.timedelta(days=1)).isoformat():
                raise ValueError('Incorrect inventory date suggestion')
            if path.endswith('/availability') and not data.get('dates'):
                raise ValueError('Missing pickup availability dates')
        return time.monotonic() - started, None
    except Exception as error:
        return time.monotonic() - started, str(error)


failed = False
for users in (1, 10, 50, 100):
    timings = []
    errors = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=users) as pool:
        for iteration in range(5):
            for duration, error in pool.map(lambda user: request(user, iteration), range(users)):
                timings.append(duration)
                if error:
                    errors.append(error)
            time.sleep(1)
    timings.sort()
    p95 = timings[max(0, int(len(timings) * .95) - 1)]
    print(json.dumps(dict(users=users, requests=len(timings), errors=len(errors),
                         p50_seconds=round(statistics.median(timings), 3), p95_seconds=round(p95, 3),
                         max_seconds=round(max(timings), 3), error_samples=errors[:3])), flush=True)
    failed |= bool(errors)
raise SystemExit(1 if failed else 0)
