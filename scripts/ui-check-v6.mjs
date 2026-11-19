// v6 页面检查：药房发药扫码（先通过 API 造一张待发药处方）
function outPath(name) {
  // 截图目录：设置环境变量 SHOT_DIR 为绝对路径（如 D:\shots），默认相对当前工作目录的 screenshots/
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

await page.goto('http://127.0.0.1:8080/login', { waitUntil: 'domcontentloaded' });
await page.evaluate(function () { localStorage.clear(); });
await page.reload({ waitUntil: 'domcontentloaded' });
await page.waitForTimeout(1200);
var inputs = page.locator('input');
await inputs.nth(0).fill('admin');
await inputs.nth(1).fill('123456');
await inputs.nth(1).press('Enter');
await page.waitForTimeout(3000);

// 用页面内 fetch（带 token）造一张待发药处方：患者→挂号→接诊→开方→收费
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
  const patient = await api('POST', '/patients', { name: '扫码发药演示', gender: 'MALE', phone: '138' + String(Date.now()).slice(-8) });
  const visit = await api('POST', '/visits/register', { patientId: patient.id, doctorId: doctors[0].id, type: 'FIRST', complaint: '乏力纳差 2 周' });
  await api('POST', '/visits/' + visit.id + '/start');
  const drugs = await api('GET', '/drugs?category=HERB&page=1&size=20');
  const drug = drugs.list[0];
  const rx = await api('POST', '/prescriptions', {
    visitId: visit.id, type: 'HERB', decoctionMethod: '自煎', doses: 7,
    items: [{ drugId: drug.id, dose: 10, quantity: 70, usageText: '水煎服' }]
  });
  const charges = await api('GET', '/charges?status=UNPAID&page=1&size=20');
  for (const order of charges.list.filter(function (o) { return o.visitId === visit.id; })) {
    await api('POST', '/charges/' + order.id + '/pay', { method: 'CASH' });
  }
  return { prescriptionId: rx.id, drugCode: drug.code };
}, token);

await page.goto('http://127.0.0.1:8080/pharmacy/dispense', { waitUntil: 'domcontentloaded' });
await page.waitForTimeout(2500);
await shot('20-scan-dispense-list');

// 打开第一条的明细抽屉（扫码区在抽屉顶部）
var detailButtons = page.getByRole('button', { name: '明细' });
var count = await detailButtons.count();
if (count > 0) {
  await detailButtons.first().click();
  await page.waitForTimeout(2000);
  await shot('21-scan-dispense-drawer');
}

var bodyText = await page.evaluate(function () { return document.body.innerText.slice(0, 400); });
return { ok: true, created: created, shots: globalThis.uiShots, errors: globalThis.uiErrors, bodyText: bodyText };
