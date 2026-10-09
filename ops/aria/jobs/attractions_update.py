"""관광지 주간 자동 갱신 (사장님 결정 2026-10-09 ①: 검사 통과하면 자동 반영, 서명은 GitHub Actions).

attractions_watch 가 등록한 변경(Change) 가운데 아직 처리하지 않은 것을 나라별로 묶어 한 번에 처리한다.

나라 하나의 흐름
  1. 묶음 지문(바뀐 변경 지문들의 해시)을 등록 — 같은 묶음은 한 번만.
  2. 상한 확인: CLAUDE_DAILY_CAP(하루, run_daily 와 같이 센다) + ATTRACTIONS_WEEKLY_CAP(ISO 주). 넘으면 다음으로 미룸.
  3. 저장소를 건드리지 않게 **별도 git worktree**(origin/main 기준, 브랜치 aria/attractions-<cc>-<날짜>-<지문8>)를 만든다.
  4. Claude Code 헤드리스(runners.claude_headless.run_prompt)가 새 스냅샷만 근거로 packs/drafts/<CC>/attractions.json 의
     그 관광지 사실 칸을 고치고 증거 extract.json 에 글자 그대로 인용을 남긴다. 웹·git·서명 도구는 주지 않는다.
  5. 결정적 검사: 바뀐 파일(작업본만) → 범위 검사(tools/attractions/auto_update_guard.py) → `build_attractions.py check <CC>`
     → `build_attractions.py record <CC> --ids …`(인용 대조·copycheck 통과 기록, packs/curation).
  6. 모두 통과하면 커밋 → 푸시 → GitHub REST 로 PR + 라벨 attractions-auto. 실패하면 PR 없이 텔레그램 알림만.
  7. worktree 정리(성공·실패 모두).

ARIA 에는 서명 키가 없다. 서명·머지·배포는 attractions-auto.yml(GitHub Actions secrets)만 한다.
명령 실행(cmd_runner)·Claude 실행(claude_runner)·GitHub(github)은 주입한다 — 테스트는 모두 가짜.
"""
from __future__ import annotations

import datetime as _dt
import importlib.util
import json
import pathlib
import shutil
import subprocess
import sys
from dataclasses import dataclass, field
from typing import Callable, Optional

from ..approvals import telegram_cmd
from ..config import REPO_ROOT, Config
from ..models import Change
from ..runners import claude_headless
from .attractions_watch import DETECTOR as WATCH_DETECTOR

DETECTOR = "attractions_update"
CHANGE_INFO_PREFIX = "att_change:"
DIFF_LINES_IN_PROMPT = 30

ALLOWED_TOOLS = [
    "Read", "Edit", "Write", "Glob", "Grep",
    "Bash(python tools/attractions/build_attractions.py check *)",
]
# 근거는 새 스냅샷뿐 — 웹은 막는다. git·배포·서명은 ARIA·CI 가 한다 [재확인: --disallowedTools 규칙 문법]
DISALLOWED_TOOLS = ["WebFetch", "WebSearch", "Bash(git *)", "Bash(gh *)", "Bash(firebase *)", "Bash(npx *)"]

