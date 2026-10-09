"""관광지 주간 확인·자동 갱신 테스트 (가짜 fetcher·가짜 git·가짜 Claude·가짜 GitHub — 네트워크 없음, 가짜 자료만)."""
import datetime as dt
import json
import pathlib
import shutil
import sys
import unittest
from types import SimpleNamespace

from ops.aria import run_weekly as rw
from ops.aria.actions import attraction_flags
from ops.aria.actions.github_pr import GitHubClient, GitHubError, PullRequest, parse_credential
from ops.aria.fingerprint_store import FingerprintStore
from ops.aria.jobs import attractions_update as au
from ops.aria.jobs import attractions_watch as aw
from ops.aria.models import STATUS_BASELINE, STATUS_CHANGED, STATUS_ERROR, STATUS_MANUAL, STATUS_UNCHANGED
from ops.aria.net import Response
from ops.aria.robots import RobotsCache
from ops.aria.tests.helpers import FakeFetcher, MemFirestore, TempDirCase, html_resp, json_resp, net_error

TODAY = dt.date(2026, 10, 12)
NOW = dt.datetime(2026, 10, 12, 6, 0, tzinfo=dt.timezone.utc)
UA = "ReadyPort-ARIA/test"
ROBOTS_OK = Response(200, b"User-agent: *\nDisallow: /private\n", {"Content-Type": "text/plain"})


def fake_place(pid, region="zz_one", src="facts_a"):
    return {"id": pid, "names": {"ko": "샘플", "en": "Sample", "source": "wd"}, "region": region, "category": "heritage",
            "official_url": f"https://place.example.test/{pid}", "source": "wd",
            "facts": {"kind": "facility", "entry": "paid", "regular_closed": ["mon"], "source": src,
                      "last_verified": "2026-10-01"},
            "status": {"value": "open", "source": src, "last_verified": "2026-10-01"},
            "summary_ko": "가짜 장소예요.", "body_ko": ["가짜 설명이에요."]}


def fake_doc(release="draft", places=("sample-one", "sample-two")):
    return {"doc_type": "attractions", "country": "JP", "version": "2026.10.01-1", "release": release,
            "regions": [{"id": "zz_one"}],
            "sources": [{"id": "wd", "name": "Wikidata", "url": "https://www.wikidata.org/", "use": "skeleton"},
                        {"id": "facts_a", "name": "가짜 공식", "url": "https://facts.example.test/a", "use": "facts"},
                        {"id": "adv", "name": "경보", "url": "https://adv.example.test/", "use": "advisory"}],
            "attractions": [fake_place(p) for p in places]}


def write_repo(root: pathlib.Path, draft=None, src=None):
    if draft is not None:
        (root / "packs" / "drafts" / "JP").mkdir(parents=True, exist_ok=True)
        (root / "packs" / "drafts" / "JP" / "attractions.json").write_text(json.dumps(draft, ensure_ascii=False, indent=2),
                                                                            encoding="utf-8")
    if src is not None:
        (root / "packs" / "src" / "JP").mkdir(parents=True, exist_ok=True)
        (root / "packs" / "src" / "JP" / "attractions.json").write_text(json.dumps(src, ensure_ascii=False, indent=2),
                                                                         encoding="utf-8")


class RobotsTest(unittest.TestCase):
    def test_rules_and_cache(self):
        f = FakeFetcher({"https://a.example.test/robots.txt": ROBOTS_OK,
                         "https://b.example.test/robots.txt": html_resp("", 404),
                         "https://c.example.test/robots.txt": html_resp("", 401),
                         "https://d.example.test/robots.txt": html_resp("<div class='cf-turnstile'></div>"),
                         "https://e.example.test/robots.txt": net_error()})
        r = RobotsCache(f, UA)
        self.assertTrue(r.allowed("https://a.example.test/spot"))
        self.assertFalse(r.allowed("https://a.example.test/private/x"))
        self.assertTrue(r.allowed("https://b.example.test/x"))
        self.assertFalse(r.allowed("https://c.example.test/x"))
        self.assertIsNone(r.allowed("https://d.example.test/x"))
        self.assertIsNone(r.allowed("https://e.example.test/x"))
        self.assertEqual(r.fetches, 5)
        r.allowed("https://a.example.test/other")
        self.assertEqual(r.fetches, 5)      # 사이트마다 한 번


