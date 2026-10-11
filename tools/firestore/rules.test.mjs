// Firestore 규칙 테스트 (에뮬레이터). 실행: cd tools/firestore && npm test
// firebase emulators:exec --only firestore "node --test rules.test.mjs"
import { test, before, after, beforeEach } from 'node:test';
import { readFileSync } from 'node:fs';
import {
  initializeTestEnvironment, assertFails, assertSucceeds,
} from '@firebase/rules-unit-testing';
import {
  doc, setDoc, getDoc, getDocs, updateDoc, deleteDoc, addDoc, collection, serverTimestamp, increment, Timestamp,
  writeBatch, query, where, orderBy, collectionGroup,
} from 'firebase/firestore';

let env;
before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'readyport-rules-test',
    firestore: { rules: readFileSync(new URL('../../firebase/firestore.rules', import.meta.url), 'utf8') },
  });
});
after(async () => { await env.cleanup(); });
beforeEach(async () => { await env.clearFirestore(); });

const app = () => env.unauthenticatedContext().firestore();
const days = (n) => Timestamp.fromMillis(Date.now() + n * 86400000);
const report = (extra = {}) => ({
  form_id: 'TH_TDAC', pack_version: '2026.09.28-2', step_id: 'personal',
  error_code: 'selector_missing', app_version: '0.1.0', ts: serverTimestamp(), expire_at: days(365), ...extra,
});

test('리포트 생성은 된다', async () => {
  await assertSucceeds(addDoc(collection(app(), 'field_reports'), report()));
  await assertSucceeds(addDoc(collection(app(), 'field_reports'), report({ site_version: 'v2.3.1' })));
});

test('리포트에 다른 필드(개인정보 등)를 넣으면 거절', async () => {
  await assertFails(addDoc(collection(app(), 'field_reports'), report({ passport_no: 'M12345678' })));
  await assertFails(addDoc(collection(app(), 'field_reports'), report({ error_code: 'anything' })));
  await assertFails(addDoc(collection(app(), 'field_reports'), report({ ts: 1 })));
  await assertFails(addDoc(collection(app(), 'field_reports'), report({ step_id: 'has space' })));
  // 보관 기간(자동 삭제일)이 없거나 너무 길면 거절
  const noExpiry = report(); delete noExpiry.expire_at;
  await assertFails(addDoc(collection(app(), 'field_reports'), noExpiry));
  await assertFails(addDoc(collection(app(), 'field_reports'), report({ expire_at: days(3650) })));
  await assertFails(addDoc(collection(app(), 'field_reports'), report({ expire_at: days(1) })));
});

test('리포트 읽기·수정·삭제 불가', async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'field_reports/r1'), { form_id: 'TH_TDAC' });
  });
  await assertFails(getDoc(doc(app(), 'field_reports/r1')));
  await assertFails(updateDoc(doc(app(), 'field_reports/r1'), { form_id: 'X' }));
  await assertFails(deleteDoc(doc(app(), 'field_reports/r1')));
});

test('찜 수: 1로 만들기, +1만 허용', async () => {
  const ref = doc(app(), 'favorite_counts/TH');
  await assertFails(setDoc(ref, { count: 5 }));
  await assertSucceeds(setDoc(ref, { count: 1 }));
  await assertSucceeds(updateDoc(ref, { count: increment(1) }));
  await assertFails(updateDoc(ref, { count: increment(2) }));
  await assertFails(updateDoc(ref, { count: increment(-1) }));
  await assertFails(updateDoc(ref, { count: increment(1), who: 'x' }));
  await assertFails(deleteDoc(ref));
  await assertSucceeds(getDoc(ref));
});

test('하트비트 등 다른 경로는 앱에서 접근 불가', async () => {
  await assertFails(getDoc(doc(app(), 'ops/heartbeat')));
  await assertFails(setDoc(doc(app(), 'ops/heartbeat'), { last_check: 1 }));
});

test('공지: 문서 하나 읽기만, 쓰기·목록 불가', async () => {
  await assertSucceeds(getDoc(doc(app(), 'notices/current')));
  await assertFails(setDoc(doc(app(), 'notices/current'), { payload: 'x', sig: 'y' }));
  await assertFails(updateDoc(doc(app(), 'notices/current'), { payload: 'x' }));
  await assertFails(deleteDoc(doc(app(), 'notices/current')));
  await assertFails(getDocs(collection(app(), 'notices')));
});

test('영상 목록: 나라 문서 읽기만, 쓰기·목록 불가', async () => {
  await assertSucceeds(getDoc(doc(app(), 'videos/TH')));
  await assertFails(setDoc(doc(app(), 'videos/TH'), { payload: 'x' }));
  await assertFails(deleteDoc(doc(app(), 'videos/TH')));
  await assertFails(getDoc(doc(app(), 'videos/thai')));
  await assertFails(getDocs(collection(app(), 'videos')));
});

// ======================= 게시판 (docs/BOARD.md) =======================
// 익명 로그인 = authenticatedContext(uid, sign_in_provider anonymous). 닉네임·글·댓글은 모두 지어낸 값이다.

const anon = (uid) => env.authenticatedContext(uid, { firebase: { sign_in_provider: 'anonymous' } }).firestore();
const seed = (fn) => env.withSecurityRulesDisabled(async (ctx) => fn(ctx.firestore()));
const secondsAgo = (n) => Timestamp.fromMillis(Date.now() - n * 1000);
const NICK = '여행자 4821';