PROMPT_TEMPLATE = """레디포트 관광지 주간 자동 갱신 (ARIA 요청 — 사장님 결정 2026-10-09: 검사를 통과하면 자동 반영). 묶음 지문 {fp}.

나라: {cc}
고칠 관광지: {ids}
고칠 파일: packs/drafts/{cc}/attractions.json (이 파일의 위 관광지만)
증거 폴더(저장소 밖): {evidence_dir}/{cc}/<관광지 id>/  — 인용 기록은 그 안의 extract.json

공식 페이지가 바뀐 곳 (새 스냅샷 = 이번 갱신의 유일한 근거):
{items}

할 일
1. 위 새 스냅샷 파일을 읽고, 위 관광지의 사실 칸(facts·status·seasonal·tips_ko·tags·access·address_local·claims·risk)이
   새 글과 맞는지 확인한다.
2. 새 글과 달라진 사실만 고친다. 고친 칸의 last_verified 는 {today}.
   고친 사실마다 그 관광지의 extract.json 에 {{"field", "value", "quote", "source", "snapshot"}} 항목을 넣는다(같은 field 의 옛 항목은 바꾼다).
   - quote: 새 스냅샷 글에서 **글자 그대로** 복사한 구절. 번역·요약·고쳐 쓰기 금지. 공백만 달라도 된다.
   - snapshot: 스냅샷 파일 이름만(예: {example_snapshot}). source: 그 스냅샷의 출처 id(attractions.json sources 의 id).
   - field 형식: claims.c1 · tips_ko.0 · tags.<태그 id> · facts · access · address_local · status · seasonal.0
3. 스냅샷에 근거가 없는 값은 넣지 않는다(지어내지 않는다). 영업시간·요금 숫자는 싣지 않는다(결정 D4: 쉬는 요일·예약·유료/무료만).
   휴관·공사·폐쇄가 새로 나오면 status.value(temp_closed 또는 partial)와 note_ko 를 근거 인용과 함께 고친다.
4. 바꾸면 안 되는 것: 다른 관광지, 관광지 추가·삭제·순서, 이름·지역·종류·좌표·summary_ko·body_ko·경보(advisory)·rank·google_place_id,
   sources 의 기존 항목(새 사실 출처가 꼭 필요하면 use=facts, https 주소로 덧붙이기만), packs/drafts/{cc}/attractions.json 밖의 저장소 파일.
5. 고친 뒤 `python tools/attractions/build_attractions.py check {cc}` 로 확인한다.
   git·PR·서명·배포·Remote Config 는 하지 않는다(ARIA 와 GitHub Actions 가 한다). 웹 검색·다른 사이트 열기 금지.
6. 쉬운 말(중1 수준), 평가어 금지. 개인정보를 어디에도 넣지 않는다.
7. 바꿀 사실이 없으면 아무 파일도 고치지 않는다.
마지막 응답은 한 줄: CHANGED: <id,id> 또는 NO_CHANGE
"""


# ---------------- 명령 실행 ----------------

@dataclass
class CmdResult:
    returncode: int
    output: str = ""


def subprocess_cmd(argv: list[str], cwd, timeout: float = 600) -> CmdResult:  # pragma: no cover - 실제 실행
    try:
        p = subprocess.run(argv, cwd=str(cwd), capture_output=True, text=True, encoding="utf-8", errors="replace",
                           timeout=timeout, shell=False)
    except (OSError, subprocess.TimeoutExpired) as e:
        return CmdResult(1, f"{type(e).__name__}")
    return CmdResult(p.returncode, ((p.stdout or "") + (p.stderr or ""))[-4000:])


def _guard_module():
    path = REPO_ROOT / "tools" / "attractions" / "auto_update_guard.py"
    spec = importlib.util.spec_from_file_location("readyport_auto_update_guard", path)
    mod = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(mod)
    return mod


# ---------------- 변경 정보 ----------------

def remember_change(store, change: Change, snapshots: list[str]) -> None:
    """watch 가 등록한 변경의 처리 정보(나라·관광지·스냅샷 이름·바뀐 줄 일부)를 kv 에 둔다. 공개 페이지 글뿐."""
    d = change.diff or {}
    store.set_value(CHANGE_INFO_PREFIX + change.fingerprint, {
        "cc": d.get("cc"), "ids": d.get("ids", []), "source": d.get("source"), "url": d.get("url"),
        "snapshot": pathlib.Path(snapshots[0]).name if snapshots else "",
        "closure_new": d.get("closure_new", []), "diff_head": (d.get("unified_diff") or [])[:DIFF_LINES_IN_PROMPT],
    })


