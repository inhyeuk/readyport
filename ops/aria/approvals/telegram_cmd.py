"""CMD:v1 승인 명령 파서·메시지 형식.

명령 형식 (한 줄):
    CMD:v1 {"action":"approve","fp":"<64자리 지문>"}
action 과 필드:
    approve  fp(필수)                     — 이 변경을 Claude 로 고쳐 PR 을 만들어도 된다(머지는 GitHub 에서 따로)
    reject   fp(필수), reason(선택)        — 처리하지 않음
    status   fp(선택)                     — 상태 보기(없으면 최근 목록)
    kill     form_id(필수)                — 자동 입력 끄기(안전한 방향)
    unkill   form_id(필수)                — 자동 입력 다시 켜기(위험한 방향, 사람 명령일 때만)
모르는 필드, 중복 키, 잘못된 형식은 모두 거절한다.

주의: 이 모듈은 글만 해석한다. "보낸 사람이 운영자인지"는 ARIA 봇 쪽에서
텔레그램 chat_id/user_id 허용 목록으로 반드시 확인한 뒤에 넘겨야 한다.
"""
from __future__ import annotations

import json
import re
from dataclasses import dataclass
from typing import Any, Optional

PREFIX = "CMD:v1"
MAX_LEN = 1000
TELEGRAM_LIMIT = 4000  # 텔레그램 한 메시지 4096자 제한보다 조금 작게

FP_RE = re.compile(r"^[0-9a-f]{64}$")
FORM_ID_RE = re.compile(r"^[A-Z]{2}_[A-Z0-9_]{2,40}$")

SPEC: dict[str, dict[str, set[str]]] = {
    "approve": {"required": {"fp"}, "optional": set()},
    "reject": {"required": {"fp"}, "optional": {"reason"}},
    "status": {"required": set(), "optional": {"fp"}},
    "kill": {"required": {"form_id"}, "optional": set()},
    "unkill": {"required": {"form_id"}, "optional": set()},
}


class CommandError(ValueError):
    pass


@dataclass(frozen=True)
class Command:
    action: str
    fp: Optional[str] = None
    form_id: Optional[str] = None
    reason: Optional[str] = None


def _no_dupes(pairs: list[tuple[str, Any]]) -> dict:
    d: dict = {}
    for k, v in pairs:
        if k in d:
            raise CommandError(f"키가 두 번 나옴: {k}")
        d[k] = v
    return d


def parse_command(text: str) -> Command:
    if not isinstance(text, str):
        raise CommandError("글이 아님")
    text = text.strip()
    if len(text) > MAX_LEN:
        raise CommandError("명령이 너무 김")
    if not text.startswith(PREFIX):
        raise CommandError("CMD:v1 로 시작해야 함")
    rest = text[len(PREFIX):]
    if not rest or not rest[0].isspace():
        raise CommandError("CMD:v1 뒤에 공백과 JSON 이 와야 함")
    try:
        obj = json.loads(rest.strip(), object_pairs_hook=_no_dupes)
    except json.JSONDecodeError as e:
        raise CommandError(f"JSON 형식 오류: {e.msg}") from None
    if not isinstance(obj, dict):
        raise CommandError("JSON 객체여야 함")
    action = obj.get("action")
    if not isinstance(action, str) or action not in SPEC:
        raise CommandError(f"모르는 action: {action!r}")
    spec = SPEC[action]
    fields = set(obj) - {"action"}
    unknown = fields - spec["required"] - spec["optional"]
    if unknown:
        raise CommandError(f"모르는 필드: {', '.join(sorted(unknown))}")
    missing = spec["required"] - fields
    if missing:
        raise CommandError(f"빠진 필드: {', '.join(sorted(missing))}")
    for k in fields:
        if not isinstance(obj[k], str):
            raise CommandError(f"{k} 는 문자열이어야 함")
    fp = obj.get("fp")
    if fp is not None and not FP_RE.match(fp):
        raise CommandError("fp 는 64자리 소문자 16진수")
    form_id = obj.get("form_id")
    if form_id is not None and not FORM_ID_RE.match(form_id):
        raise CommandError("form_id 형식 오류 (예: TH_TDAC)")
    reason = obj.get("reason")
    if reason is not None and len(reason) > 200:
        raise CommandError("reason 은 200자까지")
    return Command(action, fp, form_id, reason)


