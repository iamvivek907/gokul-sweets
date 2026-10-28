"""Complete-preview deadline regressions without live service or database traffic."""
import asyncio
import contextlib
import datetime
import importlib.util
import io
import json
import pathlib
import types
import unittest
from unittest.mock import AsyncMock, Mock, patch

spec = importlib.util.spec_from_file_location('workload', pathlib.Path(__file__).with_name('memory-workload.py'))
workload = importlib.util.module_from_spec(spec)
spec.loader.exec_module(workload)


class PreviewDeadlineTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        date_patch = patch.object(workload, "SERVICE_DATE", datetime.date(2026, 10, 30))
        date_patch.start()
        self.addCleanup(date_patch.stop)
        for name, value in [('PREVIEW_DEADLINE_SECONDS', .2),
                            ('MENU_BATCHES', [[dict(productId=i, quantity=1)] for i in (1, 2, 3)])]:
            replacement = patch.object(workload, name, value)
            replacement.start()
            self.addCleanup(replacement.stop)

    async def test_one_budget_cancels_later_batches_and_reports_timeout_as_failure(self):
        calls, cancelled = [], []
        async def response(path, body):
            calls.append(body['items'][0]['productId'])
            try:
                await asyncio.sleep(.12)
            except asyncio.CancelledError:
                cancelled.append(calls[-1])
                raise
            return dict(issueCatalog=[], dates=[dict(date=body['startDate'], slots=[], items=body['items'])])
        output = io.StringIO()
        with patch.object(workload, 'http_json', response), contextlib.redirect_stdout(output):
            failed = await workload.stage(1, workload.menu_preview, 1, 'full-menu-selected-date-preview')
        report = json.loads(output.getvalue())
        self.assertTrue(failed)
        self.assertEqual(calls, [1, 2])
        self.assertEqual(cancelled, [2])
        self.assertEqual(report['completed'], 0)
        self.assertEqual(report['errors'], 1)
        self.assertEqual(report['deadline_exceeded'], 1)
        self.assertIsNone(report['successful_p95_seconds'])

    async def test_closed_connection_error_does_not_hide_preview_deadline(self):
        writer = types.SimpleNamespace(write=lambda _: None, drain=AsyncMock(),
                                       close=Mock(), wait_closed=AsyncMock(side_effect=ConnectionResetError()))
        async def read():
            await asyncio.Event().wait()
        reader = types.SimpleNamespace(read=read)
        with patch.object(workload.asyncio, 'open_connection', AsyncMock(return_value=(reader, writer))):
            with self.assertRaises(TimeoutError):
                await workload.menu_preview(0, 0)
        writer.close.assert_called_once()
        writer.wait_closed.assert_awaited_once()

    async def test_synchronous_overrun_is_not_reported_as_success(self):
        clock = iter([0, .25])
        with patch.object(workload, 'menu_preview_batches', AsyncMock()), \
                patch.object(workload, 'time', types.SimpleNamespace(monotonic=lambda: next(clock))):
            with self.assertRaises(TimeoutError):
                await workload.menu_preview(0, 0)

class VariedScopeTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        date_patch = patch.object(workload, "SERVICE_DATE", datetime.date(2026, 10, 30))
        date_patch.start()
        self.addCleanup(date_patch.stop)

    async def test_unique_quantities_dates_and_all_batches(self):
        calls = []
        async def response(path, body):
            calls.append(body)
            return dict(issueCatalog=[], dates=[dict(date=body['startDate'], slots=[], items=body['items'])])
        with patch.object(workload, 'MENU_BATCHES', [[dict(productId=i, quantity=1)] for i in (1, 2)]), patch.object(workload, 'http_json', response):
            await workload.varied_preview(0, 0)
            await workload.varied_preview(8, 0)
        self.assertEqual([c['items'][0]['quantity'] for c in calls], [1, 1, 9, 9])
        self.assertNotEqual(calls[0]['startDate'], calls[2]['startDate'])

    async def test_capacity_misses_are_reported_but_data_errors_fail(self):
        output = io.StringIO()
        with patch.object(workload, 'varied_preview', AsyncMock(side_effect=TimeoutError)), contextlib.redirect_stdout(output):
            # patched identity is still the action recognized by stage
            failed = await workload.stage(1, workload.varied_preview, 1, 'varied-capacity')
        report = json.loads(output.getvalue())
        self.assertFalse(failed)
        self.assertEqual(report['deadline_exceeded'], 1)
        self.assertEqual(report['completed'], 0)
        with patch.object(workload, 'varied_preview', AsyncMock(side_effect=ValueError('wrong scope'))), contextlib.redirect_stdout(io.StringIO()):
            self.assertTrue(await workload.stage(1, workload.varied_preview, 1, 'varied-capacity'))


class FixtureDateTest(unittest.TestCase):
    def test_explicit_seed_date_survives_a_midnight_rollover(self):
        with patch.dict(workload.os.environ, {"GOKUL_CI_SERVICE_DATE": "2026-10-30"}):
            self.assertEqual(workload.fixture_service_date(), datetime.date(2026, 10, 30))
            # No process-start or runner-local date is used as the fixture's identity.
            with patch.object(workload.datetime, "datetime", Mock(side_effect=AssertionError("wall clock read"))):
                self.assertEqual(workload.fixture_service_date(), datetime.date(2026, 10, 30))

    def test_missing_or_invalid_seed_dates_fail_before_traffic(self):
        with patch.dict(workload.os.environ, {}, clear=True):
            with self.assertRaisesRegex(ValueError, "GOKUL_CI_SERVICE_DATE"):
                workload.fixture_service_date()
        with patch.dict(workload.os.environ, {"GOKUL_CI_SERVICE_DATE": "not-a-date"}):
            with self.assertRaises(ValueError):
                workload.fixture_service_date()


class AnchoredRequestsTest(unittest.IsolatedAsyncioTestCase):
    async def test_inventory_gap_and_suggestion_use_the_seed_anchor(self):
        anchor = datetime.date(2026, 10, 30)
        api = AsyncMock(return_value={"suggestedServiceDate": "2026-10-31", "orderable": False})
        with patch.object(workload, "SERVICE_DATE", anchor), patch.object(workload, "http_json", api):
            await workload.request(1, 1)
        self.assertEqual(api.await_args.args[1]["serviceDate"], "2026-10-30")

    async def test_discovery_uses_the_same_anchor(self):
        api = AsyncMock(return_value={"dates": [{"date": "2026-10-30"}]})
        with patch.object(workload, "SERVICE_DATE", datetime.date(2026, 10, 30)), patch.object(workload, "http_json", api):
            await workload.discovery(0, 0)
        self.assertIn("startDate=2026-10-30&days=31", api.await_args.args[0])


if __name__ == '__main__':
    unittest.main()