def pending_by_country(store) -> dict[str, list[dict]]:
    out: dict[str, list[dict]] = {}
    for row in store.list_pending(WATCH_DETECTOR, "att:"):
        info = store.get_value(CHANGE_INFO_PREFIX + row["fingerprint"])
        if not info or not info.get("cc"):
            continue
        out.setdefault(info["cc"], []).append({**row, "info": info})
    return out


def iso_week(today: _dt.date) -> str:
    y, w, _ = today.isocalendar()
    return f"{y}-W{w:02d}"


def build_prompt(cc: str, fp: str, rows: list[dict], evidence_dir: pathlib.Path, today: _dt.date) -> str:
    items, example = [], "aria_<출처>_<해시>.txt"
    for r in rows:
        info = r["info"]
        snap = info.get("snapshot") or "(없음)"
        if info.get("snapshot"):
            example = info["snapshot"]
        head = "\n".join("      " + str(x)[:300] for x in info.get("diff_head", []))
        items.append(f"- 출처 {info.get('source')} {info.get('url')}\n"
                     f"  관광지: {', '.join(info.get('ids', []))}\n"
                     f"  새 스냅샷: {evidence_dir}/{cc}/<관광지 id>/{snap}\n"
                     + (f"  휴관·공사 표현 늘어남: {', '.join(info['closure_new'])}\n" if info.get("closure_new") else "")
                     + f"  바뀐 줄(일부):\n{head}")
    ids = sorted({i for r in rows for i in r["info"].get("ids", [])})
    return PROMPT_TEMPLATE.format(fp=fp, cc=cc, ids=", ".join(ids), evidence_dir=evidence_dir, items="\n".join(items),
                                  today=today.isoformat(), example_snapshot=example)


def parse_porcelain(out: str) -> list[str]:
    files = []
    for line in (out or "").splitlines():
        if len(line) < 4:
            continue
        path = line[3:].strip().strip('"')
        if " -> " in path:
            path = path.split(" -> ", 1)[1]
        files.append(path.replace("\\", "/"))
    return files


@dataclass
class CountryOutcome:
    cc: str
    status: str                  # pr_opened / no_change / failed / deferred / dry_run / skipped
    ids: list[str] = field(default_factory=list)
    step: str = ""
    detail: str = ""
    pr_url: str = ""
    fingerprint: str = ""

    def to_dict(self) -> dict:
        return dict(self.__dict__)


