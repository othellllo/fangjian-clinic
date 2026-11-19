// v4 页面检查：医生工作台（中医病历 / 西医切换 / 必填 / 质控 / AI 助写 / 处方安全）
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

await page.goto('http://127.0.0.1:8080/doctor', { waitUntil: 'domcontentloaded' });
await page.waitForTimeout(2500);
await shot('17-clinical-workbench');

// 选中队列里第一个患者（如果有）
var queueCards = page.locator('.queue-card');
var count = await queueCards.count();
if (count > 0) {
  await queueCards.first().click();
  await page.waitForTimeout(2500);
  await shot('18-clinical-record-form');
}

var bodyText = await page.evaluate(function () { return document.body.innerText.slice(0, 500); });
return { ok: true, shots: globalThis.uiShots, errors: globalThis.uiErrors, queueCount: count, bodyText: bodyText };