class CollectTargetsTest(unittest.TestCase, TempDirCase):
    def test_fact_sources_and_official_urls_deduped(self):
        tmp = self.make_tmp()
        src = fake_doc("published", places=("sample-one",))
        write_repo(tmp, draft=fake_doc(), src=src)
        self.assertEqual(aw.attraction_countries(tmp), ["JP"])
        ts = aw.collect_targets(tmp, "JP")
        urls = {t.url: t for t in ts}
        self.assertIn("https://facts.example.test/a", urls)
        self.assertEqual(urls["https://facts.example.test/a"].ids, ["sample-one", "sample-two"])
        self.assertEqual(urls["https://facts.example.test/a"].unit, "att:JP:facts_a")
        self.assertIn("https://place.example.test/sample-two", urls)
        # skeleton(위키데이터)·advisory 출처는 보지 않는다
        self.assertNotIn("https://www.wikidata.org/", urls)
        self.assertNotIn("https://adv.example.test/", urls)


class CheckTargetTest(unittest.TestCase, TempDirCase):
    def setUp(self):
        self.tmp = self.make_tmp()
        self.store = FingerprintStore(":memory:")
        self.addCleanup(self.store.close)
        self.target = aw.WatchTarget("JP", "https://facts.example.test/a", "facts_a", ["sample-one", "sample-two"])
        self.ev = self.tmp / "evidence"

    def check(self, page, robots=ROBOTS_OK, persist=True):
        f = FakeFetcher({"https://facts.example.test/robots.txt": robots, "https://facts.example.test/a": page})
        return aw.check_target(self.target, self.store, f, RobotsCache(f, UA), evidence_dir=self.ev, persist=persist,
                               user_agent=UA, now=NOW), f

    def test_baseline_unchanged_changed_with_snapshot_and_closure(self):
        r, f = self.check(html_resp("<p>월요일 휴관</p><p>입장은 유료예요</p>"))
        self.assertEqual(r.status, STATUS_BASELINE)
        self.assertEqual(f.calls[1]["headers"]["User-Agent"], UA)
        r, _ = self.check(html_resp("<p>월요일   휴관</p>\n<p>입장은 유료예요</p><script>x()</script>"))
        self.assertEqual(r.status, STATUS_UNCHANGED)
        r, _ = self.check(html_resp("<p>월요일 휴관</p><p>공사로 임시 휴관합니다</p>"))
        self.assertEqual(r.status, STATUS_CHANGED)
        ch = r.changes[0]
        self.assertEqual(ch.detector, "attractions_watch")
        self.assertIn("임시 휴관", ch.diff["closure_new"])
        self.assertEqual(ch.diff["ids"], ["sample-one", "sample-two"])
        snaps = r.extra["snapshots"]
        self.assertEqual(len(snaps), 2)
        for s in snaps:
            p = pathlib.Path(s)
            self.assertTrue(p.is_file())
            self.assertTrue(p.name.startswith("aria_facts_a_"))
            self.assertIn("임시 휴관", p.read_text(encoding="utf-8"))
            self.assertTrue(p.with_suffix(".json").is_file())
        self.assertTrue(str(snaps[0]).startswith(str(self.ev / "JP" / "sample-one")))
        # 같은 글을 다시 받으면 unchanged
        r, _ = self.check(html_resp("<p>월요일 휴관</p><p>공사로 임시 휴관합니다</p>"))
        self.assertEqual(r.status, STATUS_UNCHANGED)

    def test_blocked_and_errors_are_not_bypassed(self):
        r, f = self.check(html_resp("<div class='cf-turnstile'></div>"))
        self.assertEqual(r.status, STATUS_MANUAL)
        r, f = self.check(html_resp("", 403))
        self.assertEqual(r.status, STATUS_MANUAL)
        self.assertEqual(len([c for c in f.calls if c["url"].endswith("/a")]), 1)   # 재시도 없음
        r, _ = self.check(html_resp("", 500))
        self.assertEqual(r.status, STATUS_ERROR)
        r, _ = self.check(Response(200, b"%PDF", {"Content-Type": "application/pdf"}))
        self.assertEqual(r.status, STATUS_MANUAL)

    def test_robots_disallow_means_no_fetch(self):
        robots = Response(200, b"User-agent: *\nDisallow: /\n", {"Content-Type": "text/plain"})
        r, f = self.check(html_resp("<p>x</p>"), robots=robots)
        self.assertEqual(r.status, STATUS_MANUAL)
        self.assertEqual([c["url"] for c in f.calls], ["https://facts.example.test/robots.txt"])

    def test_dry_run_saves_nothing(self):
        self.check(html_resp("<p>a</p>"), persist=False)
        self.assertIsNone(self.store.get_value(self.target.key))
        self.assertFalse(self.ev.exists())