def process_country(cfg: Config, store, cc: str, rows: list[dict], *, cmd_runner: Callable[..., CmdResult],
                    claude_runner, github, notifier, today: _dt.date, dry_run: bool,
                    python_bin: str = sys.executable) -> CountryOutcome:
    fps = sorted(r["fingerprint"] for r in rows)
    ids = sorted({i for r in rows for i in r["info"].get("ids", [])})
    batch = Change(DETECTOR, f"att_update:{cc}", f"[관광지] {cc} 주간 갱신 묶음 ({len(fps)}건)",
                   {"cc": cc, "changes": fps, "ids": ids})
    fp = batch.fingerprint
    if dry_run:
        return CountryOutcome(cc, "dry_run", ids, fingerprint=fp)
    store.register(batch)
    ok, why = store.claim_auto_run(fp, cfg.claude_daily_cap,
                                   [(f"att_week:{iso_week(today)}", cfg.attractions_weekly_cap)], today=today)
    if not ok:
        if why == "already_ran":
            for f in fps:
                store.record_claude_result(f, "batched")
            return CountryOutcome(cc, "skipped", ids, "claim", why, fingerprint=fp)
        return CountryOutcome(cc, "deferred", ids, "claim", why, fingerprint=fp)

    branch = f"aria/attractions-{cc.lower()}-{today:%Y%m%d}-{fp[:8]}"
    wt = pathlib.Path(cfg.data_dir) / "worktrees" / f"att-{cc.lower()}-{fp[:8]}"
    repo = pathlib.Path(cfg.repo_root)
    drafts_rel = f"packs/drafts/{cc}/attractions.json"
    outcome = CountryOutcome(cc, "failed", ids, fingerprint=fp)

    def fail(step: str, detail: str = "") -> CountryOutcome:
        outcome.status, outcome.step, outcome.detail = "failed", step, detail[-1500:]
        return outcome

    created = False
    try:
        r = cmd_runner(["git", "fetch", "origin", "main"], repo)
        if r.returncode != 0:
            return fail("git_fetch", r.output)
        r = cmd_runner(["git", "worktree", "add", "-b", branch, str(wt), "origin/main"], repo)
        if r.returncode != 0:
            return fail("git_worktree", r.output)
        created = True
        draft_path = wt / drafts_rel
        if not draft_path.is_file():
            return fail("no_drafts", f"{drafts_rel} 가 origin/main 에 없음")
        base_doc = json.loads(draft_path.read_text(encoding="utf-8"))

        prompt = build_prompt(cc, fp, rows, pathlib.Path(cfg.attractions_evidence_dir), today)
        res = claude_headless.run_prompt(
            prompt, cwd=wt, claude_bin=cfg.claude_bin, timeout_sec=cfg.claude_timeout_sec,
            allowed_tools=ALLOWED_TOOLS, disallowed_tools=DISALLOWED_TOOLS,
            add_dirs=[str(pathlib.Path(cfg.attractions_evidence_dir) / cc)], via_stdin=True, runner=claude_runner)
        store.record_claude_result(fp, f"claude_{res.status}")
        if res.status != "ok":
            return fail(f"claude_{res.status}", res.reason)

        st = cmd_runner(["git", "status", "--porcelain", "--untracked-files=all"], wt)
        files = parse_porcelain(st.output)
        if not files:
            outcome.status = "no_change"
            return outcome
        guard = _guard_module()
        if files != [drafts_rel]:
            return fail("scope_files", "Claude 가 작업본 밖을 고침: " + ", ".join(f for f in files if f != drafts_rel))
        head_doc = json.loads(draft_path.read_text(encoding="utf-8"))
        changed_ids, problems = guard.compare_docs(base_doc, head_doc)
        extra = sorted(set(changed_ids) - set(ids))
        if extra:
            problems.append(f"이번 변경과 관계없는 관광지를 고침: {', '.join(extra)}")
        if problems:
            return fail("scope_fields", "; ".join(problems))
        outcome.ids = changed_ids
        tool = "tools/attractions/build_attractions.py"
        r = cmd_runner([python_bin, tool, "check", cc], wt)
        if r.returncode != 0:
            return fail("build_check", r.output)
        r = cmd_runner([python_bin, tool, "record", cc, "--ids", ",".join(changed_ids),
                        "--evidence-dir", str(cfg.attractions_evidence_dir)], wt)
        if r.returncode != 0:
            return fail("quotes_copycheck", r.output)
        files = parse_porcelain(cmd_runner(["git", "status", "--porcelain", "--untracked-files=all"], wt).output)
        _, file_problems = guard.check_files(files)
        if file_problems:
            return fail("scope_files", "; ".join(file_problems))
        if cmd_runner(["git", "add", "--", *files], wt).returncode != 0:
            return fail("git_add")
        msg = (f"관광지 주간 자동 갱신: {cc} ({', '.join(changed_ids)})\n\n"
               f"공식 페이지 변경 감지(ARIA attractions_watch) → 새 스냅샷 인용으로 사실 칸 갱신.\n지문 {fp}")
        r = cmd_runner(["git", "commit", "-m", msg], wt)
        if r.returncode != 0:
            return fail("git_commit", r.output)
        r = cmd_runner(["git", "push", "-u", "origin", branch], wt)
        if r.returncode != 0:
            return fail("git_push", r.output)
        published = (wt / "packs" / "src" / cc / "attractions.json").is_file()
        urls = sorted({r_["info"].get("url") for r_ in rows if r_["info"].get("url")})
        body = "\n".join([
            f"ARIA 관광지 주간 자동 갱신 — {cc}: {', '.join(changed_ids)}",
            "",
            "사장님 결정(2026-10-09): 검사를 통과하면 자동 반영. 서명은 GitHub Actions(attractions-auto.yml)가 한다.",
            "",
            "- 근거: 아래 공식 페이지의 새 글(스냅샷은 운영자 PC 증거 폴더, 인용은 글자 그대로 기계 대조 통과)",
            *[f"  - {u}" for u in urls],
            "- ARIA 쪽 검사: 범위(auto_update_guard) · build_attractions check · record(인용 대조·copycheck) 통과",
            "- 공개 전 나라면 작업본만 바뀐다(서명·배포 없음)." if not published else
            "- 공개된 나라: CI 가 apply-drafts 로 이 곳만 원본에 옮겨 서명 → 머지 → 배포(deploy-packs) → attractions_version",
            "",
            f"지문: {fp}",
        ])
        pr = github.create_pr(branch, "main", f"관광지 주간 자동 갱신: {cc} ({len(changed_ids)}곳)", body)
        github.add_labels(pr.number, ["attractions-auto"])
        store.record_claude_result(fp, "ok", pr.url)
        outcome.status, outcome.pr_url = "pr_opened", pr.url
        outcome.detail = "published" if published else "drafts_only"
        return outcome
    except Exception as e:  # noqa: BLE001 — 한 나라 실패가 다른 나라를 막지 않게
        return fail("exception", f"{type(e).__name__}: {e}"[:500])
    finally:
        for f in fps:
            store.record_claude_result(f, "batched")
        if outcome.status == "failed":
            store.record_claude_result(fp, f"failed:{outcome.step}"[:50])
        if created:
            cmd_runner(["git", "worktree", "remove", "--force", str(wt)], repo)
            cmd_runner(["git", "branch", "-D", branch], repo)
            if wt.exists():  # 남은 폴더(잠긴 파일 등)
                shutil.rmtree(wt, ignore_errors=True)


