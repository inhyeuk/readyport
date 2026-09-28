/*
 * 레디포트 자동 입력 엔진 (앱 내장, 원격으로 받지 않는다 — PRD 7.5).
 * 레시피는 선언형 데이터만 넘긴다. 이 파일은 앱 업데이트로만 바뀐다.
 *
 * 절대 하지 않는 것 (PRD 2.2):
 *  - 어떤 요소도 click() 하지 않는다 (제출·다음·체크 포함)
 *  - submit/button/checkbox/radio/file/password 입력칸을 건드리지 않는다
 *  - 보안 확인(캡차) 프레임에 손대지 않는다
 *  - innerHTML 을 쓰지 않는다 (말풍선 글자는 textContent 로만)
 */
(function () {
  'use strict';
  if (window.__readyport && window.__readyport.version === 1) return;

  var COLOR_FILLED = '#E8EEFC';   // accent-soft: 앱이 채운 칸
  var COLOR_CHECK = '#E6B566';    // caution 테두리: 사람이 확인할 칸
  var BUBBLE_ATTR = 'data-readyport-bubble';
  var FORBIDDEN_TYPES = ['submit', 'button', 'reset', 'checkbox', 'radio', 'file', 'password', 'hidden', 'image'];

  function q(selector) {
    if (!selector) return null;
    try { return document.querySelector(selector); } catch (e) { return null; }
  }

  function isWritableText(el) {
    if (!el) return false;
    var tag = el.tagName;
    if (tag === 'TEXTAREA') return !el.disabled && !el.readOnly;
    if (tag !== 'INPUT') return false;
    var type = (el.getAttribute('type') || 'text').toLowerCase();
    if (FORBIDDEN_TYPES.indexOf(type) >= 0) return false;
    // 목록에서 고르는 칸(자동 완성·선택)은 확인 전이라 채우지 않는다
    if (el.getAttribute('role') === 'combobox' || el.hasAttribute('aria-autocomplete')) return false;
    return !el.disabled && !el.readOnly;
  }

  // Angular/React 가 값 변경을 알아채도록 기본 setter + input/change/blur 이벤트
  function setValue(el, value) {
    var proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    var desc = Object.getOwnPropertyDescriptor(proto, 'value');
    desc.set.call(el, value);
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
    el.dispatchEvent(new Event('blur', { bubbles: true }));
  }

  function clearBubbles() {
    var old = document.querySelectorAll('[' + BUBBLE_ATTR + ']');
    for (var i = 0; i < old.length; i++) old[i].parentNode.removeChild(old[i]);
  }

  // 공식 사이트 칸 옆에 한글 말풍선 (PRD 5.3)
  function bubble(el, text, kind) {
    var r = el.getBoundingClientRect();
    var b = document.createElement('div');
    b.setAttribute(BUBBLE_ATTR, kind);
    b.textContent = text;
    var s = b.style;
    s.position = 'absolute';
    s.left = Math.max(4, r.left + window.scrollX) + 'px';
    s.top = Math.max(0, r.top + window.scrollY - 30) + 'px';
    s.zIndex = '2147483647';
    s.maxWidth = '90vw';
    s.padding = '4px 8px';
    s.borderRadius = '8px';
    s.font = '600 14px sans-serif';
    s.pointerEvents = 'none';
    s.boxShadow = '0 1px 4px rgba(0,0,0,.25)';
    if (kind === 'filled') { s.background = '#1F4FD1'; s.color = '#FFFFFF'; }
    else { s.background = '#FFF4DC'; s.color = '#7A4100'; s.border = '1px solid ' + COLOR_CHECK; }
    document.body.appendChild(b);
  }

  function mark(el, kind) {
    if (kind === 'filled') el.style.backgroundColor = COLOR_FILLED;
    else { el.style.outline = '3px solid ' + COLOR_CHECK; el.style.outlineOffset = '2px'; }
  }

  /**
   * 현재 화면에 보이는 칸만 채운다. 사이트의 '다음'은 사람이 누른다.
   * plan = { fields:[{key, selector, widget, value, label, hint}], checkpoints:[{id, selector, ko}] }
   * 돌려주는 값(JSON 문자열): { filled:[key], assist:[key], missing:[key], absent:[key], checkpoints:[id] }
   *   missing = 선택자가 있는데 찾지 못함(사이트가 바뀌었을 수 있음), absent = 선택자 없는 도움 칸
   */
  function fill(plan) {
    clearBubbles();
    var report = { filled: [], assist: [], missing: [], absent: [], checkpoints: [] };
    (plan.fields || []).forEach(function (f) {
      var el = q(f.selector);
      if (!f.selector) { report.absent.push(f.key); return; }
      if (!el) { report.missing.push(f.key); return; }
      if (f.widget === 'text' && typeof f.value === 'string' && f.value.length > 0 && isWritableText(el)) {
        setValue(el, f.value);
        mark(el, 'filled');
        bubble(el, f.label, 'filled');
        report.filled.push(f.key);
      } else {
        mark(el, 'check');
        var text = f.label + (f.value ? ': ' + f.value : '') + (f.hint ? ' — ' + f.hint : '');
        bubble(el, text, 'check');
        report.assist.push(f.key);
      }
    });
    (plan.checkpoints || []).forEach(function (c) {
      var el = q(c.selector);
      if (!el) return;
      mark(el, 'check');
      bubble(el, c.ko, 'check');
      report.checkpoints.push(c.id);
    });
    return JSON.stringify(report);
  }

  /** 입력값 대조(PRD 5.3): 채운 칸의 지금 값을 읽어 돌려준다 */
  function read(keysToSelectors) {
    var out = {};
    Object.keys(keysToSelectors).forEach(function (k) {
      var el = q(keysToSelectors[k]);
      out[k] = el && typeof el.value === 'string' ? el.value : null;
    });
    return JSON.stringify(out);
  }

  /** 지금 화면에 있는 단계들 (probe 선택자가 보이는지) */
  function probe(selectors) {
    return JSON.stringify(selectors.map(function (s) { return !!q(s); }));
  }

  function siteVersion() {
    var body = document.body;
    var m = (body ? (body.innerText || body.textContent || '') : '').match(/Version\s+([0-9][0-9.\-]+)/);
    return m ? m[1] : null;
  }

  window.__readyport = { version: 1, fill: fill, read: read, probe: probe, siteVersion: siteVersion, clear: clearBubbles };
})();
