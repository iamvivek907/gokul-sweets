import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock

from alarm import AlarmState


class AlarmTest(unittest.TestCase):
    def setUp(self):
        self.root = tempfile.TemporaryDirectory()
        self.addCleanup(self.root.cleanup)
        self.path = Path(self.root.name) / 'config.json'
        self.identity = {'branchId': 1, 'agentId': 'kitchen', 'api_url': 'https://api.example.test'}
        self.state = AlarmState(self.path, self.identity)
        self.sound = Mock()

    def snapshot(self, enabled=True, due=1, ready=0, sequence=0):
        return {'enabled': enabled, 'needsPreparation': due,
                'readyOverdue': ready, 'testSequence': sequence}

    def test_failure_cannot_silently_resolve_orders_and_restart_restores_enabled_state(self):
        self.state.update(self.snapshot(), self.sound, 100)
        self.assertEqual(self.state.status, 'RINGING')
        self.state.failed(self.sound)
        self.sound.play.assert_called_with('fault')
        restarted = AlarmState(self.path, self.identity)
        self.assertTrue(restarted.enabled)
        self.assertTrue(restarted.due)
        restarted.failed(self.sound)
        self.sound.play.assert_called_with('fault')
        restarted.update(self.snapshot(due=0), self.sound, 200)
        self.sound.play.assert_called_with(None)
        self.assertEqual(restarted.status, 'IDLE')

    def test_explicit_disable_and_credential_revocation_stop_sound(self):
        self.state.update(self.snapshot(), self.sound, 100)
        self.state.update(self.snapshot(enabled=False), self.sound, 105)
        self.sound.play.assert_called_with(None)
        self.state.failed(self.sound)
        self.sound.play.assert_called_with(None)
        self.state.update(self.snapshot(), self.sound, 110)
        self.state.failed(self.sound, forbidden=True)
        self.sound.play.assert_called_with(None)
        self.assertFalse(AlarmState(self.path, self.identity).enabled)

    def test_test_receipt_is_durable_and_repeated_response_does_not_extend_test(self):
        self.state.update(self.snapshot(due=0, sequence=1), self.sound, 100)
        self.assertEqual(self.state.status, 'TESTING')
        self.state.update(self.snapshot(due=0, sequence=1), self.sound, 111)
        self.sound.play.assert_called_with(None)
        self.assertEqual(self.state.test_receipt, 1)
        restarted = AlarmState(self.path, self.identity)
        restarted.update(self.snapshot(due=0, sequence=1), self.sound, 120)
        self.sound.play.assert_called_with(None)
        self.assertEqual(restarted.status, 'IDLE')

    def test_sound_failure_cannot_acknowledge_test(self):
        self.sound.play.side_effect = RuntimeError('speaker API failed')
        with self.assertRaises(RuntimeError):
            self.state.update(self.snapshot(sequence=2), self.sound, 100)
        self.assertEqual(self.state.test_receipt, 0)

    def test_background_interruption_reasserts_sound_and_ready_counts_ring(self):
        self.state.update(self.snapshot(due=0, ready=2), self.sound, 100)
        self.state.update(self.snapshot(due=0, ready=2), self.sound, 105)
        self.assertEqual(self.sound.play.call_count, 2)
        self.sound.play.assert_called_with('due')

    def test_cached_state_is_scoped_to_backend_branch_and_agent(self):
        self.state.update(self.snapshot(), self.sound, 100)
        for key, value in [('branchId', 2), ('agentId', 'other'), ('api_url', 'https://prod.example.test')]:
            other = dict(self.identity, **{key: value})
            self.assertFalse(AlarmState(self.path, other).enabled)

    def test_malformed_snapshot_does_not_stop_due_sound(self):
        self.state.update(self.snapshot(), self.sound, 100)
        before = self.sound.play.call_count
        for bad in [self.snapshot(due=-1), self.snapshot(due='0'), self.snapshot(enabled='false')]:
            with self.assertRaises(ValueError):
                self.state.update(bad, self.sound, 105)
        self.assertTrue(self.state.due)
        self.assertEqual(self.sound.play.call_count, before)

    def test_corrupt_cache_does_not_enable_alarm(self):
        for content in ['broken', '[]', 'null', 'false']:
            Path(str(self.path) + '.alarm.json').write_text(content)
            self.assertFalse(AlarmState(self.path, self.identity).enabled)