def run_update(cfg: Config, store, *, cmd_runner=subprocess_cmd, claude_runner=subprocess.run, github=None,
               notifier=None, today: Optional[_dt.date] = None, dry_run: bool = True,
               countries: Optional[list[str]] = None, skip: Optional[set[str]] = None,
               python_bin: str = sys.executable) -> dict:
    """나라별 처리. skip = 이번 주 확인이 아직 끝나지 않은 나라(deferred 남음) — 묶음을 쪼개지 않으려고 다음 실행으로."""
    today = today or _dt.datetime.now().astimezone().date()
    pending = pending_by_country(store)
    out = []
    for cc in sorted(pending):
        if countries and cc not in countries:
            continue
        if skip and cc in skip:
            out.append(CountryOutcome(cc, "deferred", step="watch_incomplete").to_dict())
            continue
        if github is None and not dry_run:
            out.append(CountryOutcome(cc, "failed", step="github_not_configured").to_dict())
            continue
        o = process_country(cfg, store, cc, pending[cc], cmd_runner=cmd_runner, claude_runner=claude_runner,
                            github=github, notifier=notifier, today=today, dry_run=dry_run, python_bin=python_bin)
        out.append(o.to_dict())
        if notifier is not None and not dry_run:
            try:
                if o.status == "pr_opened":
                    notifier.send(telegram_cmd.format_attractions_pr(cc, o.ids, o.pr_url, o.detail == "published"))
                elif o.status == "failed":
                    ev = next((r["evidence_path"] for r in pending[cc] if r.get("evidence_path")), "")
                    notifier.send(telegram_cmd.format_attractions_failed(cc, o.ids, o.step, o.detail, ev))
            except Exception:  # noqa: BLE001 — 알림 실패는 결과에만 남는다
                pass
    return {"countries": out}
