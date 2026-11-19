# 一键启动开发环境（后端 + 前端）
# 用法：powershell -ExecutionPolicy Bypass -File scripts\start-dev.ps1
# 说明：优先使用 JAVA_HOME / PATH 中的 JDK 与 Maven，找不到时回退到本机常见位置。

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

# 1. 定位 JDK 21
$jdk = $env:JAVA_HOME
if (-not $jdk -or -not (Test-Path (Join-Path $jdk 'bin\java.exe'))) {
    $candidates = @()
    if (Test-Path "$env:USERPROFILE\.jdks") {
        $candidates += Get-ChildItem "$env:USERPROFILE\.jdks" -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match 'jdk-2[1-9]' } | Sort-Object Name -Descending
    }
    if ($candidates.Count -gt 0) { $jdk = $candidates[0].FullName }
}
if (-not $jdk -or -not (Test-Path (Join-Path $jdk 'bin\java.exe'))) {
    throw "未找到 JDK 21，请安装后设置 JAVA_HOME（或把 JDK 放到 %USERPROFILE%\.jdks）"
}
$env:JAVA_HOME = $jdk
Write-Host "[start-dev] JAVA_HOME = $jdk" -ForegroundColor Cyan

# 2. 定位 Maven（优先用随盘携带的 .tools\maven）
$mvn = (Get-Command mvn.cmd -ErrorAction SilentlyContinue).Source
if (-not $mvn) {
    foreach ($base in @((Join-Path $root '.tools\maven'), (Join-Path $root '..\.tools\maven'))) {
        if (-not (Test-Path $base)) { continue }
        $direct = Join-Path $base 'bin\mvn.cmd'
        if (Test-Path $direct) { $mvn = $direct; break }
        $local = Get-ChildItem $base -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($local -and (Test-Path (Join-Path $local.FullName 'bin\mvn.cmd'))) {
            $mvn = Join-Path $local.FullName 'bin\mvn.cmd'
            break
        }
    }
}
if (-not $mvn -or -not (Test-Path $mvn)) {
    throw "未找到 Maven，请安装 Maven 3.9+ 并加入 PATH（或放到 项目目录\.tools\maven 下）"
}
Write-Host "[start-dev] MAVEN = $mvn" -ForegroundColor Cyan

# 3. 启动后端（有随盘 Maven 配置则一并使用，走国内镜像更快）
$settings = Join-Path $root '.tools\maven-settings.xml'
$mvnArgLine = if (Test-Path $settings) { "-s `"$settings`" " } else { '' }
Write-Host "[start-dev] 启动后端 http://localhost:8080 ..." -ForegroundColor Green
Start-Process -FilePath 'powershell' -ArgumentList @(
    '-NoExit', '-Command',
    "`$env:JAVA_HOME='$jdk'; CD '$root\backend'; & '$mvn' $($mvnArgLine)spring-boot:run"
)

# 4. 启动前端（依赖已安装则直接 dev）
Start-Sleep -Seconds 3
if (-not (Test-Path (Join-Path $root 'frontend\node_modules'))) {
    Write-Host "[start-dev] 首次启动，安装前端依赖 ..." -ForegroundColor Yellow
    Push-Location (Join-Path $root 'frontend')
    npm install --no-audit --no-fund
    Pop-Location
}
Write-Host "[start-dev] 启动前端 http://localhost:5173 ..." -ForegroundColor Green
Start-Process -FilePath 'powershell' -ArgumentList @(
    '-NoExit', '-Command',
    "CD '$root\frontend'; npm run dev"
)

Write-Host ""
Write-Host "后端接口: http://localhost:8080/api   接口文档: http://localhost:8080/swagger-ui.html" -ForegroundColor Cyan
Write-Host "前端页面: http://localhost:5173       账号: admin / 123456" -ForegroundColor Cyan