class FlagsTest(unittest.TestCase):
    def res(self, unit, status, ids, new=(), now=None, base=None):
        return SimpleNamespace(unit=unit, status=status, extra={"ids": ids, "closure_new": list(new),
                                                               "closure_now": now or {}, "closure_base": base or {}})

    def test_flag_set_and_cleared_when_keywords_go_back(self):
        st = aw.update_flag_state({}, [self.res("att:JP:a", STATUS_CHANGED, ["p1", "p2"], ["휴관"], {"휴관": 2}, {"휴관": 1})])
        self.assertEqual(st, {"p1": {"att:JP:a": 1}, "p2": {"att:JP:a": 1}})
        # 차단·오류로 확인 못 하면 그대로
        st2 = aw.update_flag_state(st, [self.res("att:JP:a", STATUS_MANUAL, ["p1", "p2"])])
        self.assertEqual(st2, st)
        # 아직 늘어난 상태면 유지
        st3 = aw.update_flag_state(st, [self.res("att:JP:a", STATUS_UNCHANGED, ["p1", "p2"], now={"휴관": 2})])
        self.assertEqual(st3, st)
        # 기준선으로 돌아오면 뗀다
        st4 = aw.update_flag_state(st, [self.res("att:JP:a", STATUS_CHANGED, ["p1", "p2"], now={"휴관": 1})])
        self.assertEqual(st4, {})

    def test_sync_flags_allowlist_and_idempotent(self):
        fs = MemFirestore()
        r = attraction_flags.sync_flags(fs, "JP", ["p2", "p1"], NOW)
        self.assertTrue(r.changed)
        self.assertEqual(fs.docs["attraction_flags/JP"], {"ids": ["p1", "p2"], "kind": "check_in_progress", "at": NOW})
        self.assertFalse(attraction_flags.sync_flags(fs, "JP", ["p1", "p2"], NOW).changed)
        self.assertFalse(attraction_flags.sync_flags(MemFirestore(), "VN", [], NOW).changed)   # 문서 없고 빈 목록 → 안 씀
        with self.assertRaises(attraction_flags.NotAllowed):
            attraction_flags.sync_flags(fs, "KR", ["p1"], NOW)
        with self.assertRaises(attraction_flags.NotAllowed):
            attraction_flags.sync_flags(fs, "JP", ["../x"], NOW)
        d = attraction_flags.sync_flags(fs, "JP", [], NOW, dry_run=True)
        self.assertTrue(d.changed and d.dry_run)
        self.assertEqual(fs.docs["attraction_flags/JP"]["ids"], ["p1", "p2"])


# ---------------- 자동 갱신 ----------------

class FakeGit:
    """git·python 명령 흉내. worktree add 는 원본 폴더를 복사한다."""

    def __init__(self, origin: pathlib.Path, check_rc=0, record_rc=0, push_rc=0):
        self.origin = origin
        self.check_rc, self.record_rc, self.push_rc = check_rc, record_rc, push_rc
        self.calls = []

    def __call__(self, argv, cwd, timeout=600):
        self.calls.append((list(argv), str(cwd)))
        a = list(argv)
        if a[:2] == ["git", "worktree"] and a[2] == "add":
            wt = pathlib.Path(a[5])
            shutil.copytree(self.origin, wt)
            return au.CmdResult(0)
        if a[:2] == ["git", "worktree"] and a[2] == "remove":
            shutil.rmtree(a[4], ignore_errors=True)
            return au.CmdResult(0)
        if a[:2] == ["git", "status"]:
            out = []
            for p in sorted(pathlib.Path(cwd).rglob("*")):
                if p.is_file():
                    rel = p.relative_to(cwd).as_posix()
                    o = self.origin / rel
                    if not o.exists():
                        out.append(f"?? {rel}")
                    elif o.read_bytes() != p.read_bytes():
                        out.append(f" M {rel}")
            return au.CmdResult(0, "\n".join(out))
        if a[0] == "git" and a[1] == "push":
            return au.CmdResult(self.push_rc, "push failed" if self.push_rc else "")
        if a[0] == "git":
            return au.CmdResult(0)
        if "check" in a:
            return au.CmdResult(self.check_rc, "실패: 가짜 검사 실패" if self.check_rc else "통과")
        if "record" in a:
            if self.record_rc == 0:
                cur = pathlib.Path(cwd) / "packs" / "curation"
                cur.mkdir(parents=True, exist_ok=True)
                (cur / "JP.quotes.json").write_text("{}\n", encoding="utf-8")
                (cur / "JP.copycheck.json").write_text("{}\n", encoding="utf-8")
            return au.CmdResult(self.record_rc, "실패: 인용이 스냅샷에 글자 그대로 없음" if self.record_rc else "통과")
        raise AssertionError(a)


