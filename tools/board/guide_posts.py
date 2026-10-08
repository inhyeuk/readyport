# 게시판 고정 이용 안내 글 2개(guide-qna, guide-talk)를 운영자 계정으로 올린다. 2026-10-09 처음 게시.
# 다시 실행하면 같은 문서를 덮어쓴다(추천·댓글 수는 0으로 돌아간다 — 고칠 때만). 로컬 firebase CLI 로그인(운영자) 필요.
import re, sys, unicodedata, datetime
import fs_rest as fs

UID = "V7US1HYZiISBK6vJKpq3JmCmhgN2"
NICK = "레디포트 운영자"


def tokens(w):
    w = w.lower()
    if len(w) < 2:
        return []
    hangul = lambda c: '가' <= c <= '힣'
    if any(hangul(c) for c in w):
        grams = [w[i:i + 2] for i in range(len(w) - 1)]
        return [g for g in grams if all(hangul(c) for c in g) or all(c.isalnum() for c in g)]
    return [w]


def of(text):
    text = unicodedata.normalize("NFC", text)
    out = []
    for word in re.split(r"[^\w]+|_", text):
        for t in tokens(word):
            if t not in out:
                out.append(t)
    return out


def keywords(title, body):
    out = []
    for t in of(title) + of(body):
        if t not in out:
            out.append(t)
    return out[:40]


QNA_TITLE = "Q&A 이용 안내 · 좋은 질문 쓰는 법"
QNA_BODY = """레디포트 Q&A는 입국 서류·비자·공항이 궁금할 때 먼저 다녀온 여행자에게 묻는 곳이에요.

■ 이렇게 물어보면 답이 빨리 와요
1. 나라를 꼭 골라 주세요. 나라 태그가 있으면 그 나라를 다녀온 사람이 찾기 쉬워요.
2. 제목에 궁금한 점을 한 줄로 적어 주세요.
   좋은 예: 태국 TDAC는 도착 며칠 전부터 낼 수 있나요?
   아쉬운 예: 질문 있어요
3. 언제 가는지, 어느 공항으로 들어가는지, 어디까지 해 봤는지 적어 주세요.
4. 비슷한 질문이 있는지 먼저 검색해 보세요.

■ 답이 도움이 됐다면
· 가장 도움이 된 답에 '채택'을 눌러 주세요. 질문에 '해결됨'이 붙고, 다음 사람도 그 답을 먼저 봐요.
· 좋은 답과 질문에는 추천을 눌러 주세요.

■ 꼭 지켜 주세요
· 여권 번호, 주민등록번호, 전화번호, 예약 번호 같은 개인정보는 쓰지 마세요. 게시판 글은 누구나 볼 수 있어요.
· 광고·홍보, 욕설, 다른 사람을 깎아내리는 글은 지워질 수 있어요.
· 문제 있는 글은 ⋮ 메뉴의 '신고'로 알려 주세요. 신고가 쌓이면 글이 가려지고 운영자가 확인해요.

■ 꼭 알아 두세요
여기 답은 다른 여행자의 경험이에요. 레디포트는 정부 기관이 아니고, 입국 규정은 자주 바뀌어요. 출발 전에 각 나라 공식 안내와 외교부 해외안전여행(0404.go.kr)에서 한 번 더 확인해 주세요.

여권 정보는 여전히 이 휴대폰에만 있어요. 게시판은 그와 별개로, 쓰신 글과 닉네임만 서버에 올라가요.

궁금한 점이나 불편한 점은 게시판 아래 '운영자에게 알리기'로 알려 주세요."""

TALK_TITLE = "자유 토론 이용 안내"
TALK_BODY = """자유 토론은 여행 이야기와 정보를 나누는 곳이에요.

■ 이런 글을 환영해요
· 다녀온 나라의 입국 후기 — 줄이 얼마나 길었는지, 자동 심사대를 썼는지
· 공항에서 시내 가는 방법, 유심·환전 경험
· 여행 준비 체크리스트 팁, 짐 꾸리기 노하우
· 레디포트에 바라는 점

■ 글을 쓸 때
· 나라 태그를 붙여 주시면 같은 나라에 가는 사람이 찾기 쉬워요.
· 댓글에 답글을 달아 이야기를 이어 갈 수 있어요.
· 여권 번호, 전화번호, 예약 번호 같은 개인정보는 쓰지 마세요. 누구나 볼 수 있어요.
· 광고·홍보, 욕설, 남을 깎아내리는 글은 지워질 수 있어요.
· 보기 싫은 사람은 ⋮ 메뉴에서 차단할 수 있어요. 이 휴대폰에서만 그 사람 글이 안 보여요.

입국 서류나 비자가 궁금하면 Q&A 게시판에 물어봐 주세요. 답이 모이고 '해결됨'으로 정리돼서 다음 사람도 찾기 쉬워요.

즐거운 여행 이야기 기다릴게요."""


def post(doc_id, kind, title, body):
    assert 4 <= len(title) <= 60, len(title)
    assert 10 <= len(body) <= 3000, len(body)
    now = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%fZ")
    ts = {"__ts__": now}
    data = {
        "kind": kind, "title": title, "body": body, "country": "",
        "keywords": keywords(title, body), "media": [],
        "authorUid": UID, "nickname": NICK,
        "createdAt": ts, "updatedAt": ts, "lastCommentAt": ts, "lastCommentId": "",
        "commentCount": 0, "likeCount": 0, "reportCount": 0, "score": 0,
        "solved": False, "acceptedId": "", "pinned": True, "hidden": False, "deleted": False,
    }
    r = fs.call("PATCH", "/board_posts/" + doc_id, fs.fields(data))
    print(doc_id, "OK" if "fields" in r else r, "| body", len(body), "| keywords", len(data["keywords"]))


if __name__ == "__main__":
    post("guide-qna", "qna", QNA_TITLE, QNA_BODY)
    post("guide-talk", "talk", TALK_TITLE, TALK_BODY)
