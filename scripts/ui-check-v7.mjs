// v7 页面检查：医生工作台新布局（开方区占主导）+ 拼音首字母搜药下拉
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

await page.goto(APP_URL + '/doctor', { waitUntil: 'domcontentloaded' });
await page.waitForTimeout(2500);

// 选中队列第一个患者（进入病历/处方编辑态）
var cards = page.locator('.queue-card');
if (await cards.count() > 0) {
  await cards.first().click();
  await page.waitForTimeout(2000);
}
await shot('22-workbench-layout');

// 拼音首字母搜药
var drugInput = page.getByPlaceholder('药名 / 拼音首字母，如 hq');
if (await drugInput.count() > 0) {
  await drugInput.first().click();
  await drugInput.first().fill('hq');
  await page.waitForTimeout(1500);
  await shot('23-drug-pinyin-search');
}

var layout = await page.evaluate(function () {
  var el = document.querySelector('.columns');
  if (!el) return null;
  var s = getComputedStyle(el);
  return { cols: s.gridTemplateColumns, width: el.clientWidth };
});
return { ok: true, shots: globalThis.uiShots, errors: globalThis.uiErrors, layout: layout };
