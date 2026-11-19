// v7 截图：先造一个候诊患者 → 接诊 → 布局/常见项/首拼搜药
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
var created = await page.evaluate(async function (t) {
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
  const patient = await api('POST', '/patients', { name: '开方界面演示', gender: 'MALE', phone: '131' + String(Date.now()).slice(-8) });
  const visit = await api('POST', '/visits/register', {
    patientId: patient.id, doctorId: doctors[0].id, type: 'FIRST', complaint: '胃脘胀痛 3 月'
  });
  return { visitId: visit.id };
}, token);

await page.goto(APP_URL + '/doctor', { waitUntil: 'domcontentloaded' });
await page.waitForTimeout(2500);
var startBtn = page.getByRole('button', { name: '接诊', exact: true });
if (await startBtn.count() > 0) {
  await startBtn.first().click();
  await page.waitForTimeout(2500);
}
await shot('22-workbench-layout');

// 主诉空框 → 常见项
var chief = page.locator('.record-input').first();
var chiefOk = false;
if (await chief.count() > 0) {
  await chief.click();
  await page.waitForTimeout(1800);
  chiefOk = true;
  await shot('24-chief-complaint-suggestions');
  await page.keyboard.press('Escape');
}

// 拼音首字母搜药
var drugInput = page.locator('.drug-select input').first();
var drugOk = false;
if (await drugInput.count() > 0) {
  await drugInput.click();
  await page.waitForTimeout(400);
  await drugInput.fill('hq');
  await page.waitForTimeout(2000);
  drugOk = true;
  await shot('23-drug-pinyin-search');
}

return { ok: true, shots: globalThis.uiShots, errors: globalThis.uiErrors, chiefOk: chiefOk, drugOk: drugOk, created: created };
