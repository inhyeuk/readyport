// Firestore 규칙 테스트 (에뮬레이터). 실행: cd tools/firestore && npm test
// firebase emulators:exec --only firestore "node --test rules.test.mjs"
import { test, before, after, beforeEach } from 'node:test';
import { readFileSync } from 'node:fs';
import {
  initializeTestEnvironment, assertFails, assertSucceeds,
} from '@firebase/rules-unit-testing';
import {
  doc, setDoc, getDoc, updateDoc, deleteDoc, addDoc, collection, serverTimestamp, increment, Timestamp,
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
