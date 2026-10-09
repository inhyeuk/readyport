"""GitHub REST: PR 열기·라벨 붙이기 (관광지 주간 자동 갱신 전용).

- 토큰은 이 저장소 전용 자격증명(`git -c credential.namespace=readyport credential fill`)에서 그때그때 꺼낸다.
  화면·로그·파일 어디에도 남기지 않는다. ARIA 가 원래 쓰는 GitHub 토큰은 건드리지 않는다(CLAUDE.md '저장소').
- main 에 직접 푸시하지 않는다. 머지·서명·배포는 GitHub Actions(attractions-auto.yml)가 한다.
- fetcher 는 주입(테스트는 가짜).
"""
from __future__ import annotations

import json
import re
import subprocess
from dataclasses import dataclass
from typing import Callable, Optional

API = "https://api.github.com"
REPO_RE = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
AUTO_LABEL = "attractions-auto"


class GitHubError(Exception):
    pass


def parse_credential(output: str) -> Optional[str]:
    for line in (output or "").splitlines():
        if line.startswith("password="):
            return line[len("password="):].strip() or None
    return None


def credential_token(run: Callable[..., object] = subprocess.run, cwd=None) -> Optional[str]:
    """이 저장소 전용 GitHub 자격증명. 없으면 None (사람이 한 번 `git push` 로 로그인해 두어야 한다)."""
    try:
        r = run(["git", "-c", "credential.namespace=readyport", "credential", "fill"],
                input="protocol=https\nhost=github.com\n\n", capture_output=True, text=True, timeout=120,
                cwd=None if cwd is None else str(cwd),
                env=None)
    except (OSError, subprocess.TimeoutExpired):
        return None
    if getattr(r, "returncode", 1) != 0:
        return None
    return parse_credential(getattr(r, "stdout", ""))


@dataclass
class PullRequest:
    number: int
    url: str


class GitHubClient:
    def __init__(self, repo: str, token_provider: Callable[[], Optional[str]], fetcher, timeout: float = 30.0):
        if not REPO_RE.match(repo or ""):
            raise ValueError("GITHUB_REPO 형식은 owner/name")
        self.repo = repo
        self.tokens = token_provider
        self.fetch = fetcher
        self.timeout = timeout

    def _headers(self) -> dict:
        token = self.tokens()
        if not token:
            raise GitHubError("GitHub 자격증명을 찾지 못함 (git -c credential.namespace=readyport credential fill)")
        return {"Authorization": f"Bearer {token}", "Accept": "application/vnd.github+json",
                "X-GitHub-Api-Version": "2022-11-28", "Content-Type": "application/json"}

    def _post(self, path: str, body: dict) -> dict:
        resp = self.fetch("POST", f"{API}/repos/{self.repo}/{path}", headers=self._headers(),
                          data=json.dumps(body, ensure_ascii=False).encode("utf-8"), timeout=self.timeout)
        if resp.status not in (200, 201):
            # 응답 본문에 토큰은 없지만, 길게 남기지 않는다
            raise GitHubError(f"GitHub HTTP {resp.status}: {resp.text[:200]}")
        return json.loads(resp.text or "{}")

    def create_pr(self, head: str, base: str, title: str, body: str) -> PullRequest:
        if head == base or head in ("main", "master"):
            raise GitHubError("main 에서 PR 을 만들 수 없음")
        d = self._post("pulls", {"head": head, "base": base, "title": title[:200], "body": body[:60000],
                                 "maintainer_can_modify": True})
        return PullRequest(int(d["number"]), str(d["html_url"]))

    def add_labels(self, number: int, labels: list[str]) -> None:
        self._post(f"issues/{int(number)}/labels", {"labels": labels})
