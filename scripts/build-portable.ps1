# 构建「绿色便携版」发布包
#
# 产物：<仓库>\release\方笺系统\
#   ├─ 启动方笺系统.bat / 停止方笺系统.bat / 打开方笺系统.bat
#   ├─ runtime\         jlink 裁剪的运行环境（用户不用装 Java）
#   ├─ app\方笺系统.jar  后端 + 前端静态资源（单进程，一个端口）
#   ├─ config\          端口等可调配置
#   ├─ data\            运行后自动生成：数据库
#   ├─ backup\          每次启动自动备份，保留 7 份
#   ├─ logs\            运行日志
#   └─ 使用说明.txt
#
# 用法：powershell -ExecutionPolicy Bypass -File scripts\build-portable.ps1 [-SkipFrontend] [-SkipJlink] [-Zip]

param(
    [switch]$SkipFrontend,
    [switch]$SkipJlink,
    [switch]$Zip
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$releaseRoot = Join-Path $root 'release'
$appDir = Join-Path $releaseRoot '方笺系统'
$jarName = 'fangjian-clinic.jar'

# 1. 定位 JDK
$jdk = $env:JAVA_HOME
if (-not $jdk -or -not (Test-Path (Join-Path $jdk 'bin\jlink.exe'))) {
    $candidates = @()
    if (Test-Path "$env:USERPROFILE\.jdks") {
        $candidates += Get-ChildItem "$env:USERPROFILE\.jdks" -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match 'jdk-2[1-9]' } | Sort-Object Name -Descending
    }
    if ($candidates.Count -gt 0) { $jdk = $candidates[0].FullName }
}
if (-not $jdk -or -not (Test-Path (Join-Path $jdk 'bin\jlink.exe'))) {
    throw "未找到 JDK 21（需要 bin\jlink.exe），请设置 JAVA_HOME"
}
Write-Host "[build] JDK = $jdk" -ForegroundColor Cyan

# 2. 定位 Maven
$mvn = (Get-Command mvn.cmd -ErrorAction SilentlyContinue).Source
if (-not $mvn) {
    foreach ($base in @((Join-Path $root '.tools\maven'), (Join-Path $root '..\.tools\maven'))) {
        if (-not (Test-Path $base)) { continue }
        $direct = Join-Path $base 'bin\mvn.cmd'
        if (Test-Path $direct) { $mvn = $direct; break }
        $local = Get-ChildItem $base -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($local -and (Test-Path (Join-Path $local.FullName 'bin\mvn.cmd'))) { $mvn = Join-Path $local.FullName 'bin\mvn.cmd'; break }
    }
}
if (-not $mvn) { throw "未找到 Maven" }
$settings = Join-Path $root '.tools\maven-settings.xml'
$mvnArgs = @('-B', '-q', '-DskipTests', 'package')
if (Test-Path $settings) { $mvnArgs = @('-B', '-q', '-DskipTests', '-s', $settings, 'package') }

# 3. 构建前端（打进 jar）
if (-not $SkipFrontend) {
    $frontend = Join-Path $root 'frontend'
    if (-not (Test-Path (Join-Path $frontend 'node_modules'))) {
        Write-Host "[build] 安装前端依赖（首次较慢）..." -ForegroundColor Yellow
        Push-Location $frontend
        npm install --no-audit --no-fund
        Pop-Location
    }
    Write-Host "[build] 构建前端 ..." -ForegroundColor Cyan
    $esbuildDst = Join-Path $env:TEMP 'fangjian-esbuild-build\esbuild.exe'
    New-Item -ItemType Directory -Force (Split-Path $esbuildDst) | Out-Null
    $esbuildSrc = Join-Path $frontend 'node_modules\@esbuild\win32-x64\esbuild.exe'
    if (Test-Path $esbuildSrc) {
        try {
            Copy-Item $esbuildSrc $esbuildDst -Force
            $env:ESBUILD_BINARY_PATH = $esbuildDst
        } catch {
            # 目标文件被其他进程占用时，退回复制到带随机后缀的临时文件
            $fallback = Join-Path $env:TEMP ("fangjian-esbuild-build\esbuild-" + [guid]::NewGuid().ToString('N').Substring(0,8) + ".exe")
            Copy-Item $esbuildSrc $fallback -Force
            $env:ESBUILD_BINARY_PATH = $fallback
        }
    }
    Push-Location $frontend
    node .\node_modules\vite\bin\vite.js build
    Pop-Location
    if (-not (Test-Path (Join-Path $frontend 'dist\index.html'))) { throw "前端构建失败：dist/index.html 不存在" }
}