const postData = (uid, extra = {}) => ({
  kind: 'qna', title: '태국 입국 카드 질문', body: 'TDAC는 도착 며칠 전부터 낼 수 있나요?', country: 'TH',
  keywords: ['태국', '입국'], media: [], authorUid: uid, nickname: NICK,
  createdAt: serverTimestamp(), updatedAt: serverTimestamp(), lastCommentAt: serverTimestamp(), lastCommentId: '',
  commentCount: 0, likeCount: 0, reportCount: 0, score: 0, solved: false, acceptedId: '',
  pinned: false, hidden: false, deleted: false, ...extra,
});

/** 닉네임 정하기 (board_users 만들기) */
const join = (db, uid, nickname = NICK) =>
  setDoc(doc(db, 'board_users', uid), { nickname, createdAt: serverTimestamp(), postCount: 0 });

/** 글 올리기 = 글 + 내 이용자 문서의 마지막 글 시각(한 묶음) */
const writePost = (db, uid, id, extra = {}, userExtra = {}) => {
  const b = writeBatch(db);
  b.set(doc(db, 'board_posts', id), postData(uid, extra));
  b.update(doc(db, 'board_users', uid), { lastPostAt: serverTimestamp(), lastPostId: id, postCount: increment(1), ...userExtra });
  return b.commit();
};

const commentData = (uid, postId, postAuthorUid, extra = {}) => ({
  postId, postAuthorUid, authorUid: uid, nickname: `${uid}여행`, body: '도착 72시간 전부터 낼 수 있어요.',
  parentId: '', replyToId: '', replyToUid: '', replyToNick: '', notify: postAuthorUid === uid ? [] : [postAuthorUid],
  createdAt: serverTimestamp(), updatedAt: serverTimestamp(), edited: false, likeCount: 0, reportCount: 0,
  deleted: false, hidden: false, ...extra,
});

/** 댓글 달기 = 댓글 + 글의 댓글 수 + 내 마지막 댓글 시각(한 묶음) */
const writeComment = (db, uid, postId, commentId, postAuthorUid, extra = {}) => {
  const b = writeBatch(db);
  b.set(doc(db, 'board_posts', postId, 'comments', commentId), commentData(uid, postId, postAuthorUid, extra));
  b.update(doc(db, 'board_posts', postId), {
    commentCount: increment(1), score: increment(1), lastCommentAt: serverTimestamp(), lastCommentId: commentId,
  });
  b.update(doc(db, 'board_users', uid), { lastCommentAt: serverTimestamp(), lastCommentPost: postId, lastCommentId: commentId });
  return b.commit();
};

const likeBatch = (db, uid, on, likeUid = uid, step = 1) => {
  const b = writeBatch(db);
  const ref = doc(db, 'board_posts', 'p1', 'likes', likeUid);
  if (on) b.set(ref, { uid: likeUid, postId: 'p1', at: serverTimestamp() });
  else b.delete(ref);
  b.update(doc(db, 'board_posts', 'p1'), { likeCount: increment(on ? step : -step), score: increment(on ? step : -step) });
  return b.commit();
};

const reportBatch = (db, uid, reason = 'spam') => {
  const b = writeBatch(db);
  b.set(doc(db, 'board_posts', 'p1', 'reports', uid), { uid, reason, at: serverTimestamp() });
  b.update(doc(db, 'board_posts', 'p1'), { reportCount: increment(1) });
  return b.commit();
};

/** 다른 사람(bob)이 이미 올린 글 p1 + alice·bob·carol 닉네임 + 운영자 admin1 */
async function boardWorld() {
  await seed(async (db) => {
    await setDoc(doc(db, 'config/admins'), { uids: ['admin1'] });
    for (const u of ['alice', 'bob', 'carol', 'admin1']) {
      await setDoc(doc(db, 'board_users', u), { nickname: `${u}여행`, createdAt: secondsAgo(3600), postCount: 0 });
    }
    await setDoc(doc(db, 'board_posts/p1'), {
      ...postData('bob', { nickname: 'bob여행' }), createdAt: secondsAgo(600), updatedAt: secondsAgo(600), lastCommentAt: secondsAgo(600),
    });
  });
}

test('게시판: 익명 로그인한 사람이 닉네임을 정하고 올바른 글을 올린다(읽기는 로그인 없이)', async () => {
  const db = anon('alice');
  await assertSucceeds(join(db, 'alice'));
  await assertSucceeds(writePost(db, 'alice', 'a1'));
  await assertSucceeds(getDoc(doc(app(), 'board_posts/a1')));
  await assertSucceeds(getDocs(query(collection(app(), 'board_posts'), where('kind', '==', 'qna'), where('hidden', '==', false))));
});

test('게시판: 로그인·닉네임 없이 쓰기 불가', async () => {
  await assertFails(setDoc(doc(app(), 'board_posts/x'), postData('nobody')));
  const db = anon('dave');
  await assertFails(writePost(db, 'dave', 'd1'));
  await assertFails(setDoc(doc(db, 'board_posts/d1'), postData('dave')));
});

test('게시판: 닉네임 규칙 — 2~12자, 운영자처럼 보이는 이름 금지, 남의 문서 금지', async () => {
  const db = anon('erin');
  await assertFails(join(db, 'erin', '가'));
  await assertFails(join(db, 'erin', '열세글자가넘는아주긴닉네임'));
  await assertFails(join(db, 'erin', '레디포트 운영자'));
  await assertFails(join(db, 'erin', 'Admin 01'));
  await assertFails(join(db, 'erin', '여행자<b>'));
  await assertFails(join(db, 'someone-else', NICK));
  await assertFails(setDoc(doc(db, 'board_users', 'erin'), { nickname: NICK, createdAt: serverTimestamp(), postCount: 5 }));
  await assertSucceeds(join(db, 'erin', NICK));
  await assertSucceeds(updateDoc(doc(db, 'board_users', 'erin'), { nickname: 'Seoul_trip' }));
  // 마지막 글 시각을 글 없이 바꾸거나 옛날로 돌릴 수 없다
  await assertFails(updateDoc(doc(db, 'board_users', 'erin'), { lastPostAt: serverTimestamp(), lastPostId: 'nothing', postCount: increment(1) }));
  await assertFails(updateDoc(doc(db, 'board_users', 'erin'), { lastPostAt: secondsAgo(3600) }));
});