def format_command(action: str, **fields: str) -> str:
    """명령 글 만들기(형식 검사까지)."""
    text = f"{PREFIX} " + json.dumps({"action": action, **fields}, ensure_ascii=False, separators=(",", ":"))
    parse_command(text)
    return text


def _clip(s: str, n: int) -> str:
    return s if len(s) <= n else s[: n - 1] + "…"


def format_approval_request(change: Any, llm_summary: Optional[str] = None) -> str:
    """승인 요청 메시지. change 는 models.Change 또는 같은 키의 dict.

    llm_summary 가 없어도(요약 실패) 원본 diff 를 붙여 보낸다(ARIA_OPS 12.4).
    """
    c = change.to_dict() if hasattr(change, "to_dict") else dict(change)
    fp = c["fingerprint"]
    diff_text = json.dumps(c.get("diff"), ensure_ascii=False, indent=1, sort_keys=True)
    lines = [
        "[레디포트] 변경 감지 — 승인 요청",
        f"요약: {c.get('summary', '')}",
    ]
    if llm_summary:
        lines.append(f"AI 요약(참고): {_clip(llm_summary, 600)}")
    lines += [
        f"감지기: {c.get('detector')} / 단위: {c.get('unit')}",
        f"증거: {c.get('evidence_path') or '(없음)'}",
        f"지문: {fp}",
        "",
        "처리하려면 아래 한 줄을 보내세요(PR 을 만들 뿐, 머지는 GitHub 에서 사람이 합니다):",
        format_command("approve", fp=fp),
        "처리하지 않으려면:",
        format_command("reject", fp=fp),
        "",
        "원본 변경:",
    ]
    head = "\n".join(lines) + "\n"
    room = max(200, TELEGRAM_LIMIT - len(head))
    return head + _clip(diff_text, room)


def format_kill_notice(result_dict: dict, decision_dict: Optional[dict] = None) -> str:
    lines = [f"[레디포트] 자동 입력 끔: {result_dict.get('key')} (바뀜: {result_dict.get('changed')}, 시험: {result_dict.get('dry_run')})"]
    if decision_dict:
        lines.append(f"이유: {decision_dict.get('reason')}")
        lines.append(f"주요 오류: {decision_dict.get('top_errors')}")
    lines.append("다시 켜려면 원인 확인 후: " + format_command("unkill", form_id=result_dict.get("key", "").replace("kill_autofill_", "")))
    return "\n".join(lines)


def format_news_alert(change: Any) -> str:
    """뉴스 키워드 알림(참고용, 승인 요청 아님). 본문은 싣지 않는다."""
    c = change.to_dict() if hasattr(change, "to_dict") else dict(change)
    d = c.get("diff") or {}
    return "\n".join([
        "[레디포트] 입국정책 뉴스",
        _clip(str(d.get("title", "")), 200),
        str(d.get("url", "")),
        f"키워드: {', '.join(d.get('keywords', []))} / 관련 양식: {', '.join(d.get('form_ids', [])) or '-'}",
        f"지문: {c.get('fingerprint')}",
    ])


def format_attractions_changes(rows: list[dict]) -> str:
    """관광지 공식 페이지 변경 알림(참고용 — 자동 갱신 경로라 승인 명령은 없다). 공개 페이지 주소·관광지 id 만."""
    lines = [f"[레디포트] 관광지 공식 페이지 변경 {len(rows)}건 — 검사 통과하면 자동 반영(사장님 결정 2026-10-09)"]
    for r in rows[:15]:
        lines.append("· " + _clip(str(r.get("summary", "")), 220))
    if len(rows) > 15:
        lines.append(f"… 그 밖 {len(rows) - 15}건")
    return "\n".join(lines)


