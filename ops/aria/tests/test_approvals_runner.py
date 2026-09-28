import datetime as dt
import json
import subprocess
import unittest
from types import SimpleNamespace

from ops.aria.approvals import telegram_cmd as tc
from ops.aria.fingerprint_store import FingerprintStore
from ops.aria.models import Change
from ops.aria.runners import claude_headless as runner
from ops.aria.tests.helpers import TempDirCase

FP = "a" * 64


class TelegramCmdTest(unittest.TestCase):
    def test_parse_valid(self):
        c = tc.parse_command('CMD:v1 {"action":"approve","fp":"%s"}' % FP)
        self.assertEqual((c.action, c.fp), ("approve", FP))
        c = tc.parse_command('  CMD:v1   {"action":"reject","fp":"%s","reason":"오탐"}  ' % FP)
        self.assertEqual(c.reason, "오탐")
        self.assertEqual(tc.parse_command('CMD:v1 {"action":"status"}').action, "status")
        self.assertEqual(tc.parse_command('CMD:v1 {"action":"kill","form_id":"TH_TDAC"}').form_id, "TH_TDAC")

    def test_parse_rejects(self):
        bad = [
            'CMD:v2 {"action":"approve","fp":"%s"}' % FP,
            'CMD:v1{"action":"approve","fp":"%s"}' % FP,
            'cmd:v1 {"action":"approve","fp":"%s"}' % FP,
            'CMD:v1 {"action":"approve"}',
            'CMD:v1 {"action":"approve","fp":"%s","extra":1}' % FP,
            'CMD:v1 {"action":"approve","fp":"%s","fp":"%s"}' % (FP, FP),
            'CMD:v1 {"action":"approve","fp":"%s"}' % FP.upper(),
            'CMD:v1 {"action":"approve","fp":"abc"}',
            'CMD:v1 {"action":"deploy","fp":"%s"}' % FP,
            'CMD:v1 {"action":"kill","form_id":"th_tdac"}',
            'CMD:v1 {"action":"kill","form_id":"TH_TDAC","fp":"%s"}' % FP,
            'CMD:v1 {"action":"reject","fp":"%s","reason":"%s"}' % (FP, "x" * 201),
            'CMD:v1 {"action":"status","fp":123}',
            'CMD:v1 ["approve"]',
            'CMD:v1 {not json}',
            'CMD:v1 ' + " " * 1000 + '{}',
        ]
        for text in bad:
            with self.assertRaises(tc.CommandError, msg=text):
                tc.parse_command(text)

    def test_format_approval_request(self):
        ch = Change("mofa_entry_diff", "TH", "[외교부 입국요건] TH: 바뀐 칸 1개", {"x": "y"}, "C:/ev/th.json")
        msg = tc.format_approval_request(ch)
        self.assertIn(ch.fingerprint, msg)
        self.assertIn("C:/ev/th.json", msg)
        self.assertIn('"x": "y"', msg)
        lines = [ln for ln in msg.splitlines() if ln.startswith("CMD:v1")]
        self.assertEqual([tc.parse_command(ln).action for ln in lines], ["approve", "reject"])
        big = Change("d", "u", "s", {"x": "z" * 10000})
        self.assertLessEqual(len(tc.format_approval_request(big)), 4096)
        self.assertIn("AI 요약", tc.format_approval_request(ch, "요약문"))

    def test_handle_command(self):
        store = FingerprintStore(":memory:")
        self.addCleanup(store.close)
        ch = Change("d", "u", "s", {"a": 1})
        store.register(ch)
        cmd = tc.parse_command(tc.format_command("approve", fp=ch.fingerprint))
        self.assertIn("승인", tc.handle_command(cmd, store))
        self.assertEqual(store.get(ch.fingerprint)["review"], "accepted")
        self.assertIn("이미", tc.handle_command(tc.Command("reject", fp=ch.fingerprint), store))
        self.assertIn(ch.fingerprint, tc.handle_command(tc.Command("status", fp=ch.fingerprint), store))
        self.assertIn("설정", tc.handle_command(tc.Command("kill", form_id="TH_TDAC"), store))


