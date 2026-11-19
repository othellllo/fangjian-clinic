# 开发脚本说明

| 脚本 | 用途 |
| --- | --- |
| `start-dev.ps1` | 一键拉起前后端开发环境（自动定位 JDK 21 与 Maven） |
| `smoke.mjs` | 端到端接口冒烟测试（33 项）：门诊闭环 + 代煎 + 小程序预约/报到 + 越权校验 + 审计 + 401/403 |
| `ui-check.mjs` | 用真实浏览器走主要页面并截图（v1 页面） |
| `ui-check-v2.mjs` | v2 页面检查：操作审计、代煎管理、挂号台「今日预约」tab |
| `backup-mysql.ps1` | MySQL 逻辑备份（gzip + 自动清理过期备份，支持本机 mysqldump 或 docker exec） |
| `restore-mysql.ps1` | 从备份恢复（交互确认，避免误删数据） |

## 用法

```powershell
# 1. 一键启动（后端 + 前端）
powershell -ExecutionPolicy Bypass -File scripts\start-dev.ps1

# 2. 端到端冒烟（后端需已运行在 8080）
node scripts\smoke.mjs
# 可选环境变量：BASE_URL、SMOKE_USER、SMOKE_PASS

# 3. 生产备份 / 恢复
powershell -File scripts\backup-mysql.ps1 -Password '库密码' -OutDir D:\his-backup -KeepDays 30
powershell -File scripts\restore-mysql.ps1 -File D:\his-backup\yiguan_his-20261118-020000.sql.gz -Password '库密码'

# 4. UI 截图检查（需要 Tabbit 浏览器 CLI）
# 用法见 ui-check.mjs 头部注释
```

## 常见环境问题

- **Windows 上 `npm run build` 报 esbuild `Access is denied`**：本机安全策略限制了 `node_modules/@esbuild/**/esbuild.exe` 读取目录。
  可用如下方式绕过（把二进制复制到临时目录再指过去）：

  ```powershell
  $dst = "$env:TEMP\esbuild\esbuild.exe"
  New-Item -ItemType Directory -Force (Split-Path $dst) | Out-Null
  Copy-Item frontend\node_modules\@esbuild\win32-x64\esbuild.exe $dst -Force
  $env:ESBUILD_BINARY_PATH = $dst
  cd frontend; npm run build
  ```

  这是开发机环境问题，与项目代码无关；CI 与 Linux 构建不受影响。

- **`npm run build` 在 PowerShell 里报 `failed to load config`**：同样先在 `frontend` 目录设置
  `$env:ESBUILD_BINARY_PATH`，再执行 `node .\node_modules\vite\bin\vite.js build`。

## v3 新增脚本

| 脚本 | 用途 |
| --- | --- |
| `smoke-v3.mjs` | v3 模块冒烟（19 项）：打印 HTML、追溯码全流程、设置、导出 BOM、政策库、医保统计 |
| `build-portable.ps1` | 一键构建绿色便携版（前端构建 → jar 打包 → jlink 运行时 → 组装 → 可选 zip） |

用法：

```powershell
# 先启动后端并准备数据（v1/v2 冒烟会造数据）
node scripts\smoke.mjs
node scripts\smoke-v3.mjs

# 构建便携版（-Zip 额外生成 zip）
powershell -ExecutionPolicy Bypass -File scripts\build-portable.ps1 -Zip
```

## v4 新增脚本

| 脚本 | 用途 |
| --- | --- |
| `smoke-v4.mjs` | v4 模块冒烟（17 项）：联想、中医/西医质控、AI 本地助写、十八反/妊娠禁用/剂量审查、先煎自动标注、病历保存校验 |
| `ui-check-v4.mjs` | 医生工作台截图（中医病历表单 + 质控 + AI 助写按钮） |

## v6 新增脚本

| 脚本 | 用途 |
| --- | --- |
| `smoke-v6.mjs` | 扫码发药冒烟（9 项）：饮片按量扣库存、扫完自动完成、旧发药接口受保护、需追溯品种拦截条码、追溯码逐件核销、直接出库 |