# 4. 构建后端 jar（自动把 frontend/dist 打进 static/）
Write-Host "[build] 构建后端 jar ..." -ForegroundColor Cyan
Push-Location (Join-Path $root 'backend')
& $mvn @mvnArgs
if ($LASTEXITCODE -ne 0) { Pop-Location; throw "后端构建失败" }
Pop-Location
$builtJar = Get-ChildItem (Join-Path $root 'backend\target\*.jar') | Where-Object { $_.Name -notmatch 'original' } | Select-Object -First 1
if (-not $builtJar) { throw "未找到后端 jar" }

# 5. 组装发布目录
Write-Host "[build] 组装发布目录：$appDir" -ForegroundColor Cyan
if (Test-Path $appDir) { Remove-Item $appDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path (Join-Path $appDir 'app'), (Join-Path $appDir 'config'), (Join-Path $appDir 'data'), (Join-Path $appDir 'backup'), (Join-Path $appDir 'logs') | Out-Null
Copy-Item $builtJar.FullName (Join-Path $appDir "app\$jarName") -Force

# 6. jlink 裁剪运行时
if (-not $SkipJlink) {
    Write-Host "[build] 生成内置运行环境（jlink，约 1-2 分钟）..." -ForegroundColor Cyan
    $jlink = Join-Path $jdk 'bin\jlink.exe'
    $modules = @(
        'java.base', 'java.compiler', 'java.datatransfer', 'java.desktop', 'java.instrument',
        'java.logging', 'java.management', 'java.naming', 'java.net.http', 'java.prefs',
        'java.rmi', 'java.scripting', 'java.security.jgss', 'java.security.sasl', 'java.sql',
        'java.transaction.xa', 'java.xml', 'java.xml.crypto',
        'jdk.crypto.ec', 'jdk.crypto.cryptoki', 'jdk.localedata', 'jdk.management',
        'jdk.naming.rmi', 'jdk.unsupported', 'jdk.zipfs'
    ) -join ','
    & $jlink --add-modules $modules --output (Join-Path $appDir 'runtime') --strip-debug --no-header-files --no-man-pages --compress=zip-6
    if ($LASTEXITCODE -ne 0) { throw "jlink 失败" }
} else {
    throw "本脚本需要内置运行环境，不能跳过 jlink"
}

# 7. 启动 / 停止 / 打开脚本 + 配置 + 说明
# 注意：.bat 用 GBK（中文 Windows 的 cmd 默认代码页 936）写入，避免乱码；
#       .txt 用带 BOM 的 UTF-8，记事本可直接打开；.properties 用无 BOM UTF-8。
$utf8Bom = New-Object System.Text.UTF8Encoding($true)
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$gbk = [System.Text.Encoding]::GetEncoding(936)
# Windows 的 .bat 必须是 CRLF 换行，否则 cmd 会把多行结构解析错（echo 被当成命令）
function ToCrlf($text) {
    return (($text -replace "`r`n", "`n") -replace "`n", "`r`n")
}
function Write-Text($path, $content) {
    [System.IO.File]::WriteAllText($path, (ToCrlf $content), $utf8Bom)
}
function Write-Config($path, $content) {
    [System.IO.File]::WriteAllText($path, (ToCrlf $content), $utf8NoBom)
}
function Write-Bat($path, $content) {
    [System.IO.File]::WriteAllText($path, (ToCrlf $content), $gbk)
}

Write-Bat (Join-Path $appDir '启动方笺系统.bat') @'
@echo off
chcp 936 >nul
title 方笺系统
cd /d "%~dp0"
if not exist "runtime\bin\javaw.exe" (
  echo [错误] 缺少运行环境 runtime\，请确认压缩包解压完整。
  pause & exit /b 1
)
if not exist "data" mkdir "data"
if not exist "logs" mkdir "logs"
echo 正在启动方笺系统，首次启动约需 20-40 秒，请稍候...
start "" "runtime\bin\javaw.exe" -Xms256m -Xmx1024m -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai -jar "app\fangjian-clinic.jar" --spring.profiles.active=local
for /l %%i in (1,1,120) do (
  curl -s -o nul http://127.0.0.1:8080/api/insurance/providers
  if not errorlevel 1 goto ready
  timeout /t 1 /nobreak >nul
)
echo.
echo [警告] 启动超时。请查看 logs\fangjian-clinic.log 了解原因。
pause
exit /b 1
:ready
start "" http://127.0.0.1:8080
echo 已启动：http://127.0.0.1:8080
echo 关闭本窗口不会停止系统；需要停止请运行「停止方笺系统.bat」。
timeout /t 4 >nul
exit /b 0
'@

Write-Bat (Join-Path $appDir '停止方笺系统.bat') @'
@echo off
chcp 936 >nul
echo 正在停止方笺系统...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$c = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1; if ($c) { Stop-Process -Id $c.OwningProcess -Force; Write-Host '已停止' -ForegroundColor Green } else { Write-Host '系统未在运行' -ForegroundColor Yellow }"
timeout /t 3 >nul
'@

Write-Bat (Join-Path $appDir '打开方笺系统.bat') @'
@echo off
chcp 936 >nul
start "" http://127.0.0.1:8080
'@

Write-Config (Join-Path $appDir 'config\application.properties') @'
# 方笺系统 — 本地配置（可直接用记事本修改，改完重启「启动方笺系统.bat」生效）
# 端口被占用时改这里，例如改成 8081
server.port=8080

# 日志级别：INFO 正常，DEBUG 排查问题时用（日志会变大）
logging.level.com.fangjian.clinic=INFO
'@

Write-Text (Join-Path $appDir '使用说明.txt') @'
方笺 —— 绿色便携版使用说明
================================

一、启动
  双击「启动方笺系统.bat」，等待约 20-40 秒，浏览器会自动打开
  http://127.0.0.1:8080

  首次启动会自动建库并初始化演示数据：
    管理员 admin / 123456
    医生   doctor01 / 123456、doctor02、doctor03
    收费员 cashier01 / 123456
    药师   pharmacist01 / 123456

  ★ 正式使用前务必改掉这些默认密码（登录后到「员工管理」里改）。

二、停止
  双击「停止方笺系统.bat」。
  直接关掉浏览器只是关闭页面，后台服务仍在运行。

三、数据在哪（最重要）
  data\      数据库文件（fangjian.mv.db），所有患者、病历、收费数据都在这里
  backup\    每次启动自动备份一份（保留最近 7 份）
  logs\      运行日志

  迁移到新电脑：把整个「方笺系统」文件夹拷走即可，数据跟着走。
  建议放在非系统盘（如 D:\方笺系统），并定期把 backup\ 拷到 U 盘或另一台电脑。
  ★ 本系统不联网、数据不出店，但也意味着没有云端兜底——备份必须你自己做。

四、常见问题
  1) 提示端口被占用 / 页面打不开
     用记事本打开 config\application.properties，把 server.port 改成 8081，
     保存后重新启动，浏览器访问 http://127.0.0.1:8081
  2) 启动超时
     打开 logs\fangjian-clinic.log 查看最后几十行错误信息
  3) 杀毒软件报警
     未签名程序被误报属常见现象；确认来源可信后加入信任列表
  4) 换电脑
     拷贝整个文件夹；如果原来装在 C 盘，建议新位置不要放 C 盘

