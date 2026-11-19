// UI 冒烟检查：真实浏览器走一遍主要页面，截图输出到 screenshots/
// 说明：Tabbit 注入环境对顶层变量作用域较严格，这里只用函数声明 + globalThis。
function outPath(name) {
  // 截图目录：设置环境变量 SHOT_DIR 为绝对路径（如 D:\shots），默认相对当前工作目录的 screenshots/
  var dir = process.env.SHOT_DIR || 'screenshots';
  return dir.replace(/[\\/]+$/, '') + '/' + name + '.png';
}

globalThis.uiErrors = [];
globalThis.uiShots = [];

page.on('pageerror', function (e) {
  // 忽略浏览器扩展（chrome-extension://）自身的脚本错误，只关心应用代码
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

await page.goto('http://127.0.0.1:5173/login', { waitUntil: 'domcontentloaded' });
// 清掉上次登录缓存，确保截到真正的登录页
await page.evaluate(function () { localStorage.clear(); });
await page.reload({ waitUntil: 'domcontentloaded' });
await page.waitForTimeout(1200);
await shot('01-login');

var inputs = page.locator('input');
var count = await inputs.count();
if (count < 2) {
  return { ok: false, reason: 'login inputs not found', count: count };
}

await inputs.nth(0).fill('admin');
await inputs.nth(1).fill('123456');
await inputs.nth(1).press('Enter');
await page.waitForTimeout(3500);
var afterLogin = page.url();
await shot('02-after-login');

var targets = [
  ['registration', '03-registration-desk'],
  ['doctor', '04-doctor-workbench'],
  ['cashier', '05-cashier'],
  ['pharmacy/dispense', '06-pharmacy'],
  ['reports', '07-reports'],
  ['patients', '08-patients']
];
for (var i = 0; i < targets.length; i++) {
  await page.goto('http://127.0.0.1:5173/' + targets[i][0], { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(1800);
  await shot(targets[i][1]);
}

var bodyText = await page.evaluate(function () { return document.body.innerText.slice(0, 300); });
return { ok: true, afterLogin: afterLogin, shots: globalThis.uiShots, errors: globalThis.uiErrors, bodyText: bodyText };
