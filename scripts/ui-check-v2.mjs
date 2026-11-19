// v2 页面检查：操作审计、代煎管理、挂号台「今日预约」tab
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

await page.goto(APP_URL + '/login', { waitUntil: 'domcontentloaded' });
await page.evaluate(function () { localStorage.clear(); });
await page.reload({ waitUntil: 'domcontentloaded' });
await page.waitForTimeout(1000);

var inputs = page.locator('input');
await inputs.nth(0).fill('admin');
await inputs.nth(1).fill('123456');
await inputs.nth(1).press('Enter');
await page.waitForTimeout(3000);

// 挂号台 + 今日预约 tab
await page.goto(APP_URL + '/registration', { waitUntil: 'domcontentloaded' });
await page.waitForTimeout(1500);
await page.getByRole('tab').nth(2).click();
await page.waitForTimeout(1500);
await shot('09-registration-appointments');

// 代煎管理
await page.goto(APP_URL + '/pharmacy/decoction', { waitUntil: 'domcontentloaded' });
await page.waitForTimeout(2000);
await shot('10-decoction-manage');

// 操作审计
await page.goto(APP_URL + '/system/audit', { waitUntil: 'domcontentloaded' });
await page.waitForTimeout(2000);
await shot('11-audit-log');

var bodyText = await page.evaluate(function () { return document.body.innerText.slice(0, 400); });
return { ok: true, shots: globalThis.uiShots, errors: globalThis.uiErrors, bodyText: bodyText };