class FakeRun:
    def __init__(self, stdout="", returncode=0, exc=None):
        self.stdout, self.returncode, self.exc = stdout, returncode, exc
        self.calls = []

    def __call__(self, argv, **kw):
        self.calls.append((argv, kw))
        if self.exc:
            raise self.exc
        return SimpleNamespace(stdout=self.stdout, stderr="", returncode=self.returncode)


class ClaudeRunnerTest(unittest.TestCase, TempDirCase):
    def setUp(self):
        self.tmp = self.make_tmp()
        self.store = FingerprintStore(self.tmp / "s.sqlite")
        self.addCleanup(self.store.close)
        self.change = Change("form_structure_hash", "TH_TDAC", "양식 구조 바뀜", {"x": 1}, "C:/ev/a.json")
        self.store.register(self.change)
        self.day = dt.date(2026, 9, 29)

    def run_it(self, fake, cap=3):
        return runner.run_for_change(self.change.to_dict(), self.store, self.tmp, "TH / TH_TDAC",
                                     claude_bin="claude-test-bin", daily_cap=cap, timeout_sec=5,
                                     runner=fake, today=self.day)

    def test_not_accepted_does_not_run(self):
        fake = FakeRun()
        r = self.run_it(fake)
        self.assertEqual((r.status, r.reason), ("not_run", "not_accepted"))
        self.assertEqual(fake.calls, [])

    def test_runs_once_with_safe_argv_and_parses_pr(self):
        self.store.set_review(self.change.fingerprint, "accepted")
        out = json.dumps({"type": "result", "is_error": False,
                          "result": "PR 만듦: https://github.com/owner/ReadyPort/pull/12 끝"})
        fake = FakeRun(stdout="경고 줄\n" + out)
        r = self.run_it(fake)
        self.assertEqual(r.status, "ok")
        self.assertEqual(r.pr_url, "https://github.com/owner/ReadyPort/pull/12")
        argv, kw = fake.calls[0]
        self.assertEqual(argv[1], "-p")
        self.assertNotIn("--bare", argv)
        self.assertEqual(argv[argv.index("--output-format") + 1], "json")
        self.assertIn("--allowedTools", argv)
        self.assertEqual(kw["cwd"], str(self.tmp))
        self.assertEqual(kw["timeout"], 5)
        self.assertFalse(kw["shell"])
        prompt = argv[2]
        for must in ("제출", "캡차", "개인정보", "build_packs.py --check", "main 브랜치에 직접 푸시하지 않는다",
                     r.branch, self.change.fingerprint, "C:/ev/a.json"):
            self.assertIn(must, prompt)
        self.assertTrue(r.branch.startswith("claude/th-tdac-"))
        self.assertEqual(self.store.get(self.change.fingerprint)["pr_url"], r.pr_url)
        # 같은 지문 두 번째는 실행 안 함
        r2 = self.run_it(fake)
        self.assertEqual(r2.reason, "already_ran")
        self.assertEqual(len(fake.calls), 1)

    def test_daily_cap(self):
        self.store.set_review(self.change.fingerprint, "accepted")
        r = self.run_it(FakeRun(stdout="{}"), cap=0)
        self.assertEqual(r.reason, "daily_cap_reached")

    def test_timeout_and_bad_output(self):
        self.store.set_review(self.change.fingerprint, "accepted")
        r = self.run_it(FakeRun(exc=subprocess.TimeoutExpired("claude", 5)))
        self.assertEqual(r.status, "timeout")
        self.assertEqual(self.store.get(self.change.fingerprint)["claude_status"], "timeout")

        other = Change("d", "SG_SGAC", "s", {"y": 2})
        self.store.register(other)
        self.store.set_review(other.fingerprint, "accepted")
        r = runner.run_for_change(other.to_dict(), self.store, self.tmp, "SG", claude_bin="x", runner=FakeRun(
            stdout="not json", returncode=1), today=self.day)
        self.assertEqual(r.status, "error")

    def test_build_argv_refuses_bare(self):
        with self.assertRaises(ValueError):
            runner.build_argv("claude", "p", ["Read", "--bare"])
        with self.assertRaises(ValueError):
            runner.build_argv("--dangerously-skip-permissions", "p", ["Read"])

    def test_branch_name(self):
        self.assertEqual(runner.branch_name("notice:th_imm", "0123456789" + "0" * 54), "claude/notice-th-imm-01234567")


if __name__ == "__main__":
    unittest.main()