test('게시판: 글 필드·길이 검사', async () => {
  await boardWorld();
  const db = anon('alice');
  const bad = [
    { title: '가나' },
    { title: '가'.repeat(61) },
    { body: '열 글자 안 됨' },
    { body: '가'.repeat(3001) },
    { kind: 'news' },
    { country: 'US' },
    { keywords: Array.from({ length: 41 }, (_, i) => `k${i}`) },
    { commentCount: 3 },
    { likeCount: 1, score: 1 },
    { solved: true },
    { pinned: true },
    { hidden: true },
    { passport_no: 'M12345678' },
    { createdAt: secondsAgo(100) },
  ];
  for (const extra of bad) {
    await assertFails(writePost(db, 'alice', 'bad', { nickname: 'alice여행', ...extra }));
  }
  await assertSucceeds(writePost(db, 'alice', 'good', {
    nickname: 'alice여행', kind: 'talk', country: '', title: '방콕 야시장 후기', body: '쩟페어 야시장 다녀왔어요. 사람이 많아요.',
  }));
});

test('게시판: 남을 흉내 낼 수 없다 — 글쓴이 = 로그인 ID, 닉네임 = 내 닉네임', async () => {
  await boardWorld();
  const db = anon('alice');
  await assertFails(writePost(db, 'alice', 'fake', { authorUid: 'bob', nickname: 'alice여행' }));
  await assertFails(writePost(db, 'alice', 'fake2', { nickname: 'bob여행' }));
  await assertSucceeds(writePost(db, 'alice', 'real', { nickname: 'alice여행' }));
});