五、病历书写与用药安全（v4）
  中医/西医病历分开，必填项带红星；主诉/现病史/舌脉/证候等支持联想（关键字或拼音）；
  保存前自动质控，缺必填项会被拦截；现病史/治则/医嘱/体格检查旁有「AI 助写」
  （默认本地模板不联网，可在系统设置接内网模型，AI 只出草稿需人工确认）。
  开方自动审查十八反/十九畏/妊娠禁忌/毒性药/剂量范围；附子先煎、薄荷后下、
  旋覆花包煎、人参另煎、阿胶烊化、三七冲服等特殊煎法自动写入用法。

六、当前版本限制（务必知悉）
  - 小程序患者端未包含在本包内（需要单独用微信开发者工具部署）
  - 医保接口为扩展点，未接入任何地区真实报文
  - 患者端短信验证码仍为固定 123456（本包未含小程序，不影响）
  - 备份为整库 zip，恢复需联系实施人员按文档操作

六、版本
  方笺 v0.1.0（本地部署版）
'@

# 8. 汇总
$jarSize = [math]::Round((Get-Item (Join-Path $appDir "app\$jarName")).Length / 1MB, 1)
$total = [math]::Round((Get-ChildItem $appDir -Recurse -File | Measure-Object Length -Sum).Sum / 1MB, 1)
Write-Host "[build] 完成：$appDir" -ForegroundColor Green
Write-Host "        jar ${jarSize}MB，整个包 ${total}MB" -ForegroundColor Green

if ($Zip) {
    $zipPath = Join-Path $releaseRoot '方笺系统-v0.1.0-便携版.zip'
    if (Test-Path $zipPath) { Remove-Item $zipPath -Force }
    Write-Host "[build] 打包 zip（可能需要 1-2 分钟）..." -ForegroundColor Cyan
    Compress-Archive -Path $appDir -DestinationPath $zipPath -CompressionLevel Optimal
    Write-Host "[build] zip：$zipPath（$([math]::Round((Get-Item $zipPath).Length/1MB,1))MB）" -ForegroundColor Green
}
