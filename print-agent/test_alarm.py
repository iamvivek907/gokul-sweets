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

    def test_selected_sound_applies_to_due_work_and_preview_with_distinct_fault(self):
        from alarm import SoundChoices
        player = SoundChoices()
        player.prepare(Path(self.root.name))
        player.play = Mock()
        for choice in ('BUZZER', 'BELL', 'CHIME'):
            self.state.update(dict(self.snapshot(sequence=1), soundProfile=choice), player, 100)
            self.assertEqual(player.paths['test'], player.paths['due'])
            self.assertEqual(player.paths['due'], player.profiles[choice])
            self.assertNotEqual(player.paths['fault'], player.paths['due'])
        self.state.failed(player)
        player.play.assert_called_with('fault')

    def test_old_backend_defaults_to_buzzer_and_invalid_sound_cannot_silence_work(self):
        self.state.update(self.snapshot(), self.sound, 100)
        self.sound.select.assert_called_with('BUZZER')
        with self.assertRaises(ValueError):
            self.state.update(dict(self.snapshot(due=0), soundProfile='../other'), self.sound, 101)
        self.assertTrue(self.state.due)

    def test_generated_choices_have_distinct_pcm_and_quiet_gaps_without_clipping(self):
        from alarm import SoundChoices
        import wave
        import struct
        player = SoundChoices()
        player.prepare(Path(self.root.name))
        sounds = []
        for path in player.profiles.values():
            with wave.open(str(path), 'rb') as wav:
                self.assertEqual(wav.getparams()[:4], (1, 2, 16000, 32000))
                data = wav.readframes(wav.getnframes())
            samples = struct.unpack('<32000h', data)
            self.assertLess(max(map(abs, samples)), 32767)
            self.assertGreater(max(map(abs, samples)), 10000)
            self.assertEqual(set(samples[-8000:]), {0})
            sounds.append(data)
        self.assertEqual(len(set(sounds)), 4)

class SupervisorTest(unittest.TestCase):
    def test_windows_plays_selected_sound_for_due_orders_and_test(self):
        from unittest.mock import patch
        from alarm import WindowsSound
        player = Mock(SND_FILENAME=1, SND_ASYNC=2, SND_LOOP=4, SND_NODEFAULT=8)
        with tempfile.TemporaryDirectory() as folder:
            with patch('alarm.sys.platform', 'win32'), patch.dict('sys.modules', winsound=player), patch('alarm.ctypes.windll', Mock(), create=True):
                sound = WindowsSound(Path(folder))
                sound.select('CHIME')
                for mode in ('due', 'test'):
                    sound.play(mode)
                    player.PlaySound.assert_called_with(str(sound.profiles['CHIME']), 15)
                sound.play('fault')
                player.PlaySound.assert_called_with(str(sound.profiles['FAULT']), 15)

    def test_macos_changes_loop_when_selected_sound_changes(self):
        from unittest.mock import patch
        from subprocess import TimeoutExpired
        from alarm import MacSound
        with tempfile.TemporaryDirectory() as folder:
            first, second = Mock(), Mock()
            first.poll.return_value = second.poll.return_value = None
            first.wait.side_effect = [TimeoutExpired('afplay', .15), 0]
            second.wait.side_effect = TimeoutExpired('afplay', .15)
            with patch('alarm.subprocess.Popen', side_effect=[first, second]) as popen:
                sound = MacSound(Path(folder))
                sound.play('due')
                sound.select('BELL')
                sound.play('due')
                first.terminate.assert_called_once()
                self.assertEqual(popen.call_count, 2)
                self.assertIn(str(sound.profiles['BELL']), popen.call_args.args[0])

    def test_macos_tune_change_cannot_prevent_stop_when_work_clears_or_disabled(self):
        from unittest.mock import patch
        from subprocess import TimeoutExpired
        from alarm import MacSound
        for initial_mode in ('due', 'test', 'fault'):
            for enabled in (True, False):
                with self.subTest(initial_mode=initial_mode, enabled=enabled), tempfile.TemporaryDirectory() as folder:
                    root = Path(folder)
                    process = Mock()
                    process.poll.return_value = None
                    process.wait.side_effect = [TimeoutExpired('afplay', .15), 0]
                    with patch('alarm.subprocess.Popen', return_value=process) as popen:
                        sound = MacSound(root)
                        sound.play(initial_mode)
                        state = AlarmState(root / 'config.json', {'branchId': 1})
                        state.enabled = True
                        state.update({'enabled': enabled, 'needsPreparation': 0,
                                      'readyOverdue': 0, 'testSequence': 0,
                                      'soundProfile': 'BELL'}, sound, 100)
                        process.terminate.assert_called_once()
                        self.assertIsNone(sound.process)
                        state.update({'enabled': enabled, 'needsPreparation': 0,
                                      'readyOverdue': 0, 'testSequence': 0,
                                      'soundProfile': 'BELL'}, sound, 105)
                        self.assertEqual(popen.call_count, 1)

    def test_macos_tune_change_then_revocation_stops_active_loop(self):
        from unittest.mock import patch
        from subprocess import TimeoutExpired
        from alarm import MacSound
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            process = Mock()
            process.poll.return_value = None
            process.wait.side_effect = [TimeoutExpired('afplay', .15), 0]
            with patch('alarm.subprocess.Popen', return_value=process):
                sound = MacSound(root)
                sound.play('due')
                sound.select('CHIME')
                state = AlarmState(root / 'config.json', {'branchId': 1})
                state.enabled = True
                state.failed(sound, forbidden=True)
                process.terminate.assert_called_once()
                self.assertIsNone(sound.process)

    def test_startup_lock_failure_retries_without_waiting_for_printer_loop(self):
        from unittest.mock import patch
        from alarm import supervise
        stop = Mock()
        stop.is_set.return_value = False
        stop.wait.side_effect = [False, True]
        with patch('alarm.run', side_effect=[BlockingIOError('locked'), OSError('temporary')]) as run:
            supervise({'station': 'KITCHEN'}, Path('profile.json'), stop)
        self.assertEqual(run.call_count, 2)
        stop.wait.assert_called_with(5)

    def test_macos_interrupted_playback_restarts_and_disable_stops_child_group(self):
        from unittest.mock import patch
        from alarm import MacSound
        with tempfile.TemporaryDirectory() as folder:
            first, second = Mock(), Mock()
            from subprocess import TimeoutExpired
            first.wait.side_effect = TimeoutExpired("afplay", .15)
            second.wait.side_effect = [TimeoutExpired("afplay", .15), 0]
            first.poll.side_effect = [None, 1, 1]
            second.poll.return_value = None
            with patch('alarm.subprocess.Popen', side_effect=[first, second]) as popen:
                sound = MacSound(Path(folder))
                sound.play('due')
                sound.play('due')
                self.assertEqual(popen.call_count, 1)
                sound.play('due')
                self.assertEqual(popen.call_count, 2)
                sound.play(None)
                second.terminate.assert_called_once()
                self.assertNotIn("start_new_session", popen.call_args.kwargs)
                second.wait.assert_called_with(timeout=3)
                self.assertIsNone(sound.process)

    def test_macos_immediate_audio_failure_is_not_acknowledged(self):
        from unittest.mock import patch
        from alarm import MacSound
        with tempfile.TemporaryDirectory() as folder:
            process = Mock()
            process.wait.return_value = 1
            with patch('alarm.subprocess.Popen', return_value=process):
                sound = MacSound(Path(folder))
                with self.assertRaisesRegex(RuntimeError, 'playback'):
                    sound.play('test')