test('게시판: 남의 글은 고치거나 지울 수 없고, 내 글은 고치고 지운다', async () => {
  await boardWorld();
  const alice = anon('alice');
  await assertFails(updateDoc(doc(alice, 'board_posts/p1'), { title: '제목을 바꿔 봄', updatedAt: serverTimestamp() }));
  await assertFails(deleteDoc(doc(alice, 'board_posts/p1')));
  const bob = anon('bob');
  await assertSucceeds(updateDoc(doc(bob, 'board_posts/p1'), { title: '태국 입국 카드 질문 고침', updatedAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(bob, 'board_posts/p1'), { title: '새 제목입니다', likeCount: 99, updatedAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(bob, 'board_posts/p1'), { pinned: true }));
  await assertFails(updateDoc(doc(bob, 'board_posts/p1'), { title: '짧', updatedAt: serverTimestamp() }));
  await assertSucceeds(deleteDoc(doc(bob, 'board_posts/p1')));
});

test('게시판: 추천 수는 내 추천 문서와 짝으로만 움직인다', async () => {
  await boardWorld();
  const db = anon('alice');
  await assertFails(updateDoc(doc(db, 'board_posts/p1'), { likeCount: increment(1), score: increment(1) }));
  await assertFails(setDoc(doc(db, 'board_posts/p1/likes/alice'), { uid: 'alice', postId: 'p1', at: serverTimestamp() }));
  await assertFails(likeBatch(db, 'alice', true, 'alice', 2));
  await assertSucceeds(likeBatch(db, 'alice', true));
  await assertFails(likeBatch(db, 'alice', true));
  await assertSucceeds(likeBatch(db, 'alice', false));
  await assertFails(likeBatch(db, 'alice', true, 'carol'));
  // 자기 글은 추천할 수 없다
  await assertFails(likeBatch(anon('bob'), 'bob', true));
});

test('게시판: 신고 수는 새 신고 문서와 짝으로 +1, 한 사람 한 번, 신고 문서는 운영자만 읽는다', async () => {
  await boardWorld();
  const db = anon('alice');
  await assertFails(updateDoc(doc(db, 'board_posts/p1'), { reportCount: increment(1) }));
  await assertFails(reportBatch(db, 'alice', 'because'));
  await assertSucceeds(reportBatch(db, 'alice'));
  await assertFails(reportBatch(db, 'alice'));
  await assertFails(reportBatch(anon('bob'), 'bob')); // 자기 글 신고 불가
  await assertSucceeds(getDoc(doc(db, 'board_posts/p1/reports/alice')));
  await assertFails(getDoc(doc(anon('carol'), 'board_posts/p1/reports/alice')));
  await assertFails(getDocs(collection(anon('carol'), 'board_posts/p1/reports')));
  await assertSucceeds(getDocs(collection(anon('admin1'), 'board_posts/p1/reports')));
});

test('게시판: 댓글은 댓글 수와 짝, 답글은 한 단계, 알림 대상은 글쓴이·답글 대상만', async () => {
  await boardWorld();
  const alice = anon('alice');
  await assertFails(setDoc(doc(alice, 'board_posts/p1/comments/c0'), commentData('alice', 'p1', 'bob')));
  await assertFails(updateDoc(doc(alice, 'board_posts/p1'), {
    commentCount: increment(1), score: increment(1), lastCommentAt: serverTimestamp(), lastCommentId: 'zz',
  }));
  await assertFails(writeComment(alice, 'alice', 'p1', 'c0', 'bob', { notify: ['bob', 'mallory'] }));
  await assertFails(writeComment(alice, 'alice', 'p1', 'c0', 'bob', { body: '' }));
  await assertSucceeds(writeComment(alice, 'alice', 'p1', 'c1', 'bob'));
  const carol = anon('carol');
  // 답글 대상이 그 댓글을 쓴 사람이 아니면 거절
  await assertFails(writeComment(carol, 'carol', 'p1', 'r0', 'bob', {
    parentId: 'c1', replyToId: 'c1', replyToUid: 'bob', replyToNick: 'alice여행', notify: ['bob'],
  }));
  await assertSucceeds(writeComment(carol, 'carol', 'p1', 'r1', 'bob', {
    parentId: 'c1', replyToId: 'c1', replyToUid: 'alice', replyToNick: 'alice여행', notify: ['bob', 'alice'],
  }));
  const bob = anon('bob');
  // 답글을 부모로 삼으면(두 단계) 거절, 같은 묶음 + @대상이면 된다
  await assertFails(writeComment(bob, 'bob', 'p1', 'r2', 'bob', {
    parentId: 'r1', replyToId: 'r1', replyToUid: 'carol', replyToNick: 'carol여행', notify: ['carol'],
  }));
  await assertSucceeds(writeComment(bob, 'bob', 'p1', 'r2', 'bob', {
    parentId: 'c1', replyToId: 'r1', replyToUid: 'carol', replyToNick: 'carol여행', notify: ['carol'],
  }));
  // 나에게 온 댓글 찾기(묶음 질의) — 가리지 않은 것만 묻는다
  await assertSucceeds(getDocs(query(
    collectionGroup(alice, 'comments'), where('hidden', '==', false), where('notify', 'array-contains', 'alice'),
    where('createdAt', '>', secondsAgo(3600)), orderBy('createdAt'),
  )));
});

test('게시판: 댓글 고치기·지우기는 쓴 사람만, 지우면 자리는 남는다', async () => {
  await boardWorld();
  const alice = anon('alice');
  await assertSucceeds(writeComment(alice, 'alice', 'p1', 'c1', 'bob'));
  await assertFails(updateDoc(doc(anon('carol'), 'board_posts/p1/comments/c1'), { body: '남이 고침', edited: true, updatedAt: serverTimestamp() }));
  await assertSucceeds(updateDoc(doc(alice, 'board_posts/p1/comments/c1'), { body: '고친 답이에요.', edited: true, updatedAt: serverTimestamp() }));
  await assertFails(deleteDoc(doc(alice, 'board_posts/p1/comments/c1')));
  await assertFails(updateDoc(doc(alice, 'board_posts/p1/comments/c1'), { deleted: true, updatedAt: serverTimestamp() }));
  await assertSucceeds(updateDoc(doc(alice, 'board_posts/p1/comments/c1'), { deleted: true, body: '', nickname: '', updatedAt: serverTimestamp() }));
  const bob = anon('bob');
  await assertFails(deleteDoc(doc(bob, 'board_posts/p1')));
  await assertSucceeds(updateDoc(doc(bob, 'board_posts/p1'), {
    deleted: true, title: '', body: '', country: '', keywords: [], media: [], nickname: '', updatedAt: serverTimestamp(),
  }));
});

test('게시판: 글은 30초, 댓글은 10초 간격 (규칙의 서버 시각)', async () => {
  await boardWorld();
  const alice = anon('alice');
  await assertSucceeds(writePost(alice, 'alice', 'a1', { nickname: 'alice여행' }));
  await assertFails(writePost(alice, 'alice', 'a2', { nickname: 'alice여행' }));
  await seed(async (db) => updateDoc(doc(db, 'board_users/alice'), { lastPostAt: secondsAgo(31) }));
  await assertSucceeds(writePost(alice, 'alice', 'a3', { nickname: 'alice여행' }));
  await assertFails(writePost(alice, 'alice', 'a4', { nickname: 'alice여행' }, { lastPostAt: secondsAgo(3600) }));
  await assertSucceeds(writeComment(alice, 'alice', 'p1', 'c1', 'bob'));
  await assertFails(writeComment(alice, 'alice', 'p1', 'c2', 'bob'));
  await seed(async (db) => updateDoc(doc(db, 'board_users/alice'), { lastCommentAt: secondsAgo(11) }));
  await assertSucceeds(writeComment(alice, 'alice', 'p1', 'c3', 'bob'));
});

test('게시판: 가린 글은 운영자·글쓴이 말고는 목록·읽기에서 빠진다', async () => {
  await boardWorld();
  await seed(async (db) => updateDoc(doc(db, 'board_posts/p1'), { hidden: true }));
  const carol = anon('carol');
  await assertFails(getDoc(doc(carol, 'board_posts/p1')));
  await assertFails(getDoc(doc(app(), 'board_posts/p1')));
  await assertFails(getDocs(query(collection(carol, 'board_posts'), where('kind', '==', 'qna'))));
  const open = await assertSucceeds(getDocs(query(collection(carol, 'board_posts'), where('kind', '==', 'qna'), where('hidden', '==', false))));
  if (open.docs.some((d) => d.id === 'p1')) throw new Error('가린 글이 목록에 나왔다');
  await assertSucceeds(getDoc(doc(anon('bob'), 'board_posts/p1')));
  await assertSucceeds(getDoc(doc(anon('admin1'), 'board_posts/p1')));
  await assertSucceeds(getDocs(query(collection(anon('admin1'), 'board_posts'), where('hidden', '==', true))));
  await assertFails(writeComment(anon('alice'), 'alice', 'p1', 'c1', 'bob'));
});

test('게시판: 고정·가림·사진 스위치는 운영자만, config/admins 는 아무도 못 쓴다', async () => {
  await boardWorld();
  const alice = anon('alice');
  await assertFails(updateDoc(doc(alice, 'board_posts/p1'), { pinned: true }));
  await assertFails(updateDoc(doc(alice, 'board_posts/p1'), { hidden: true }));
  await assertFails(setDoc(doc(alice, 'config/board'), { mediaEnabled: true, updatedAt: serverTimestamp() }));
  const admin = anon('admin1');
  await assertSucceeds(updateDoc(doc(admin, 'board_posts/p1'), { pinned: true }));
  await assertSucceeds(updateDoc(doc(admin, 'board_posts/p1'), { hidden: true, reportCount: 0 }));
  await assertFails(updateDoc(doc(admin, 'board_posts/p1'), { title: '운영자가 글을 바꿈', updatedAt: serverTimestamp() }));
  await assertSucceeds(setDoc(doc(admin, 'config/board'), { mediaEnabled: true, updatedAt: serverTimestamp() }));
  await assertFails(setDoc(doc(admin, 'config/board'), { mediaEnabled: 'yes', updatedAt: serverTimestamp() }));
  await assertSucceeds(getDoc(doc(app(), 'config/board')));
  await assertSucceeds(getDoc(doc(app(), 'config/admins')));
  await assertFails(setDoc(doc(alice, 'config/admins'), { uids: ['alice'] }));
  await assertFails(updateDoc(doc(admin, 'config/admins'), { uids: ['admin1', 'alice'] }));
  await assertFails(setDoc(doc(app(), 'config/admins'), { uids: ['x'] }));
  await assertSucceeds(deleteDoc(doc(admin, 'board_posts/p1')));
});

test('게시판: Q&A 채택은 질문한 사람만, 남이 쓴 맨 위 답 하나', async () => {
  await boardWorld();
  await assertSucceeds(writeComment(anon('alice'), 'alice', 'p1', 'c1', 'bob'));
  await assertSucceeds(writeComment(anon('carol'), 'carol', 'p1', 'r1', 'bob', {
    parentId: 'c1', replyToId: 'c1', replyToUid: 'alice', replyToNick: 'alice여행', notify: ['bob', 'alice'],
  }));
  await assertFails(updateDoc(doc(anon('carol'), 'board_posts/p1'), { solved: true, acceptedId: 'c1' }));
  const bob = anon('bob');
  await assertFails(updateDoc(doc(bob, 'board_posts/p1'), { solved: true, acceptedId: 'r1' }));
  await assertFails(updateDoc(doc(bob, 'board_posts/p1'), { solved: true, acceptedId: 'nope' }));
  await assertSucceeds(updateDoc(doc(bob, 'board_posts/p1'), { solved: true, acceptedId: 'c1' }));
  await assertSucceeds(updateDoc(doc(bob, 'board_posts/p1'), { solved: false, acceptedId: '' }));
});

test('게시판: 사진·동영상은 운영자가 켰을 때만, 내 Storage 경로만', async () => {
  await boardWorld();
  const url = (uid, post, n = 0, ext = 'jpg') =>
    `https://firebasestorage.googleapis.com/v0/b/readyport-app.firebasestorage.app/o/board%2F${uid}%2F${post}%2F${n}.${ext}?alt=media&token=1a2b-3c4d`;
  const alice = anon('alice');
  const nick = { nickname: 'alice여행' };
  await assertFails(writePost(alice, 'alice', 'm1', { ...nick, media: [url('alice', 'm1')] }));
  await seed(async (db) => setDoc(doc(db, 'config/board'), { mediaEnabled: true, updatedAt: serverTimestamp() }));
  await assertFails(writePost(alice, 'alice', 'm1', { ...nick, media: [url('bob', 'm1')] }));
  await assertFails(writePost(alice, 'alice', 'm1', { ...nick, media: ['https://evil.example.com/a.jpg'] }));
  await assertFails(writePost(alice, 'alice', 'm1', { ...nick, media: [0, 1, 2, 3, 4, 5].map((n) => url('alice', 'm1', n)) }));
  await assertSucceeds(writePost(alice, 'alice', 'm1', { ...nick, media: [url('alice', 'm1', 0), url('alice', 'm1', 1, 'mp4')] }));
});

test('게시판: 내 기록 지우기 — 내 댓글·추천 묶음 질의, 이용자 문서 지우기', async () => {
  await boardWorld();
  const alice = anon('alice');
  await assertSucceeds(writeComment(alice, 'alice', 'p1', 'c1', 'bob'));
  await assertSucceeds(getDocs(query(collectionGroup(alice, 'comments'), where('authorUid', '==', 'alice'))));
  await assertSucceeds(getDocs(query(collectionGroup(alice, 'likes'), where('uid', '==', 'alice'))));
  await assertFails(getDocs(query(collectionGroup(alice, 'likes'), where('uid', '==', 'bob'))));
  await assertSucceeds(getDocs(query(collection(alice, 'board_posts'), where('authorUid', '==', 'alice'))));
  await assertFails(deleteDoc(doc(alice, 'board_users/bob')));
  await assertSucceeds(deleteDoc(doc(alice, 'board_users/alice')));
});

// ======================= 관광지 평점·확인 중 표시 (docs/ARIA_OPS.md 12.10) =======================

const vote = (stars = 5, extra = {}) => ({ stars, at: serverTimestamp(), visited: true, ...extra });

test('평점: 로그인한 사람이 내 표 하나만 만들고 고치고 지운다', async () => {
  const alice = anon('alice');
  const mine = doc(alice, 'attraction_ratings/JP_sample-place/votes/alice');
  await assertSucceeds(setDoc(mine, vote(5)));
  await assertSucceeds(setDoc(mine, vote(3)));
  await assertSucceeds(getDoc(mine));
  await assertSucceeds(deleteDoc(mine));
  // 남의 표·로그인 없이·목록은 안 된다
  await assertFails(setDoc(doc(alice, 'attraction_ratings/JP_sample-place/votes/bob'), vote(5)));
  await assertFails(setDoc(doc(app(), 'attraction_ratings/JP_sample-place/votes/alice'), vote(5)));
  await assertFails(getDocs(collection(alice, 'attraction_ratings/JP_sample-place/votes')));
  await assertFails(getDoc(doc(anon('bob'), 'attraction_ratings/JP_sample-place/votes/alice')));
});

test('평점: 별 1~5 정수, 다녀왔어요=true, 서버 시각, 자유 글 없음, 나라·id 형식', async () => {
  const alice = anon('alice');
  const mine = doc(alice, 'attraction_ratings/JP_sample-place/votes/alice');
  for (const bad of [vote(0), vote(6), vote(4.5), vote('5'), vote(5, { visited: false }), vote(5, { at: Timestamp.now() }),
    vote(5, { comment: '좋아요' }), { stars: 5, at: serverTimestamp() }]) {
    await assertFails(setDoc(mine, bad));
  }
  await assertFails(setDoc(doc(alice, 'attraction_ratings/KR_sample-place/votes/alice'), vote(5)));
  await assertFails(setDoc(doc(alice, 'attraction_ratings/JP_Sample_Place/votes/alice'), vote(5)));
  await assertFails(setDoc(doc(alice, 'attraction_ratings/JP_sample-place'), { n: 1 }));
});

test('평점 집계·확인 중 표시: 누구나 나라 문서 읽기, 앱은 쓰기 불가', async () => {
  await seed(async (db) => {
    await setDoc(doc(db, 'attraction_rating_stats/JP'), { 'sample-place': { avg: 4.2, n: 7 } });
    await setDoc(doc(db, 'attraction_flags/JP'), { ids: ['sample-place'], kind: 'check_in_progress', at: Timestamp.now() });
  });
  for (const db of [app(), anon('alice')]) {
    await assertSucceeds(getDoc(doc(db, 'attraction_rating_stats/JP')));
    await assertSucceeds(getDoc(doc(db, 'attraction_flags/JP')));
    await assertFails(setDoc(doc(db, 'attraction_rating_stats/JP'), { 'sample-place': { avg: 5, n: 99 } }));
    await assertFails(setDoc(doc(db, 'attraction_flags/JP'), { ids: [], kind: 'check_in_progress', at: serverTimestamp() }));
    await assertFails(deleteDoc(doc(db, 'attraction_flags/JP')));
    await assertFails(getDocs(collection(db, 'attraction_rating_stats')));
  }
  await assertFails(getDoc(doc(app(), 'attraction_flags/jp')));
});

// ======================= 여행 계획 요청 (docs/ARIA_OPS.md 12.11) =======================
// 요청 값은 모두 지어낸 것(실제 사람·건강 정보 아님)

const planData = (uid, extra = {}) => ({
  uid, country: 'JP', purposes: ['sightseeing', 'food'], purpose_note: '가짜 메모',
  travelers: { adults: 2, seniors: 1, teens: 0, children: 1, genders: { female: 2, male: 2 } },
  mobility: ['long_walk_hard'], sensitive_consent: true, days: 3, budget_band: 'standard', currency: 'KRW',
  status: 'queued', createdAt: serverTimestamp(), ...extra,
});
const without = (o, ...keys) => { const c = { ...o }; for (const k of keys) delete c[k]; return c; };

/**
 * 요청 = 요청 문서 + 내 횟수 기록(한 묶음). 횟수 기록은 나라별 누적 counts (2026-10-11).
 * quota = { counts: 직전 나라별 횟수, extra: 서버가 더해 준 추가 횟수(그대로 되돌려 씀), raw: 일부러 덮어쓸 칸 }
 */
const requestPlan = (db, uid, id, extra = {}, first = true, quota = {}, drop = []) => {
  const country = extra.country ?? 'JP';
  const b = writeBatch(db);
  b.set(doc(db, 'plan_requests', id), without(planData(uid, extra), ...drop));
  const counts = { ...(quota.counts ?? {}) };
  counts[country] = (counts[country] ?? 0) + 1;
  b.set(doc(db, 'plan_quota', uid), {
    last: serverTimestamp(), lastRequestId: id, counts, ...(quota.extra ? { extra: quota.extra } : {}), ...(quota.raw ?? {}),
  });
  return b.commit();
};
const daysAgo = (n) => Timestamp.fromMillis(Date.now() - n * 86400000);

test('계획 요청: 올바른 요청과 횟수 기록을 한 묶음으로, 본인·운영자만 읽는다', async () => {
  await seed(async (db) => setDoc(doc(db, 'config/admins'), { uids: ['admin1'] }));
  const alice = anon('alice');
  await assertSucceeds(requestPlan(alice, 'alice', 'req1'));
  await assertSucceeds(getDoc(doc(alice, 'plan_requests/req1')));
  await assertSucceeds(getDocs(query(collection(alice, 'plan_requests'), where('uid', '==', 'alice'))));
  await assertSucceeds(getDoc(doc(alice, 'plan_quota/alice')));
  await assertSucceeds(getDoc(doc(anon('admin1'), 'plan_requests/req1')));
  await assertFails(getDoc(doc(anon('bob'), 'plan_requests/req1')));
  await assertFails(getDoc(doc(app(), 'plan_requests/req1')));
  await assertFails(getDocs(collection(anon('bob'), 'plan_requests')));
  await assertFails(getDoc(doc(anon('bob'), 'plan_quota/alice')));
  // 날짜로 요청, 이동 조건 없으면 동의 칸도 없어야
  await assertSucceeds(requestPlan(anon('carol'), 'carol', 'req2',
    { start_date: '2026-11-01', end_date: '2026-11-03', mobility: [] }, true, {}, ['days', 'sensitive_consent']));
  // 이동 조건을 안 고르면 동의 칸도 없어야 하고, 날짜는 끝이 시작보다 앞일 수 없다
  await assertFails(requestPlan(anon('dave'), 'dave', 'req3', { mobility: [] }));
  await assertFails(requestPlan(anon('erin'), 'erin', 'req4',
    { start_date: '2026-11-05', end_date: '2026-11-01' }, true, {}, ['days']));
});

test('계획 요청: 횟수 기록 없이·남의 이름으로·모양이 틀리면 거절', async () => {
  const alice = anon('alice');
  await assertFails(setDoc(doc(alice, 'plan_requests/solo'), planData('alice')));             // 횟수 기록 없이
  await assertFails(requestPlan(alice, 'bob', 'r0'));                                          // 남의 ID
  const bad = [
    { country: 'KR' }, { purposes: [] }, { purposes: ['gambling'] }, { purposes: ['food', 'food'] },
    { purpose_note: '가'.repeat(201) }, { travelers: { adults: 0, seniors: 0, teens: 0, children: 0 } },
    { travelers: { adults: 2, seniors: 0, teens: 0 } }, { travelers: { adults: 2, seniors: 0, teens: 0, children: 0, genders: { other: 1 } } },
    { travelers: { adults: 21, seniors: 0, teens: 0, children: 0 } },
    { mobility: ['diagnosis_text'] }, { sensitive_consent: false }, { mobility: [], sensitive_consent: true },
    { days: 31 }, { days: 0 }, { start_date: '2026-11-01', end_date: '2026-11-03' },
    { budget_band: 'unlimited' }, { currency: 'USD' }, { status: 'done' }, { createdAt: Timestamp.now() },
    { passport_no: 'M00000000' }, { health_note: '가짜' },
  ];
  for (const [i, extra] of bad.entries()) {
    await assertFails(requestPlan(alice, 'alice', `bad${i}`, extra));
  }
  await assertFails(requestPlan(alice, 'alice', 'nc', {}, true, {}, ['sensitive_consent']));   // 이동 조건 있는데 동의 없음
});

test('계획 요청: 나라마다 2번까지 (같은 나라 세 번째는 거절, 다른 나라는 된다)', async () => {
  const alice = anon('alice');
  await assertSucceeds(requestPlan(alice, 'alice', 'r1'));
  await assertSucceeds(requestPlan(alice, 'alice', 'r2', {}, false, { counts: { JP: 1 } }));
  await assertFails(requestPlan(alice, 'alice', 'r3', {}, false, { counts: { JP: 2 } }));                 // 일본 세 번째
  await assertSucceeds(requestPlan(alice, 'alice', 'r4', { country: 'TH' }, false, { counts: { JP: 2 } })); // 태국 첫 번째
  // 횟수를 줄이거나(되돌리기) 다른 나라 횟수를 건드리거나 추가 횟수를 만들 수 없다
  await assertFails(requestPlan(alice, 'alice', 'r5', { country: 'TH' }, false, { counts: { JP: 2, TH: 1 }, raw: { counts: { JP: 0, TH: 2 } } }));
  await assertFails(requestPlan(alice, 'alice', 'r5', { country: 'TH' }, false, { counts: { JP: 2, TH: 1 }, raw: { counts: { JP: 2, TH: 2, VN: 0 } } }));
  await assertFails(requestPlan(alice, 'alice', 'r5', { country: 'VN' }, false, { counts: { JP: 2, TH: 1 }, extra: { VN: 5 } }));
  // 기록을 지우거나 새로 만들거나 요청 없이 고치기 불가
  await assertFails(deleteDoc(doc(alice, 'plan_quota/alice')));
  await assertFails(requestPlan(alice, 'alice', 'r6', { country: 'SG' }, true));
  await assertFails(updateDoc(doc(alice, 'plan_quota/alice'), { last: serverTimestamp(), lastRequestId: 'ghost', counts: { JP: 0 } }));
  // 서버가 JP 추가 횟수 1을 더해 준 사람은 세 번째까지 되고 네 번째는 안 된다(extra 는 그대로 되돌려 쓴다)
  await seed(async (db) => setDoc(doc(db, 'plan_quota/bob'), { last: daysAgo(3), lastRequestId: 'old', counts: { JP: 2 }, extra: { JP: 1 } }));
  const bob = anon('bob');
  await assertSucceeds(requestPlan(bob, 'bob', 'b3', {}, false, { counts: { JP: 2 }, extra: { JP: 1 } }));
  await assertFails(requestPlan(bob, 'bob', 'b4', {}, false, { counts: { JP: 3 }, extra: { JP: 1 } }));
  // 예전(7일 2회) 기록이 있는 사람도 나라별로 새로 센다
  await seed(async (db) => setDoc(doc(db, 'plan_quota/carol'), { last: daysAgo(3), prev: daysAgo(8), lastRequestId: 'old' }));
  await assertSucceeds(requestPlan(anon('carol'), 'carol', 'c1', {}, false, {}));
});

test('계획 요청: 이용자는 취소·취소한 요청 삭제만, 결과는 본인만 읽고 아무도 쓰지 못한다', async () => {
  await seed(async (db) => {
    await setDoc(doc(db, 'config/admins'), { uids: ['admin1'] });
    await setDoc(doc(db, 'plan_requests/r1'), { ...planData('alice'), createdAt: Timestamp.now() });
    await setDoc(doc(db, 'plan_requests/r2'), { ...planData('alice'), status: 'done', createdAt: Timestamp.now() });
    await setDoc(doc(db, 'plan_results/r1'), { uid: 'alice', request_id: 'r1', plan: { days: [] }, ai_generated: true });
  });
  const alice = anon('alice');
  await assertFails(updateDoc(doc(alice, 'plan_requests/r1'), { days: 5 }));
  await assertFails(updateDoc(doc(alice, 'plan_requests/r1'), { status: 'done', finishedAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(anon('bob'), 'plan_requests/r1'), { status: 'cancelled', finishedAt: serverTimestamp() }));
  await assertSucceeds(updateDoc(doc(alice, 'plan_requests/r1'), { status: 'cancelled', finishedAt: serverTimestamp() }));
  await assertFails(updateDoc(doc(alice, 'plan_requests/r2'), { status: 'cancelled', finishedAt: serverTimestamp() }));
  // 취소한 요청은 본인만 지울 수 있다(2026-10-09). 끝난 요청(r2)·남의 요청은 못 지운다
  await assertFails(deleteDoc(doc(anon('bob'), 'plan_requests/r1')));
  await assertFails(deleteDoc(doc(anon('bob'), 'plan_requests/r2')));
  await assertSucceeds(getDoc(doc(alice, 'plan_results/r1')));
  await assertSucceeds(getDoc(doc(anon('admin1'), 'plan_results/r1')));
  await assertSucceeds(getDoc(doc(alice, 'plan_results/not-yet')));                           // 아직 없으면 '없음'
  await assertFails(getDoc(doc(anon('bob'), 'plan_results/r1')));
  await assertSucceeds(getDocs(query(collection(alice, 'plan_results'), where('uid', '==', 'alice'))));
  await assertFails(getDocs(collection(anon('bob'), 'plan_results')));
  await assertFails(setDoc(doc(alice, 'plan_results/r9'), { uid: 'alice', plan: {} }));
  await assertFails(updateDoc(doc(alice, 'plan_results/r1'), { plan: { days: [1] } }));
  await assertFails(deleteDoc(doc(anon('bob'), 'plan_results/r1')));
  // 2026-10-09: 끝난 요청(취소·완료)과 내 결과는 본인이 지울 수 있다
  await assertSucceeds(deleteDoc(doc(alice, 'plan_results/r1')));
  await assertSucceeds(deleteDoc(doc(alice, 'plan_requests/r1')));
  await assertSucceeds(deleteDoc(doc(alice, 'plan_requests/r2')));
  await assertFails(setDoc(doc(anon('admin1'), 'plan_results/r9'), { uid: 'alice', plan: {} }));
});

// ---------------- AI 계획 신고 plan_flags/{requestId} ----------------
const flag = (uid, extra = {}) => ({ uid, reason: 'inaccurate', at: serverTimestamp(), ...extra });

test('계획 신고: 내 계획에만 한 번, 이유 4가지·메모 200자, 읽기는 운영자만, 고치기·지우기 불가', async () => {
  await seed(async (db) => {
    await setDoc(doc(db, 'config/admins'), { uids: ['admin1'] });
    for (const id of ['r1', 'r2', 'r3']) {
      await setDoc(doc(db, `plan_results/${id}`), { uid: 'alice', request_id: id, plan: { days: [] }, ai_generated: true });
    }
    await setDoc(doc(db, 'plan_results/b1'), { uid: 'bob', request_id: 'b1', plan: { days: [] }, ai_generated: true });
  });
  const alice = anon('alice');
  await assertSucceeds(setDoc(doc(alice, 'plan_flags/r1'), flag('alice')));                       // 메모 없이
  await assertSucceeds(setDoc(doc(alice, 'plan_flags/r2'), flag('alice', { reason: 'unsafe', note: '가짜 메모' })));
  await assertSucceeds(setDoc(doc(alice, 'plan_flags/r3'), flag('alice', { reason: 'other', note: '가'.repeat(200) })));
  // 같은 계획 두 번(= 고치기) · 지우기 불가
  await assertFails(setDoc(doc(alice, 'plan_flags/r1'), flag('alice', { reason: 'other' })));
  await assertFails(updateDoc(doc(alice, 'plan_flags/r1'), { reason: 'other' }));
  await assertFails(deleteDoc(doc(alice, 'plan_flags/r1')));
  await assertFails(deleteDoc(doc(anon('admin1'), 'plan_flags/r1')));
  // 이용자는 자기 신고도 읽지 못한다(운영자만)
  await assertFails(getDoc(doc(alice, 'plan_flags/r1')));
  await assertFails(getDocs(query(collection(alice, 'plan_flags'), where('uid', '==', 'alice'))));
  await assertFails(getDoc(doc(app(), 'plan_flags/r1')));
  await assertSucceeds(getDoc(doc(anon('admin1'), 'plan_flags/r1')));
  await assertSucceeds(getDocs(collection(anon('admin1'), 'plan_flags')));
  // 남의 계획·없는 계획·로그인 없이·남의 이름으로는 안 된다
  await assertFails(setDoc(doc(alice, 'plan_flags/b1'), flag('alice')));
  await assertFails(setDoc(doc(alice, 'plan_flags/none'), flag('alice')));
  await assertFails(setDoc(doc(app(), 'plan_flags/b1'), flag('bob')));
  await assertFails(setDoc(doc(anon('bob'), 'plan_flags/r2'), flag('alice')));
  // 모양이 틀리면 거절
  const bad = [
    { reason: 'spam' }, { reason: '' }, { note: '' }, { note: '가'.repeat(201) }, { note: 5 },
    { at: Timestamp.now() }, { passport_no: 'M00000000' }, { content: '계획 글' },
  ];
  for (const extra of bad) {
    await assertFails(setDoc(doc(anon('bob'), 'plan_flags/b1'), flag('bob', extra)));
  }
  await assertFails(setDoc(doc(anon('bob'), 'plan_flags/b1'), { uid: 'bob', at: serverTimestamp() }));   // 이유 없음
  await assertSucceeds(setDoc(doc(anon('bob'), 'plan_flags/b1'), flag('bob', { reason: 'inappropriate' })));
});
