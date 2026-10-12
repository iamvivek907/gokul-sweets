import contextlib
import copy
import io
import json
from pathlib import Path
import sqlite3
import socket
import tempfile
import threading
import unittest
from unittest.mock import Mock, patch
from urllib.error import HTTPError

import agent


class AgentTest(unittest.TestCase):
    def setUp(self):
        self.cfg = json.loads(Path(__file__).with_name("config.example.json").read_text())
        self.kot = {"kotNumber": "KOT-1", "orderNumber": "ORDER-1", "customerOrderNumber": 7,
                    "branchName": "Shop", "branchAddress": "Main road", "fulfillmentType": "PICKUP",
                    "pickupDate": "2026-10-10", "pickupStartTime": "10:00:00", "pickupEndTime": "11:00:00",
                    "pickupType": "NORMAL", "items": [{"productName": "Sweet", "quantity": 1,
                    "quantityLabel": "500 g", "displayOrder": 1}]}
        self.job = {"printJobId": 12, "claimToken": "private-claim", "copies": 1,
                    "printer": {"code": self.cfg["printer_code"], "station": self.cfg["station"],
                    "protocol": self.cfg["protocol"], "host": self.cfg["target"], "port": 9100,
                    "paperWidthMm": 80, "autoCut": False}, "kot": self.kot}
        self.db = sqlite3.connect(":memory:")
        self.db.execute("CREATE TABLE pending (id INTEGER PRIMARY KEY, payload TEXT)")
        self.addCleanup(self.db.close)
        self.api = Mock(cfg=self.cfg)

    def test_delivery_uses_delivery_window_and_preserves_weight(self):
        self.kot.update(fulfillmentType="DELIVERY", pickupDate=None, pickupStartTime=None,
                        pickupEndTime=None, deliveryDate="2026-10-12",
                        deliveryStartTime="14:00:00", deliveryEndTime="15:00:00")
        output = agent.ticket(self.cfg, self.kot)
        self.assertIn(b"Delivery: 2026-10-12", output)
        self.assertIn(b"14:00:00 - 15:00:00 IST", output)
        self.assertIn(b"500 g  Sweet", output)
        self.assertNotIn(b"Pickup:", output)

    def test_text_cannot_inject_commands_and_non_ascii_is_rejected(self):
        self.kot["items"][0]["productName"] = "Sweet\x1b@\nSecond\x1dV"
        output = agent.ticket(self.cfg, self.kot)
        self.assertEqual(output.count(b"\x1b"), 1)
        self.assertNotIn(b"\x1d", output)
        self.kot["items"][0]["productName"] = "मिठाई"
        with self.assertRaises(UnicodeEncodeError):
            agent.ticket(self.cfg, self.kot)

    def test_narrow_ticket_wraps_and_only_opt_in_cut(self):
        self.cfg["paper_width_mm"] = 58
        self.kot["items"][0]["productName"] = "A very long product name " * 4
        output = agent.ticket(self.cfg, self.kot)
        self.assertTrue(all(len(line) <= 32 for line in output[2:].splitlines()))
        self.assertNotIn(b"\x1dV", output)
        self.cfg["auto_cut"] = True
        self.assertTrue(agent.ticket(self.cfg).endswith(b"\x1dV\x00"))

    def test_profile_mismatch_stops_without_physical_write(self):
        self.job["printer"]["host"] = "Other shop queue"
        with patch.object(agent, "deliver") as send, self.assertRaises(ValueError):
            agent.process_job(self.api, self.db, self.cfg, self.job)
        send.assert_not_called()
        self.assertEqual(self.api.post.call_args.args[0], "jobs/12/failed")
        self.assertIsNone(self.db.execute("SELECT * FROM pending").fetchone())

    def test_partial_write_is_journalled_and_not_marked_failed_or_printed(self):
        with patch.object(agent, "deliver", side_effect=IOError("partial")), self.assertRaises(IOError):
            agent.process_job(self.api, self.db, self.cfg, self.job)
        self.api.post.assert_not_called()
        self.assertEqual(json.loads(self.db.execute("SELECT payload FROM pending").fetchone()[0])["state"], "uncertain")

    def test_acknowledgement_failure_keeps_sent_journal(self):
        self.api.post.side_effect = HTTPError("https://example.com", 503, "unavailable", {}, None)
        with patch.object(agent, "deliver") as send, self.assertRaises(HTTPError):
            agent.process_job(self.api, self.db, self.cfg, self.job)
        send.assert_called_once()
        self.assertEqual(json.loads(self.db.execute("SELECT payload FROM pending").fetchone()[0])["state"], "sent")

    def test_success_clears_journal_only_after_acknowledgement(self):
        def ack(path, body):
            self.assertEqual(path, "jobs/12/printed")
            self.assertIsNotNone(self.db.execute("SELECT payload FROM pending").fetchone())
            self.assertEqual(body["claimToken"], "private-claim")
        self.api.post.side_effect = ack
        with patch.object(agent, "deliver") as send, contextlib.redirect_stdout(io.StringIO()):
            agent.process_job(self.api, self.db, self.cfg, self.job)
        send.assert_called_once()
        self.assertIsNone(self.db.execute("SELECT * FROM pending").fetchone())

    def test_restart_does_not_claim_or_resend_and_resolution_only_acknowledges(self):
        with tempfile.TemporaryDirectory() as directory:
            profile = Path(directory) / "profile.json"
            with contextlib.closing(sqlite3.connect(str(profile) + ".sqlite3")) as db:
                db.execute("CREATE TABLE pending (id INTEGER PRIMARY KEY, payload TEXT)")
                agent.save_pending(db, self.cfg, self.job, "uncertain")
            with patch.object(agent, "Api", return_value=self.api), patch.object(agent, "deliver") as send:
                with contextlib.redirect_stdout(io.StringIO()), self.assertRaises(ValueError):
                    agent.consume(self.cfg, profile)
                self.api.post.assert_not_called()
                with contextlib.redirect_stdout(io.StringIO()):
                    agent.consume(self.cfg, profile, "printed")
                self.assertEqual(self.api.post.call_args.args[0], "jobs/12/printed")
                send.assert_not_called()

    def test_single_profile_lock_rejects_second_process(self):
        with tempfile.TemporaryDirectory() as directory:
            profile = Path(directory) / "profile.json"
            with agent.exclusive_profile(profile), self.assertRaises(OSError):
                with agent.exclusive_profile(profile):
                    self.fail("Second lock acquired")

    def test_authentication_error_stops_instead_of_polling(self):
        self.api.post.side_effect = HTTPError("https://example.com", 401, "unauthorized", {}, None)
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(agent, "Api", return_value=self.api), patch.object(agent.time, "sleep") as sleep:
                with self.assertRaises(HTTPError):
                    agent.consume(self.cfg, Path(directory) / "profile.json")
                sleep.assert_not_called()

    def test_usb_raw_sequence_and_partial_write_abort(self):
        driver = Mock()
        driver.WritePrinter.return_value = 1
        with patch.object(agent.sys, "platform", "win32"), patch.dict("sys.modules", {"win32print": driver}):
            with self.assertRaises(IOError):
                agent.send(self.cfg, b"123")
        driver.StartDocPrinter.assert_called_once_with(driver.OpenPrinter.return_value, 1, ("Gokul KOT", None, "RAW"))
        driver.AbortPrinter.assert_called_once()
        driver.ClosePrinter.assert_called_once()

    def test_bluetooth_opens_paired_port_with_bounded_writes(self):
        self.cfg.update(protocol="ESC_POS_BLUETOOTH", target="COM5")
        driver = Mock()
        driver.Serial.return_value.__enter__ = Mock(return_value=driver)
        driver.Serial.return_value.__exit__ = Mock(return_value=False)
        driver.write.return_value = 3
        with patch.dict("sys.modules", {"serial": driver}):
            agent.send(self.cfg, b"123")
        driver.Serial.assert_called_once_with("COM5", 9600, timeout=5, write_timeout=10)
        driver.write.assert_called_once_with(b"123")
        driver.flush.assert_called_once()

    def test_child_transport_delivers_actual_tcp_bytes(self):
        received = []
        with socket.socket() as server:
            server.bind(("127.0.0.1", 0))
            server.listen(1)
            server.settimeout(5)
            def receive():
                with server.accept()[0] as connection:
                    connection.settimeout(5)
                    chunks = []
                    while chunk := connection.recv(4096):
                        chunks.append(chunk)
                    received.append(b"".join(chunks))
            listener = threading.Thread(target=receive)
            listener.start()
            self.cfg.update(protocol="ESC_POS_TCP", target="127.0.0.1", port=server.getsockname()[1])
            agent.deliver(self.cfg, b"actual ESC/POS data")
            listener.join(5)
            self.assertFalse(listener.is_alive())
        self.assertEqual(received, [b"actual ESC/POS data"])

    def test_transport_timeout_terminates_worker_and_reports_uncertain(self):
        ctx = Mock()
        reader, writer, worker = Mock(), Mock(), Mock()
        ctx.Pipe.return_value = (reader, writer)
        ctx.Process.return_value = worker
        worker.is_alive.side_effect = [True, True, False]
        with patch.object(agent.multiprocessing, "get_context", return_value=ctx):
            with self.assertRaises(TimeoutError):
                agent.deliver(self.cfg, b"data")
        worker.terminate.assert_called_once()
        reader.close.assert_called_once()

    def test_keyboard_interrupt_stops_worker_before_returning_to_profile_lock(self):
        ctx = Mock()
        reader, writer, worker = Mock(), Mock(), Mock()
        ctx.Pipe.return_value = (reader, writer)
        ctx.Process.return_value = worker
        worker.pid = 99
        worker.join.side_effect = [KeyboardInterrupt(), None]
        worker.is_alive.side_effect = [True, False]
        with patch.object(agent.multiprocessing, "get_context", return_value=ctx):
            with self.assertRaises(KeyboardInterrupt):
                agent.deliver(self.cfg, b"data")
        worker.terminate.assert_called_once()
        self.assertEqual(worker.join.call_args_list[-1].args, (5,))
        reader.close.assert_called_once()
        self.api.post.assert_not_called()

    def test_cleanup_kills_worker_if_termination_does_not_finish(self):
        ctx = Mock()
        reader, writer, worker = Mock(), Mock(), Mock()
        ctx.Pipe.return_value = (reader, writer)
        ctx.Process.return_value = worker
        worker.join.side_effect = [KeyboardInterrupt(), None, None]
        worker.is_alive.return_value = True
        with patch.object(agent.multiprocessing, "get_context", return_value=ctx):
            with self.assertRaises(KeyboardInterrupt):
                agent.deliver(self.cfg, b"data")
        worker.terminate.assert_called_once()
        worker.kill.assert_called_once()
        self.assertEqual(worker.join.call_args_list[-1].args, ())

    def test_lost_committed_ack_is_reconciled_without_resending(self):
        class CommittedAckApi:
            def __init__(self, cfg):
                self.cfg = cfg
                self.printed = False
            def identity(self):
                return {"branchId": self.cfg["branch_id"], "agentId": self.cfg["agent_id"], "station": self.cfg["station"]}
            def post(self, path, body):
                if path.endswith("/status"):
                    return {"printJobId": 12, "branchId": self.cfg["branch_id"], "station": self.cfg["station"], "status": "PRINTED"}
                if self.printed:
                    raise HTTPError("https://example.com", 409, "already printed", {}, None)
                self.printed = True
                raise TimeoutError("Response lost after backend commit")
        api = CommittedAckApi(self.cfg)
        with tempfile.TemporaryDirectory() as directory:
            profile = Path(directory) / "profile.json"
            with patch.object(agent, "deliver") as send:
                with contextlib.closing(sqlite3.connect(str(profile) + ".sqlite3")) as db:
                    db.execute("CREATE TABLE pending (id INTEGER PRIMARY KEY, payload TEXT)")
                    with self.assertRaises(TimeoutError):
                        agent.process_job(api, db, self.cfg, self.job)
                send.assert_called_once()
                with patch.object(agent, "Api", return_value=api), contextlib.redirect_stdout(io.StringIO()):
                    agent.consume(self.cfg, profile, "printed")
                send.assert_called_once()
            with contextlib.closing(sqlite3.connect(str(profile) + ".sqlite3")) as db:
                self.assertIsNone(db.execute("SELECT * FROM pending").fetchone())

    def test_conflict_recovery_rejects_non_terminal_or_wrong_scope_status(self):
        self.api.identity.return_value = {"branchId": 1, "station": "KITCHEN", "agentId": "test"}
        good = {"printJobId": 12, "branchId": 1, "station": "KITCHEN", "status": "PRINTED"}
        for change in ({"status": "CLAIMED"}, {"status": "FAILED"}, {"branchId": 2},
                       {"station": "BILLING"}, {"printJobId": 99}):
            with self.subTest(change=change):
                self.api.post.side_effect = [HTTPError("https://example.com", 409, "conflict", {}, None), good | change]
                with self.assertRaises(ValueError):
                    agent.acknowledge(self.api, self.job, "printed")

    def test_retry_conflict_does_not_treat_printed_status_as_retry_approval(self):
        self.api.post.side_effect = HTTPError("https://example.com", 409, "conflict", {}, None)
        with self.assertRaises(HTTPError):
            agent.acknowledge(self.api, self.job, "retry")
        self.api.post.assert_called_once()

    def test_child_transport_reports_failure_without_acknowledgement(self):
        ctx = Mock()
        reader, writer, worker = Mock(), Mock(), Mock()
        ctx.Pipe.return_value = (reader, writer)
        ctx.Process.return_value = worker
        worker.is_alive.return_value = False
        worker.exitcode = 0
        reader.poll.return_value = True
        reader.recv.return_value = False
        with patch.object(agent.multiprocessing, "get_context", return_value=ctx):
            with self.assertRaises(IOError):
                agent.deliver(self.cfg, b"data")

    def test_config_rejects_insecure_or_credentialled_api_and_bad_devices(self):
        with tempfile.TemporaryDirectory() as directory:
            profile = Path(directory) / "profile.json"
            for origin in ("http://api.example.com", "https://secret@api.example.com", "https://api.example.com/path"):
                cfg = copy.deepcopy(self.cfg)
                cfg["api_url"] = origin
                profile.write_text(json.dumps(cfg))
                with self.assertRaises(ValueError):
                    agent.load_config(profile)
            cfg = copy.deepcopy(self.cfg)
            cfg["target"] = "Queue\nInjected"
            profile.write_text(json.dumps(cfg))
            with self.assertRaises(ValueError):
                agent.load_config(profile)

    def test_api_refuses_redirects_and_sql_escapes_queue_name(self):
        self.assertIsNone(agent.NoRedirect().redirect_request(None, None, 302, "", {}, "https://elsewhere.example.com"))
        self.cfg["target"] = "Shop's printer"
        self.assertIn("'Shop''s printer'", agent.sql(self.cfg))


    def test_weighted_backend_compatibility_suffix_is_not_printed_twice(self):
        self.kot["items"][0].update(productName="Sweet · 500 g", saleMode="WEIGHT")
        output = agent.ticket(self.cfg, self.kot)
        self.assertEqual(output.count(b"500 g"), 1)
        self.assertIn(b"500 g  Sweet", output)

    def managed_db(self):
        self.db.execute("CREATE TABLE commands (id TEXT PRIMARY KEY, result TEXT NOT NULL)")

    def test_remote_test_receipt_prevents_duplicate_after_restart_or_lost_reply(self):
        self.managed_db()
        command = {"id": "test-1", "action": "TEST"}
        with patch.object(agent, "deliver") as send:
            result = agent.managed_command(self.api, self.db, self.cfg, command)
            again = agent.managed_command(self.api, self.db, self.cfg, command)
        self.assertEqual(result, again)
        send.assert_called_once()
        self.api.post.assert_not_called()

    def test_expired_command_does_not_print_when_station_reconnects(self):
        self.managed_db()
        with patch.object(agent, "deliver") as send:
            result = agent.managed_command(self.api, self.db, self.cfg, {"id": "expired", "action": "TEST", "expiresAt": 0})
        self.assertEqual(result["status"], "EXPIRED")
        send.assert_not_called()

    def test_failed_remote_test_is_never_automatically_resent(self):
        self.managed_db()
        command = {"id": "test-2", "action": "TEST"}
        with patch.object(agent, "deliver", side_effect=TimeoutError()) as send:
            with self.assertRaises(TimeoutError):
                agent.managed_command(self.api, self.db, self.cfg, command)
            result = agent.managed_command(self.api, self.db, self.cfg, command)
        self.assertEqual(result["status"], "NEEDS_ATTENTION")
        send.assert_called_once()

    def test_remote_recovery_requires_exact_pending_job_and_never_writes_paper(self):
        self.managed_db()
        agent.save_pending(self.db, self.cfg, self.job, "uncertain")
        with patch.object(agent, "deliver") as send:
            for command in ({"id": "wrong", "action": "PRINTED", "jobId": 99}, {"id": "test", "action": "TEST"}):
                with self.assertRaises(ValueError):
                    agent.managed_command(self.api, self.db, self.cfg, command)
            result = agent.managed_command(self.api, self.db, self.cfg,
                         {"id": "correct", "action": "PRINTED", "jobId": 12})
            self.assertEqual(result["status"], "DONE")
            self.api.post.assert_called_once_with("jobs/12/printed", {"agentId": self.cfg["agent_id"], "claimToken": "private-claim"})
            send.assert_not_called()
            self.assertIsNone(self.db.execute("SELECT * FROM pending").fetchone())

    def test_failed_recovery_keeps_pending_ticket(self):
        self.managed_db()
        agent.save_pending(self.db, self.cfg, self.job, "uncertain")
        self.api.post.side_effect = TimeoutError()
        with self.assertRaises(TimeoutError):
            agent.managed_command(self.api, self.db, self.cfg, {"id": "retry", "action": "RETRY", "jobId": 12})
        self.assertIsNotNone(self.db.execute("SELECT * FROM pending").fetchone())

    def remote_profile(self):
        return {"branchId": self.cfg["branch_id"], "station": self.cfg["station"],
                "agentId": self.cfg["agent_id"], "printerCode": self.cfg["printer_code"],
                "protocol": self.cfg["protocol"], "target": self.cfg["target"], "port": 9100,
                "baudRate": 9600, "paperWidthMm": 80, "autoCut": False}

    def test_remote_profile_updates_connection_but_cannot_change_identity_or_origin(self):
        profile = self.remote_profile() | {"target": "New queue", "api_url": "https://attacker.example"}
        updated = agent.managed_profile(self.cfg, profile)
        self.assertEqual(updated["target"], "New queue")
        self.assertEqual(updated["api_url"], self.cfg["api_url"])
        with self.assertRaises(ValueError):
            agent.managed_profile(self.cfg, profile | {"branchId": 99})

    def test_background_paused_agent_reports_but_does_not_claim(self):
        self.api.identity.return_value = {"branchId": 1, "agentId": "shop", "station": "KITCHEN"}
        self.api.post.side_effect = [{"profile": self.remote_profile(), "enabled": False, "command": {}}, {}]
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(agent, "Api", return_value=self.api), patch.object(agent, "devices_snapshot", return_value={}), patch.object(agent.time, "sleep", side_effect=KeyboardInterrupt):
                with self.assertRaises(KeyboardInterrupt):
                    agent.managed(self.cfg, Path(directory) / "profile.json")
        self.assertEqual([call.args[0] for call in self.api.post.call_args_list], ["control", "heartbeat"])

    def test_background_with_pending_ticket_stays_online_without_claiming(self):
        self.api.identity.return_value = {"branchId": 1, "agentId": "shop", "station": "KITCHEN"}
        self.api.post.side_effect = [{"profile": self.remote_profile(), "enabled": True, "command": {}}, {}]
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "profile.json"
            with contextlib.closing(sqlite3.connect(str(path) + ".sqlite3")) as db:
                db.execute("CREATE TABLE pending (id INTEGER PRIMARY KEY, payload TEXT)")
                agent.save_pending(db, self.cfg, self.job, "uncertain")
            with patch.object(agent, "Api", return_value=self.api), patch.object(agent, "devices_snapshot", return_value={}), patch.object(agent.time, "sleep", side_effect=KeyboardInterrupt):
                with self.assertRaises(KeyboardInterrupt):
                    agent.managed(self.cfg, path)
        report = self.api.post.call_args_list[0].args[1]["runtime"]
        self.assertEqual(report["status"], "NEEDS_ATTENTION")
        self.assertEqual(report["pendingJobId"], 12)
        self.assertNotIn("claimToken", json.dumps(report))
        self.assertEqual([call.args[0] for call in self.api.post.call_args_list], ["control", "heartbeat"])

    def test_invalid_job_holds_background_claims_until_operator_fixes_or_tests(self):
        self.api.identity.return_value = {"branchId": 1, "agentId": "shop", "station": "KITCHEN"}
        bad = dict(self.job, copies=0)
        reports = []
        def post(path, body):
            if path == "control":
                reports.append(body["runtime"])
                return {"profile": self.remote_profile(), "enabled": True, "command": {}}
            return bad if path == "jobs/claim" else None
        self.api.post.side_effect = post
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(agent, "Api", return_value=self.api), patch.object(agent, "devices_snapshot", return_value={}), patch.object(agent.time, "sleep", side_effect=[None, KeyboardInterrupt]), patch.object(agent, "deliver") as send:
                with self.assertRaises(KeyboardInterrupt):
                    agent.managed(self.cfg, Path(directory) / "profile.json")
                send.assert_not_called()
        self.assertEqual(sum(call.args[0] == "jobs/claim" for call in self.api.post.call_args_list), 1)
        self.assertEqual(reports[-1]["status"], "ERROR")

    def test_lost_retry_ack_can_recover_without_sending_paper(self):
        self.managed_db()
        agent.save_pending(self.db, self.cfg, self.job, "uncertain")
        # First response is lost after the server commits; the same claim's
        # persisted receipt makes the second acknowledgement a successful no-op.
        self.api.post.side_effect = [TimeoutError(), None]
        command = {"id": "retry-1", "action": "RETRY", "jobId": 12}
        with patch.object(agent, "deliver") as send:
            with self.assertRaises(TimeoutError):
                agent.managed_command(self.api, self.db, self.cfg, command)
            self.assertIsNotNone(self.db.execute("SELECT * FROM pending").fetchone())
            result = agent.managed_command(self.api, self.db, self.cfg, command | {"id": "retry-2"})
            self.assertEqual(result["status"], "DONE")
            self.assertIsNone(self.db.execute("SELECT * FROM pending").fetchone())
            self.assertEqual(self.api.post.call_args_list[0], self.api.post.call_args_list[1])
            send.assert_not_called()

    def test_validation_hold_survives_disconnect_and_reconnect(self):
        self.api.identity.return_value = {"branchId": 1, "agentId": "shop", "station": "KITCHEN"}
        reports = []
        def post(path, body):
            if path == "control":
                reports.append(body["runtime"])
                if len(reports) == 2:
                    raise agent.error.URLError("temporary outage")
                return {"profile": self.remote_profile(), "enabled": True, "command": {}}
            return dict(self.job, copies=0) if path == "jobs/claim" else None
        self.api.post.side_effect = post
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(agent, "Api", return_value=self.api), patch.object(agent, "devices_snapshot", return_value={}), patch.object(agent.time, "sleep", side_effect=[None, None, None, KeyboardInterrupt]), patch.object(agent, "deliver") as send:
                with self.assertRaises(KeyboardInterrupt):
                    agent.managed(self.cfg, Path(directory) / "profile.json")
                send.assert_not_called()
        self.assertEqual(sum(call.args[0] == "jobs/claim" for call in self.api.post.call_args_list), 1)
        self.assertEqual(reports[1]["message"], reports[-1]["message"])
        self.assertEqual(reports[-1]["status"], "ERROR")

    def test_validation_failure_ack_outage_also_holds_printing(self):
        self.api.identity.return_value = {"branchId": 1, "agentId": "shop", "station": "KITCHEN"}
        def post(path, body):
            if path == "control":
                return {"profile": self.remote_profile(), "enabled": True, "command": {}}
            if path.endswith("/failed"):
                raise TimeoutError("Failure response lost")
            return dict(self.job, copies=0) if path == "jobs/claim" else None
        self.api.post.side_effect = post
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(agent, "Api", return_value=self.api), patch.object(agent, "devices_snapshot", return_value={}), patch.object(agent.time, "sleep", side_effect=[None, KeyboardInterrupt]), patch.object(agent, "deliver") as send:
                with self.assertRaises(KeyboardInterrupt):
                    agent.managed(self.cfg, Path(directory) / "profile.json")
                send.assert_not_called()
        self.assertEqual(sum(call.args[0] == "jobs/claim" for call in self.api.post.call_args_list), 1)

    def test_connection_failure_alone_recovers_without_operator_action(self):
        self.api.identity.return_value = {"branchId": 1, "agentId": "shop", "station": "KITCHEN"}
        controls = 0
        def post(path, body):
            nonlocal controls
            if path == "control":
                controls += 1
                if controls == 1:
                    raise agent.error.URLError("temporary outage")
                return {"profile": self.remote_profile(), "enabled": True, "command": {}}
            return None
        self.api.post.side_effect = post
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(agent, "Api", return_value=self.api), patch.object(agent, "devices_snapshot", return_value={}), patch.object(agent.time, "sleep", side_effect=[None, KeyboardInterrupt]):
                with self.assertRaises(KeyboardInterrupt):
                    agent.managed(self.cfg, Path(directory) / "profile.json")
        self.assertEqual(sum(call.args[0] == "jobs/claim" for call in self.api.post.call_args_list), 1)

    def test_expired_test_does_not_clear_validation_hold(self):
        self.api.identity.return_value = {"branchId": 1, "agentId": "shop", "station": "KITCHEN"}
        controls = 0
        def post(path, body):
            nonlocal controls
            if path == "control":
                controls += 1
                command = {"id": "expired-test", "action": "TEST", "expiresAt": 0} if controls == 2 else {}
                return {"profile": self.remote_profile(), "enabled": controls != 2, "command": command}
            return dict(self.job, copies=0) if path == "jobs/claim" else None
        self.api.post.side_effect = post
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(agent, "Api", return_value=self.api), patch.object(agent, "devices_snapshot", return_value={}), patch.object(agent.time, "sleep", side_effect=[None, None, KeyboardInterrupt]), patch.object(agent, "deliver") as send:
                with self.assertRaises(KeyboardInterrupt):
                    agent.managed(self.cfg, Path(directory) / "profile.json")
                send.assert_not_called()
        self.assertEqual(sum(call.args[0] == "jobs/claim" for call in self.api.post.call_args_list), 1)

    def test_validation_hold_survives_restart_with_bootstrap_and_remote_profiles(self):
        for remote_target in (self.cfg["target"], "Remotely selected queue"):
            with self.subTest(target=remote_target), tempfile.TemporaryDirectory() as directory:
                path = Path(directory) / "profile.json"
                api = Mock(cfg=dict(self.cfg))
                api.identity.return_value = {"branchId": 1, "agentId": "shop", "station": "KITCHEN"}
                reports = []
                def post(endpoint, body):
                    if endpoint == "control":
                        reports.append(body["runtime"])
                        return {"profile": self.remote_profile() | {"target": remote_target}, "enabled": True, "command": {}}
                    return dict(self.job, copies=0) if endpoint == "jobs/claim" else None
                api.post.side_effect = post
                with patch.object(agent, "Api", return_value=api), patch.object(agent, "devices_snapshot", return_value={}), patch.object(agent, "deliver") as send:
                    for _ in range(2):
                        with patch.object(agent.time, "sleep", side_effect=KeyboardInterrupt), self.assertRaises(KeyboardInterrupt):
                            agent.managed(dict(self.cfg), path)
                    send.assert_not_called()
                self.assertEqual(sum(call.args[0] == "jobs/claim" for call in api.post.call_args_list), 1)
                self.assertEqual(reports[-1]["status"], "ERROR")
                with contextlib.closing(sqlite3.connect(str(path) + ".sqlite3")) as db:
                    row = db.execute("SELECT message,profile FROM holds").fetchone()
                    self.assertIsNotNone(row)
                    self.assertEqual(json.loads(row[1])["target"], remote_target)

    def test_changed_admin_profile_clears_saved_hold_after_restart(self):
        self.api.identity.return_value = {"branchId": 1, "agentId": "shop", "station": "KITCHEN"}
        profile = self.remote_profile()
        claims = []
        def post(endpoint, body):
            if endpoint == "control":
                return {"profile": profile, "enabled": True, "command": {}}
            if endpoint == "jobs/claim":
                claims.append(1)
                return dict(self.job, copies=0) if len(claims) == 1 else None
            return None
        self.api.post.side_effect = post
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "profile.json"
            with patch.object(agent, "Api", return_value=self.api), patch.object(agent, "devices_snapshot", return_value={}):
                with patch.object(agent.time, "sleep", side_effect=KeyboardInterrupt), self.assertRaises(KeyboardInterrupt):
                    agent.managed(dict(self.cfg), path)
                profile = profile | {"target": "Corrected queue"}
                with patch.object(agent.time, "sleep", side_effect=KeyboardInterrupt), self.assertRaises(KeyboardInterrupt):
                    agent.managed(dict(self.cfg), path)
            with contextlib.closing(sqlite3.connect(str(path) + ".sqlite3")) as db:
                self.assertIsNone(db.execute("SELECT * FROM holds").fetchone())
        self.assertEqual(len(claims), 2)

    def test_successful_test_clears_saved_hold_across_restart(self):
        self.api.identity.return_value = {"branchId": 1, "agentId": "shop", "station": "KITCHEN"}
        command = {}
        claims = []
        def post(endpoint, body):
            if endpoint == "control":
                return {"profile": self.remote_profile(), "enabled": not command, "command": command}
            if endpoint == "jobs/claim":
                claims.append(1)
                return dict(self.job, copies=0) if len(claims) == 1 else None
            return None
        self.api.post.side_effect = post
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "profile.json"
            with patch.object(agent, "Api", return_value=self.api), patch.object(agent, "devices_snapshot", return_value={}), patch.object(agent, "deliver") as send:
                for action in ({}, {"id": "successful-test", "action": "TEST"}, {}):
                    command = action
                    with patch.object(agent.time, "sleep", side_effect=KeyboardInterrupt), self.assertRaises(KeyboardInterrupt):
                        agent.managed(dict(self.cfg), path)
                send.assert_called_once()
            with contextlib.closing(sqlite3.connect(str(path) + ".sqlite3")) as db:
                self.assertIsNone(db.execute("SELECT * FROM holds").fetchone())
        self.assertEqual(len(claims), 2)

    def test_hold_profile_signature_excludes_unrecognized_local_fields(self):
        signature = agent.profile_signature(self.cfg | {"local_secret": "must-not-be-saved"})
        self.assertNotIn("must-not-be-saved", signature)
        self.assertEqual(json.loads(signature)["target"], self.cfg["target"])

    @unittest.skipUnless(agent.sys.platform == "win32", "Windows DPAPI")
    def test_installer_secret_can_be_decrypted_only_through_windows_dpapi(self):
        import win32crypt
        secret = b"test-only-secret"
        encrypted = win32crypt.CryptProtectData(secret, "Gokul print agent", None, None, None, 0)
        self.assertNotIn(secret, encrypted)
        self.assertEqual(win32crypt.CryptUnprotectData(encrypted, None, None, None, 0)[1], secret)


if __name__ == "__main__":
    unittest.main()