class MacCrashTest(unittest.TestCase):
    @unittest.skipIf(__import__('sys').platform == 'win32', 'requires Unix processes')
    def test_playback_shares_parent_group_and_exits_after_parent_is_killed(self):
        import os
        import signal
        import subprocess
        import sys
        import time
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            fake = root / 'afplay'
            fake.write_text('#!/bin/sh\nsleep .2\n')
            fake.chmod(0o755)
            marker = root / 'audio.pid'
            code = '''import subprocess,sys,time
from pathlib import Path
import alarm
real = subprocess.Popen
def fake(args, **kwargs):
    args = [arg.replace('/usr/bin/afplay', sys.argv[2]) if isinstance(arg,str) else arg for arg in args]
    return real(args, **kwargs)
alarm.subprocess.Popen = fake
sound = alarm.MacSound(Path(sys.argv[1]))
sound.play('due')
Path(sys.argv[1], 'audio.pid').write_text(str(sound.process.pid))
time.sleep(30)
'''
            parent = subprocess.Popen([sys.executable, '-c', code, str(root), str(fake)],
                                      cwd=str(Path(__file__).parent), start_new_session=True)
            child = None
            try:
                deadline = time.monotonic() + 5
                while not marker.exists() and time.monotonic() < deadline:
                    time.sleep(.02)
                self.assertTrue(marker.exists(), 'playback did not start')
                child = int(marker.read_text())
                self.assertEqual(os.getpgid(child), os.getpgid(parent.pid))
                parent.kill()
                parent.wait(timeout=3)
                # Even without launchd cleanup, the loop's parent guard ends playback.
                deadline = time.monotonic() + 3
                state = ''
                while time.monotonic() < deadline:
                    state = subprocess.run(['/bin/ps', '-o', 'stat=', '-p', str(child)],
                                           capture_output=True, text=True).stdout.strip()
                    if not state or state.startswith('Z'):
                        break
                    time.sleep(.02)
                self.assertTrue(not state or state.startswith('Z'), 'orphan playback remained running')
            finally:
                if parent.poll() is None:
                    parent.kill()
                    parent.wait(timeout=3)
                try:
                    os.killpg(parent.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
