"""Complete-preview deadline regressions without live service or database traffic."""
import asyncio
import contextlib
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


if __name__ == '__main__':
    unittest.main()
