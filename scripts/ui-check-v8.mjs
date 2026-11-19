// v8 截图 + 键盘流验证：选药 → 克数 → 回车回检索 → 再用量下拉
function outPath(name) {
  var dir = (typeof SHOT_DIR !== 'undefined' && SHOT_DIR) ? SHOT_DIR : 'screenshots';
  return dir.replace(/[\\/]+$/, '') + '/' + name + '.png';
}
globalThis.uiErrors = [];
globalThis.uiShots = [];
page.on('pageerror', function (e) {
  var text = String(e && e.stack ? e.stack : e);
  if (text.indexOf('chrome-extension://') >= 0) return;
  globalThis.uiErrors.push(String(e));
});
page.on('console', function (m) {
  if (m.type() !== 'error') return;
  var text = String(m.text() || '');
  if (text.indexOf('chrome-extension://') >= 0) return;
  globalThis.uiErrors.push('console: ' + text);
});
async function shot(name) {
  await page.screenshot({ path: outPath(name), fullPage: false });
  globalThis.uiShots.push(name);
}

await page.setViewportSize({ width: 1600, height: 900 });
await page.goto(APP_URL + '/login', { waitUntil: 'domcontentloaded' });
await page.evaluate(function () { localStorage.clear(); });
await page.reload({ waitUntil: 'domcontentloaded' });
await page.waitForTimeout(1200);
var inputs = page.locator('input');
await inputs.nth(0).fill('admin');
await inputs.nth(1).fill('123456');
await inputs.nth(1).press('Enter');
await page.waitForTimeout(2500);

var token = await page.evaluate(function () { return localStorage.getItem('fangjian_token'); });
await page.evaluate(async function (t) {
  async function api(method, path, body) {
    const res = await fetch('/api' + path, {
      method,
      headers: { 'Content-Type': 'application/json; charset=utf-8', Authorization: 'Bearer ' + t },
      body: body ? JSON.stringify(body) : undefined
    });
    const json = await res.json();
    if (json.code !== 0) throw new Error(path + ': ' + json.message);
    return json.data;
  }
  const doctors = await api('GET', '/users/doctors');
  const patient = await api('POST', '/patients', { name: '键盘流演示', gender: 'MALE', phone: '129' + String(Date.now()).slice(-8) });
  await api('POST', '/visits/register', { patientId: patient.id, doctorId: doctors[0].id, type: 'FIRST', complaint: '胃脘胀痛 3 月' });
}, token);

await page.goto(APP_URL + '/doctor', { waitUntil: 'domcontentloaded' });
await page.waitForTimeout(2500);
var startBtn = page.getByRole('button', { name: '接诊', exact: true });
if (await startBtn.count() > 0) {
  await startBtn.first().click();
  await page.waitForTimeout(2200);
}

var drugInput = page.locator('.drug-select input').first();
await drugInput.click();
await page.waitForTimeout(400);
await drugInput.fill('hq');
await page.waitForTimeout(1200);
await page.keyboard.press('Enter');        // 选中第一个（黄芪）
await page.waitForTimeout(900);
var activeAfterPick = await page.evaluate(function () {
  var el = document.activeElement;
  return el ? (el.className || '') + '|' + (el.tagName || '') : 'none';
});
await page.keyboard.type('10');            // 直接写克数
await page.keyboard.press('Enter');        // 回药品检索
await page.waitForTimeout(800);
var activeAfterDose = await page.evaluate(function () {
  var el = document.activeElement;
  return el ? (el.className || '') + '|' + (el.tagName || '') : 'none';
});

// 再录第二味药
await drugInput.fill('hq');
await page.waitForTimeout(1200);
await page.keyboard.press('Enter');
await page.waitForTimeout(900);
await page.keyboard.type('6');
await page.keyboard.press('Enter');
await page.waitForTimeout(900);
await shot('25-prescription-keyboard-flow');

return {
  ok: true, shots: globalThis.uiShots, errors: globalThis.uiErrors,
  activeAfterPick: activeAfterPick, activeAfterDose: activeAfterDose
};
