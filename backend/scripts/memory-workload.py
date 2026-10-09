"""Disposable localhost CI traffic only. Synchronized async clients avoid thread-startup skew."""
import asyncio
import datetime
import http.client
import io
import json
import os
import statistics
import time
import urllib.request
from zoneinfo import ZoneInfo

BASE = 'http://127.0.0.1:10000'
ITEMS, MENU_ITEMS, MENU_BATCHES = [], [], []
TODAY = datetime.datetime.now(ZoneInfo('Asia/Kolkata')).date()
PREVIEW_DEADLINE_SECONDS = 8
PREVIEW_USERS = tuple(int(value) for value in os.environ.get('GOKUL_CI_PREVIEW_USERS', '100,500').split(','))


def load_fixture():
    global ITEMS, MENU_ITEMS, MENU_BATCHES
    with urllib.request.urlopen(BASE + '/api/menu?branchId=10001') as response:
        menu = json.load(response)
    MENU_ITEMS = [dict(productId=p['id'], quantity=1) for category in menu for p in category['products']]
    ITEMS = MENU_ITEMS[:5]
    MENU_BATCHES = [MENU_ITEMS[offset:offset + 100] for offset in range(0, len(MENU_ITEMS), 100)]


async def http_json(path, payload=None, headers=None):
    writer = None
    try:
        async with asyncio.timeout(30):
            reader, writer = await asyncio.open_connection('127.0.0.1', 10000)
            body = json.dumps(payload).encode() if payload is not None else b''
            fields = {'Host': '127.0.0.1:10000', 'Connection': 'close', **(headers or {})}
            if payload is not None:
                fields.update({'Content-Type': 'application/json', 'Content-Length': str(len(body))})
            head = ('POST' if payload is not None else 'GET') + ' ' + path + ' HTTP/1.1\r\n'
            head += ''.join(key + ': ' + value + '\r\n' for key, value in fields.items()) + '\r\n'
            writer.write(head.encode() + body)
            await writer.drain()
            raw = await reader.read()
            class Socket:
                def makefile(self, mode):
                    return io.BytesIO(raw)
            response = http.client.HTTPResponse(Socket())
            response.begin()
            if response.status != 200:
                raise ValueError('HTTP ' + str(response.status) + ': ' + response.read().decode()[:200])
            return json.loads(response.read())
    finally:
        if writer is not None:
            writer.close()
            try:
                async with asyncio.timeout(1):
                    await writer.wait_closed()
            except (TimeoutError, ConnectionError, OSError):
                pass  # Closing a cancelled response must not replace the original deadline error.


async def request(user, iteration):
    staff = user % 5 == 0
    headers, payload = {}, None
    if staff:
        path = '/api/admin/orders/queue/counts?branchId=10001' if iteration % 2 else '/api/admin/orders?branchId=10001&page=0&size=20'
        headers = {'Cookie': 'gokul_staff=memory_ci_' + str(user % 100 + 1).zfill(33)}
    elif iteration % 3 == 1:
        path = '/api/branches/10001/inventory/check'
        payload = dict(serviceDate=TODAY.isoformat(), items=ITEMS)
    elif iteration % 3 == 2:
        path = '/api/branches/10001/availability'
        payload = dict(startDate=(TODAY + datetime.timedelta(days=1)).isoformat(), days=7, items=ITEMS)
    else:
        path = '/api/menu?branchId=10001&view=availability'
    data = await http_json(path, payload, headers)
    if not staff and payload is None and len(data.get('items', [])) != 379:
        raise ValueError('Incomplete menu availability')
    if path.endswith('/inventory/check'):
        expected = (TODAY + datetime.timedelta(days=1)).isoformat()
        actual = data.get('suggestedServiceDate', data.get('suggestedDate'))
        if actual != expected:
            raise ValueError(f'Incorrect inventory date suggestion: expected {expected}, got {actual}; orderable={data.get("orderable")}')
    if path.endswith('/availability') and not data.get('dates'):
        raise ValueError('Missing pickup availability dates')


async def discovery(user, iteration):
    result = await http_json('/api/branches/10001/pickup-discovery?startDate=' + TODAY.isoformat() + '&days=31')
    if not result.get('dates') or any(day.get('items') for day in result['dates']):
        raise ValueError('Expected lightweight dates without item inventory matrix')