class FakeClaude:
    """Claude 흉내: cwd 의 작업본에서 지정한 관광지의 칸을 고친다."""

    def __init__(self, edit=None, status_ok=True):
        self.edit = edit
        self.status_ok = status_ok
        self.calls = []

    def __call__(self, argv, **kw):
        self.calls.append((argv, kw))
        if self.edit:
            self.edit(pathlib.Path(kw["cwd"]))
        out = json.dumps({"type": "result", "is_error": not self.status_ok, "result": "CHANGED: sample-one"})
        return SimpleNamespace(stdout=out, stderr="", returncode=0)


def edit_closed_day(wt: pathlib.Path, field="facts", pid="sample-one"):
    p = wt / "packs" / "drafts" / "JP" / "attractions.json"
    d = json.loads(p.read_text(encoding="utf-8"))
    for a in d["attractions"]:
        if a["id"] == pid:
            if field == "facts":
                a["facts"]["regular_closed"] = ["tue"]
            else:
                a[field] = "바꾼 글이에요."
    p.write_text(json.dumps(d, ensure_ascii=False, indent=2), encoding="utf-8")


class FakeGitHub:
    def __init__(self):
        self.prs, self.labels = [], []

    def create_pr(self, head, base, title, body):
        self.prs.append((head, base, title, body))
        return PullRequest(7, "https://github.com/owner/readyport/pull/7")

    def add_labels(self, number, labels):
        self.labels.append((number, labels))


class ListNotifier:
    def __init__(self):
        self.sent = []

    def send(self, text):
        self.sent.append(text)


