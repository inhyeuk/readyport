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
