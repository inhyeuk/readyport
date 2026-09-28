// 앱 내장 자동 입력 엔진 검사 (node --test). 실제 정부 사이트에는 연결하지 않는다 (작업 규칙 1).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { JSDOM } from 'jsdom';

const root = new URL('../../', import.meta.url);
const engine = readFileSync(new URL('app/src/main/assets/autofill/engine.js', root), 'utf8');
const mock = readFileSync(new URL('./mock_tdac.html', import.meta.url), 'utf8');
const recipe = JSON.parse(readFileSync(new URL('packs/src/recipes/TH_TDAC.json', root), 'utf8'));

function page() {
  const dom = new JSDOM(mock, { runScripts: 'outside-only', url: 'https://tdac.immigration.go.th/arrival-card/#/add' });
  const w = dom.window;
  // 엔진이 click 을 부르거나 제출을 일으키면 기록한다
  const spy = { clicks: 0, submits: 0, inputEvents: 0 };
  w.HTMLElement.prototype.click = function () { spy.clicks++; };
  w.document.addEventListener('click', () => spy.clicks++, true);
  w.document.getElementById('form').addEventListener('submit', (e) => { spy.submits++; e.preventDefault(); });
  w.document.addEventListener('input', () => spy.inputEvents++, true);
  w.eval(engine);
  return { w, doc: w.document, rp: w.__readyport, spy };
}

const values = {
  'passport.surname': 'ERIKSSON', 'passport.given': 'ANNA MARIA', 'passport.number': 'L898902C3',
  'passport.nationality': '대한민국 (KOR)', 'passport.birth_date': '1974-08-12', 'passport.gender': '여 (Female)',
  'profile.occupation': 'OFFICE WORKER', 'profile.country_res': '대한민국', 'profile.city_res': 'SEOUL',
  'profile.phone': '1012345678', 'profile.phone_code': '82', 'trip.arrival_date': '2026/11/03', 'trip.purpose': '관광 → HOLIDAY',
  'trip.arrival_mode': '비행기 → AIR', 'trip.flight_no': 'KE651', 'stay.address': '1 SAMPLE ROAD, BANGKOK', 'stay.type': '호텔 → HOTEL',
};

function plan(stepIds) {
  const fields = recipe.steps.filter((s) => stepIds.includes(s.id)).flatMap((s) => s.fields).map((f) => ({
    key: f.key, selector: f.selector, widget: f.widget, value: values[f.key] ?? '', label: f.labels.ko, hint: f.hint_ko ?? null,
  }));
  return { fields, checkpoints: recipe.checkpoints };
}

test('채울 수 있는 글자 칸만 채우고 이벤트를 보낸다', () => {
  const { doc, rp, spy } = page();
  const report = JSON.parse(rp.fill(plan(['personal', 'trip'])));
  assert.deepEqual(report.filled.sort(), ['passport.given', 'passport.number', 'passport.surname', 'profile.occupation',
    'profile.phone', 'profile.phone_code', 'stay.address', 'trip.arrival_date', 'trip.flight_no'].sort());
  assert.equal(doc.querySelector('[formcontrolname="arrDate"]').value, '2026/11/03');
  assert.equal(doc.querySelector('[formcontrolname="familyName"]').value, 'ERIKSSON');
  assert.equal(doc.querySelector('[formcontrolname="accAddress"]').value, '1 SAMPLE ROAD, BANGKOK');
  assert.ok(spy.inputEvents >= report.filled.length);
  assert.equal(doc.querySelector('[formcontrolname="familyName"]').style.backgroundColor, 'rgb(232, 238, 252)');
});

test('목록·달력 칸은 채우지 않고 말풍선으로 값을 보여 준다', () => {
  const { doc, rp } = page();
  const report = JSON.parse(rp.fill(plan(['personal', 'trip'])));
  for (const k of ['passport.gender', 'trip.purpose', 'trip.arrival_mode', 'stay.type']) assert.ok(report.assist.includes(k), k);
  const texts = [...doc.querySelectorAll('[data-readyport-bubble]')].map((b) => b.textContent);
  // 사이트에서 골라야 할 실제 선택지 글자를 말풍선에 보여 준다
  assert.ok(texts.some((t) => t.includes('여행 목적') && t.includes('HOLIDAY')));
  assert.ok(texts.some((t) => t.includes('숙소 종류') && t.includes('HOTEL')));
  // 선택자가 없는 도움 칸(국적·사는 나라 등)은 absent 로 따로 알린다
  assert.ok(report.absent.includes('passport.nationality'));
  assert.ok(report.absent.includes('profile.country_res'));
  // 가짜 화면에 없는 칸 → missing (사이트가 바뀌면 이렇게 잡힌다)
  assert.ok(report.missing.includes('trip.transport_mode'));
});

test('절대 누르거나 제출하거나 체크하지 않는다', () => {
  const { doc, rp, spy } = page();
  const p = plan(['personal', 'trip']);
  // 악의적인 레시피가 제출 버튼·체크박스·비밀번호를 text 칸으로 속여도
  p.fields.push({ key: 'x.submit', selector: 'button[type="submit"]', widget: 'text', value: 'x', label: 'x' });
  p.fields.push({ key: 'x.declare', selector: '[formcontrolname="checkedDecalraion"]', widget: 'text', value: 'true', label: 'x' });
  p.fields.push({ key: 'x.password', selector: '#secret', widget: 'text', value: 'hunter2', label: 'x' });
  const report = JSON.parse(rp.fill(p));
  assert.equal(spy.clicks, 0);
  assert.equal(spy.submits, 0);
  assert.equal(doc.querySelector('[formcontrolname="checkedDecalraion"]').checked, false);
  assert.equal(doc.querySelector('#secret').value, '');
  assert.ok(!report.filled.includes('x.submit') && !report.filled.includes('x.declare') && !report.filled.includes('x.password'));
});

test('사람이 할 곳(보안 확인·서약·이메일·제출)을 표시한다', () => {
  const { rp } = page();
  const report = JSON.parse(rp.fill(plan(['personal'])));
  assert.deepEqual(report.checkpoints.sort(), ['captcha', 'declaration', 'email', 'final_submit'].sort());
});

test('말풍선 글자는 HTML 로 해석되지 않는다', () => {
  const { doc, rp } = page();
  rp.fill({ fields: [{ key: 'a.b', selector: '[formcontrolname="familyName"]', widget: 'assist', value: '<img src=x onerror=alert(1)>', label: '<b>x</b>' }] });
  assert.equal(doc.querySelectorAll('img').length, 0);
  assert.equal(doc.querySelectorAll('b').length, 0);
});

test('다시 채우면 말풍선이 쌓이지 않는다', () => {
  const { doc, rp } = page();
  rp.fill(plan(['personal']));
  const n = doc.querySelectorAll('[data-readyport-bubble]').length;
  rp.fill(plan(['personal']));
  assert.equal(doc.querySelectorAll('[data-readyport-bubble]').length, n);
});

test('입력값 대조·단계 확인·사이트 버전', () => {
  const { rp } = page();
  rp.fill(plan(['personal']));
  const read = JSON.parse(rp.read({ 'passport.surname': '[formcontrolname="familyName"]', 'nope': '#nope' }));
  assert.equal(read['passport.surname'], 'ERIKSSON');
  assert.equal(read['nope'], null);
  assert.deepEqual(JSON.parse(rp.probe(recipe.steps.map((s) => s.probe))), [true, true, false]);
  assert.equal(rp.siteVersion(), recipe.site_version_seen);
});