class UpdateTest(unittest.TestCase, TempDirCase):
    def setUp(self):
        self.tmp = self.make_tmp()
        self.origin = self.tmp / "origin"
        write_repo(self.origin, draft=fake_doc())
        self.cfg = self.make_cfg(self.tmp, claude_bin=sys.executable, claude_daily_cap=3, attractions_weekly_cap=2)
        self.store = FingerprintStore(":memory:")
        self.addCleanup(self.store.close)

    def add_change(self, text="<p>새 안내</p>", ids=("sample-one",)):
        t = aw.WatchTarget("JP", "https://facts.example.test/a", "facts_a", list(ids))
        f = FakeFetcher({"https://facts.example.test/robots.txt": ROBOTS_OK, "https://facts.example.test/a": html_resp("<p>옛 안내</p>")})
        aw.check_target(t, self.store, f, RobotsCache(f, UA), evidence_dir=self.cfg.attractions_evidence_dir, persist=True, now=NOW)
        f = FakeFetcher({"https://facts.example.test/robots.txt": ROBOTS_OK, "https://facts.example.test/a": html_resp(text)})
        r = aw.check_target(t, self.store, f, RobotsCache(f, UA), evidence_dir=self.cfg.attractions_evidence_dir, persist=True, now=NOW)
        rw.register_and_notify({r.unit: r}, self.store, ListNotifier(), persist=True)
        return r

    def upd(self, git=None, claude=None, gh=None, notifier=None, dry_run=False):
        self.git = git or FakeGit(self.origin)
        self.gh = gh or FakeGitHub()
        self.notifier = notifier or ListNotifier()
        return au.run_update(self.cfg, self.store, cmd_runner=self.git, claude_runner=claude or FakeClaude(edit_closed_day),
                             github=self.gh, notifier=self.notifier, today=TODAY, dry_run=dry_run, python_bin="python")

    def test_success_opens_labelled_pr_with_safe_claude_args(self):
        self.add_change()
        claude = FakeClaude(edit_closed_day)
        out = self.upd(claude=claude)["countries"][0]
        self.assertEqual(out["status"], "pr_opened", out)
        self.assertEqual(out["ids"], ["sample-one"])
        self.assertEqual(out["detail"], "drafts_only")
        head, base, title, body = self.gh.prs[0]
        self.assertRegex(head, r"^aria/attractions-jp-20261012-[0-9a-f]{8}$")
        self.assertEqual(base, "main")
        self.assertIn("https://facts.example.test/a", body)
        self.assertEqual(self.gh.labels, [(7, ["attractions-auto"])])
        argv, kw = claude.calls[0]
        self.assertNotIn("--bare", argv)
        self.assertEqual(argv[1], "-p")
        self.assertIn("--disallowedTools", argv)
        self.assertIn("WebFetch", argv[argv.index("--disallowedTools") + 1])
        self.assertIn("--add-dir", argv)
        prompt = kw["input"]
        self.assertNotIn(prompt, argv)                     # 지시문은 표준 입력으로
        for must in ("글자 그대로", "packs/drafts/JP/attractions.json", "sample-one", "aria_facts_a_", "지어내지"):
            self.assertIn(must, prompt)
        cmds = [c[0] for c in self.git.calls]
        self.assertIn(["python", "tools/attractions/build_attractions.py", "check", "JP"], cmds)
        rec = next(c for c in cmds if "record" in c)
        self.assertEqual(rec[rec.index("--ids") + 1], "sample-one")
        add = next(c for c in cmds if c[:2] == ["git", "add"])
        self.assertEqual(sorted(add[3:]), ["packs/curation/JP.copycheck.json", "packs/curation/JP.quotes.json",
                                           "packs/drafts/JP/attractions.json"])
        self.assertTrue(any(c[:3] == ["git", "push", "-u"] for c in cmds))
        self.assertTrue(any(c[:3] == ["git", "worktree", "remove"] for c in cmds))
        self.assertFalse(any("main" == c[-1] and c[:2] == ["git", "push"] for c in cmds))
        self.assertIn("pull/7", self.notifier.sent[0])
        # 같은 묶음은 다시 하지 않는다
        self.assertEqual(self.upd()["countries"], [])

    def test_check_failure_notifies_and_makes_no_pr(self):
        self.add_change()
        out = self.upd(git=FakeGit(self.origin, check_rc=1))["countries"][0]
        self.assertEqual((out["status"], out["step"]), ("failed", "build_check"))
        self.assertEqual(self.gh.prs, [])
        self.assertIn("멈춤", self.notifier.sent[0])
        self.assertIn("build_check", self.notifier.sent[0])
        self.assertFalse(any(c[0][:2] == ["git", "push"] for c in self.git.calls))

    def test_quote_failure_stops(self):
        self.add_change()
        out = self.upd(git=FakeGit(self.origin, record_rc=1))["countries"][0]
        self.assertEqual(out["step"], "quotes_copycheck")
        self.assertEqual(self.gh.prs, [])

    def test_scope_guards(self):
        self.add_change()
        out = self.upd(claude=FakeClaude(lambda wt: edit_closed_day(wt, field="summary_ko")))["countries"][0]
        self.assertEqual(out["step"], "scope_fields")

    def test_other_file_or_other_place_rejected(self):
        self.add_change()

        def touch_other(wt):
            (wt / "packs" / "src").mkdir(parents=True, exist_ok=True)
            (wt / "packs" / "src" / "x.json").write_text("{}", encoding="utf-8")
            edit_closed_day(wt)
        out = self.upd(claude=FakeClaude(touch_other))["countries"][0]
        self.assertEqual(out["step"], "scope_files")
        # 새 변경(다른 지문) → 이번에는 관계없는 관광지를 고침
        self.add_change(text="<p>또 바뀐 안내</p>")
        out = self.upd(claude=FakeClaude(lambda wt: edit_closed_day(wt, pid="sample-two")))["countries"][0]
        self.assertEqual(out["step"], "scope_fields")
        self.assertIn("관계없는", out["detail"])

    def test_no_change_and_caps(self):
        self.add_change()
        out = self.upd(claude=FakeClaude(None))["countries"][0]
        self.assertEqual(out["status"], "no_change")
        self.add_change(text="<p>둘째</p>")
        self.add_change(text="<p>셋째</p>")
        self.assertEqual(self.upd()["countries"][0]["status"], "pr_opened")
        self.add_change(text="<p>넷째</p>")
        out = self.upd()["countries"][0]
        self.assertEqual((out["status"], out["detail"]), ("deferred", "cap_reached:att_week:2026-W42"))
        # 미룬 변경은 남아 있다(다음 주에)
        self.assertTrue(self.store.list_pending("attractions_watch", "att:"))

    def test_dry_run_and_incomplete_watch(self):
        self.add_change()
        out = self.upd(dry_run=True)["countries"][0]
        self.assertEqual(out["status"], "dry_run")
        self.assertEqual(self.git.calls, [])
        out = au.run_update(self.cfg, self.store, cmd_runner=FakeGit(self.origin), github=FakeGitHub(), today=TODAY,
                            dry_run=False, skip={"JP"})["countries"][0]
        self.assertEqual(out["status"], "deferred")


