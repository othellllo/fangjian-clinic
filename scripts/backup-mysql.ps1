param(
    [string]$DbHost = '127.0.0.1',
    [int]$DbPort = 3306,
    [string]$Database = 'yiguan_his',
    [string]$User = 'yiguan',
    [string]$Password = $env:DB_PASSWORD,
    [string]$OutDir = '',
    [int]$KeepDays = 14,
    [string]$Container = 'yiguan-mysql'
)

# 医馆 HIS — MySQL 逻辑备份脚本
# 用法示例：
#   powershell -File scripts\backup-mysql.ps1 -Password yourpass
#   powershell -File scripts\backup-mysql.ps1 -DbHost 127.0.0.1 -OutDir D:\his-backup -KeepDays 30
#
# 说明：
#   - 优先使用本机 mysqldump；没有则尝试 docker exec 到容器里执行（docker-compose 默认容器名 yiguan-mysql）。
#   - 备份文件为 gzip 压缩的 SQL：<库名>-<yyyyMMdd-HHmmss>.sql.gz
#   - 默认保留 14 天，可用 -KeepDays 调整。

$ErrorActionPreference = 'Stop'
if (-not $OutDir) {
    $OutDir = Join-Path (Split-Path -Parent $PSScriptRoot) 'backups'
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$sqlFile = Join-Path $OutDir "$Database-$stamp.sql"
$gzFile = "$sqlFile.gz"

$dumpArgs = @(
    "--host=$DbHost", "--port=$DbPort", "--user=$User",
    '--single-transaction', '--routines', '--events', '--triggers',
    '--default-character-set=utf8mb4', '--set-gtid-purged=OFF',
    "--result-file=$sqlFile", $Database
)
if ($Password) { $dumpArgs = @("--password=$Password") + $dumpArgs }

$mysqldump = (Get-Command mysqldump -ErrorAction SilentlyContinue)
if ($mysqldump) {
    Write-Host "[backup] 使用本机 mysqldump ..." -ForegroundColor Cyan
    & $mysqldump.Source @dumpArgs
    if ($LASTEXITCODE -ne 0) { throw "mysqldump 失败，退出码 $LASTEXITCODE" }
} else {
    $docker = (Get-Command docker -ErrorAction SilentlyContinue)
    if (-not $docker) { throw "未找到 mysqldump，也未找到 docker。请安装 MySQL 客户端或使用 docker-compose 部署。" }
    Write-Host "[backup] 使用 docker exec $Container 执行 mysqldump ..." -ForegroundColor Cyan
    $dockerArgs = @('exec', $Container, 'mysqldump') + $dumpArgs
    & $docker.Source @dockerArgs
    if ($LASTEXITCODE -ne 0) { throw "docker exec mysqldump 失败，退出码 $LASTEXITCODE" }
}

# 压缩
$source = [System.IO.File]::OpenRead($sqlFile)
try {
    $target = [System.IO.File]::Create($gzFile)
    try {
        $gzip = New-Object System.IO.Compression.GZipStream($target, [System.IO.Compression.CompressionMode]::Compress)
        try { $source.CopyTo($gzip) } finally { $gzip.Dispose() }
    } finally { $target.Dispose() }
} finally { $source.Dispose() }
Remove-Item $sqlFile -Force

$size = [math]::Round((Get-Item $gzFile).Length / 1KB, 1)
Write-Host "[backup] 完成：$gzFile（$size KB）" -ForegroundColor Green

# 清理过期备份
$expired = Get-ChildItem $OutDir -Filter "$Database-*.sql.gz" |
    Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-$KeepDays) }
foreach ($file in $expired) {
    Remove-Item $file.FullName -Force
    Write-Host "[backup] 已删除过期备份：$($file.Name)" -ForegroundColor Yellow
}

Write-Host "[backup] 当前备份数：$((Get-ChildItem $OutDir -Filter "$Database-*.sql.gz").Count)" -ForegroundColor Cyan