async def menu_preview_batches(user, iteration):
    """Match the browser's full-menu, selected-date preview and sequential 100-item batches."""
    date = (TODAY + datetime.timedelta(days=1)).isoformat()
    for batch in MENU_BATCHES:
        result = await http_json('/api/branches/10001/availability?menuPreview=true&compact=true',
                                 dict(startDate=date, days=1, items=batch, fulfilmentType='PICKUP'))
        dates = result.get('dates', [])
        catalog = result.get('issueCatalog')
        if catalog is None:
            raise ValueError('Expected opt-in compact menu preview')
        for day in dates:
            for slot in day['slots']:
                if any(not isinstance(index, int) or index < 0 or index >= len(catalog)
                       for index in slot['issueIndexes']):
                    raise ValueError('Invalid menu preview issue index')
        expected = {item['productId'] for item in batch}
        if len(dates) != 1 or dates[0]['date'] != date or {item['productId'] for item in dates[0].get('items', [])} != expected:
            raise ValueError('Full-menu preview must return every requested item for the selected date')


async def menu_preview(user, iteration):
    # One deadline for the complete preview, not a fresh budget for each batch.
    # Cancellation stops later batches and closes the active HTTP connection in http_json.
    started = time.monotonic()
    async with asyncio.timeout(PREVIEW_DEADLINE_SECONDS):
        await menu_preview_batches(user, iteration)
    # Also reject overruns from synchronous JSON parsing before the timer can run.
    if time.monotonic() - started >= PREVIEW_DEADLINE_SECONDS:
        raise TimeoutError


async def stage(users, action, rounds, scenario):
    timings, successful_timings, errors = [], [], []
    active = peak = 0
    for iteration in range(rounds):
        gate = asyncio.Event()
        async def client(user):
            nonlocal active, peak
            await gate.wait()
            active += 1
            peak = max(peak, active)
            started = time.monotonic()
            try:
                await action(user, iteration)
                return time.monotonic() - started, None
            except Exception as error:
                return time.monotonic() - started, str(error) or type(error).__name__
            finally:
                active -= 1
        tasks = [asyncio.create_task(client(user)) for user in range(users)]
        await asyncio.sleep(0)  # Every client reaches the shared gate before release.
        gate.set()
        results = await asyncio.gather(*tasks)
        timings.extend(duration for duration, error in results)
        successful_timings.extend(duration for duration, error in results if not error)
        errors.extend(error for duration, error in results if error)
        if iteration + 1 < rounds:
            await asyncio.sleep(1)
    timings.sort()
    successful_timings.sort()
    print(json.dumps(dict(scenario=scenario, memory_profile=os.environ.get('GOKUL_CI_MEMORY_PROFILE', 'unspecified'),
                          cpu_limit=os.environ.get('GOKUL_CI_CPU_LIMIT') or 'runner-default',
                          users=users, peak_in_flight=peak, requests=len(timings),
                          errors=len(errors), p50_seconds=round(statistics.median(timings), 3),
                          p95_seconds=round(timings[max(0, int(len(timings)*.95)-1)], 3),
                          max_seconds=round(max(timings), 3),
                          completed=len(successful_timings),
                          successful_p95_seconds=round(successful_timings[max(0, int(len(successful_timings)*.95)-1)], 3) if successful_timings else None,
                          deadline_seconds=PREVIEW_DEADLINE_SECONDS if action is menu_preview else None,
                          deadline_exceeded=sum(error == 'TimeoutError' for error in errors) if action is menu_preview else None,
                          error_samples=errors[:3])), flush=True)
    return bool(errors) or peak != users


async def main():
    load_fixture()
    failed = False
    for users in (1, 10, 50, 100, 500, 1000):
        failed |= await stage(users, request, 5, 'mixed-customer-staff')
    for users in (10, 100, 500, 1000):
        failed |= await stage(users, discovery, 1, 'lightweight-31-day-pickup-discovery')
    # Each completed preview here is four sequential POSTs for the 379-item fixture.
    # Report complete-preview latency; the earlier stages are individual HTTP request timings.
    for users in PREVIEW_USERS:
        failed |= await stage(users, menu_preview, 1, 'full-menu-selected-date-preview')
    return 1 if failed else 0


if __name__ == '__main__':
    raise SystemExit(asyncio.run(main()))