def format_attractions_pr(cc: str, ids: list[str], pr_url: str, published: bool) -> str:
    after = ("GitHub Actions 가 검사 → 서명 → 머지 → 배포까지 합니다." if published
             else "아직 공개 전인 나라라 작업본만 바뀝니다(서명·배포 없음).")
    return "\n".join([f"[레디포트] 관광지 자동 갱신 PR: {cc} ({', '.join(ids)})", pr_url, after])


def format_attractions_failed(cc: str, ids: list[str], step: str, detail: str = "", evidence: str = "") -> str:
    """검사 실패 → PR 없이 알림만(사장님 결정: 검사 통과할 때만 자동 반영)."""
    lines = [f"[레디포트] 관광지 자동 갱신 멈춤: {cc} ({', '.join(ids) or '-'})", f"단계: {step}"]
    if detail:
        lines.append("내용: " + _clip(detail, 1500))
    if evidence:
        lines.append(f"증거: {evidence}")
    lines.append("PR 은 만들지 않았어요. 필요하면 직접 확인해 주세요.")
    return "\n".join(lines)


def format_attraction_flags(cc: str, ids: list[str], changed: bool) -> str:
    if ids:
        return f"[레디포트] {cc} 관광지 '공식 안내가 바뀌었어요 — 확인 중' 표시: {', '.join(ids)}" + ("" if changed else " (그대로)")
    return f"[레디포트] {cc} 관광지 '확인 중' 표시를 모두 뗐어요"


def format_status(row: Optional[dict]) -> str:
    if not row:
        return "그 지문은 없어요."
    keys = ("fingerprint", "detector", "unit", "summary", "state", "review", "claude_status", "pr_url", "created_at")
    return "\n".join(f"{k}: {row.get(k)}" for k in keys)


def handle_command(cmd: Command, store, kill_client=None, dry_run: bool = True) -> str:
    """명령을 저장소·스위치에 반영하고 답장 글을 돌려준다.

    approve 는 review=accepted 만 한다(Claude 실행은 runners.claude_headless 가 상한을 보고 따로).
    unkill 은 사람이 보낸 명령이므로 approved_by_human=True 로 넘긴다 — 보낸 사람 확인은 봇이 먼저 한다.
    """
    from ..actions import kill_switch

    if cmd.action == "approve":
        ok = store.set_review(cmd.fp, "accepted")
        return "승인 기록함. Claude 실행 대기." if ok else "이미 결정됐거나 없는 지문이에요."
    if cmd.action == "reject":
        ok = store.set_review(cmd.fp, "rejected", cmd.reason or "")
        return "거절 기록함." if ok else "이미 결정됐거나 없는 지문이에요."
    if cmd.action == "status":
        if cmd.fp:
            return format_status(store.get(cmd.fp))
        rows = store.list_recent(5)
        return "\n".join(f"{r['fingerprint'][:12]} {r['review']}/{r['state']} {r['summary'][:60]}" for r in rows) or "기록 없음"
    if kill_client is None:
        return "Remote Config 연결이 설정되지 않았어요."
    if cmd.action == "kill":
        r = kill_switch.kill_autofill_on(kill_client, cmd.form_id, dry_run=dry_run)
        return f"자동 입력 끔 {r.key}: 바뀜={r.changed} 시험={r.dry_run}"
    if cmd.action == "unkill":
        r = kill_switch.kill_autofill_off(kill_client, cmd.form_id, approved_by_human=True, dry_run=dry_run)
        return f"자동 입력 다시 켬 {r.key}: 바뀜={r.changed} 시험={r.dry_run} {'; '.join(r.notes)}"
    raise CommandError(f"처리 못 하는 action: {cmd.action}")