class GitHubClientTest(unittest.TestCase):
    def test_pr_and_labels_with_token_from_provider(self):
        f = FakeFetcher({("POST", "https://api.github.com/repos/owner/readyport/pulls"):
                         json_resp({"number": 3, "html_url": "https://github.com/owner/readyport/pull/3"}, 201),
                         ("POST", "https://api.github.com/repos/owner/readyport/issues/3/labels"): json_resp([], 200)})
        gh = GitHubClient("owner/readyport", lambda: "tok-test", f)
        pr = gh.create_pr("aria/attractions-jp-20261012-abcdef12", "main", "t", "b")
        gh.add_labels(pr.number, ["attractions-auto"])
        self.assertEqual(pr.url, "https://github.com/owner/readyport/pull/3")
        self.assertEqual(f.calls[0]["headers"]["Authorization"], "Bearer tok-test")
        self.assertEqual(json.loads(f.calls[1]["data"]), {"labels": ["attractions-auto"]})
        with self.assertRaises(GitHubError):
            gh.create_pr("main", "main", "t", "b")
        with self.assertRaises(GitHubError):
            GitHubClient("owner/readyport", lambda: None, f).create_pr("aria/x", "main", "t", "b")
        self.assertEqual(parse_credential("protocol=https\nhost=github.com\nusername=x\npassword=abc\n"), "abc")


class RunWeeklyTest(unittest.TestCase, TempDirCase):
    def test_watch_registers_flags_and_state(self):
        tmp = self.make_tmp()
        write_repo(tmp, draft=fake_doc(places=("sample-one",)))
        cfg = self.make_cfg(tmp)
        store = FingerprintStore(":memory:")
        self.addCleanup(store.close)
        pages = {"a": "<p>월요일 휴관</p>"}
        routes = {"https://facts.example.test/robots.txt": ROBOTS_OK,
                  "https://place.example.test/robots.txt": html_resp("", 404),
                  "https://facts.example.test/a": lambda *a: html_resp(pages["a"]),
                  "https://place.example.test/sample-one": html_resp("<div class='g-recaptcha'></div>")}
        fs = MemFirestore()
        n = ListNotifier()
        s1 = rw.run_weekly(cfg, fetcher=FakeFetcher(routes), step="watch", dry_run=False, notifier=n, firestore=fs,
                           store=store, today=TODAY, now=NOW, sleep=lambda s: None)
        self.assertEqual(s1["watch"]["units"]["att:JP:facts_a"]["status"], STATUS_BASELINE)
        self.assertEqual(s1["watch"]["units"]["att:JP:official-sample-one"]["status"], STATUS_MANUAL)
        pages["a"] = "<p>월요일 휴관</p><p>공사로 임시 휴관</p>"
        s2 = rw.run_weekly(cfg, fetcher=FakeFetcher(routes), step="watch", dry_run=False, notifier=n, firestore=fs,
                           store=store, today=TODAY, now=NOW, sleep=lambda s: None)
        self.assertEqual(s2["watch"]["new_changes"], 1)
        self.assertEqual(fs.docs["attraction_flags/JP"]["ids"], ["sample-one"])
        self.assertTrue(any("공식 페이지 변경 1건" in t for t in n.sent))
        self.assertTrue(any("확인 중" in t for t in n.sent))
        state = rw.load_week_state(cfg, TODAY)
        self.assertEqual(state["att:JP:facts_a"], STATUS_CHANGED)
        self.assertEqual(rw.incomplete_countries(state, list(state)), set())
        self.assertEqual(rw.incomplete_countries({"att:JP:x": "deferred"}, ["att:JP:x"]), {"JP"})
        # 시험 실행은 저장·쓰기 없음
        before = dict(fs.docs)
        pages["a"] = "<p>전혀 다른 글</p>"
        rw.run_weekly(cfg, fetcher=FakeFetcher(routes), step="watch", dry_run=True, firestore=fs, store=store,
                      today=TODAY, now=NOW, sleep=lambda s: None)
        self.assertEqual(fs.docs, before)


if __name__ == "__main__":
    unittest.main()
