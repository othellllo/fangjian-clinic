param(
    [Parameter(Mandatory = $true)][string]$File,
    [string]$DbHost = '127.0.0.1',
    [int]$DbPort = 3306,
    [string]$Database = 'fangjian_clinic',
    [string]$User = 'fangjian',
    [string]$Password = $env:DB_PASSWORD,
    [string]$Container = 'fangjian-mysql',
    [switch]$Force
)

# 方笺 — MySQL 恢复脚本
# 用法：
#   powershell -File scripts\restore-mysql.ps1 -File backups\fangjian_clinic-20261118-213000.sql.gz -Password yourpass
#
# 注意：恢复会覆盖同名库中的现有数据，默认需要交互确认，-Force 可跳过。

$ErrorActionPreference = 'Stop'
if (-not (Test-Path $File)) { throw "备份文件不存在：$File" }
$File = (Resolve-Path $File).Path

if (-not $Force) {
    Write-Host "即将把 $File 恢复到 $DbHost/$Database，现有数据会被覆盖！" -ForegroundColor Yellow
    $answer = Read-Host "输入 RESTORE 确认继续"
    if ($answer -ne 'RESTORE') { Write-Host "已取消"; exit 1 }
}

$tempSql = Join-Path ([System.IO.Path]::GetTempPath()) ("restore-" + [guid]::NewGuid().ToString('N') + '.sql')

# 解压
if ($File.EndsWith('.gz')) {
    $input = [System.IO.File]::OpenRead($File)
    try {
        $gzip = New-Object System.IO.Compression.GZipStream($input, [System.IO.Compression.CompressionMode]::Decompress)
        try {
            $output = [System.IO.File]::Create($tempSql)
            try { $gzip.CopyTo($output) } finally { $output.Dispose() }
        } finally { $gzip.Dispose() }
    } finally { $input.Dispose() }
} else {
    Copy-Item $File $tempSql -Force
}

try {
    $mysql = (Get-Command mysql -ErrorAction SilentlyContinue)
    if ($mysql) {
        Write-Host "[restore] 使用本机 mysql 客户端导入 ..." -ForegroundColor Cyan
        $mysqlArgs = @("--host=$DbHost", "--port=$DbPort", "--user=$User", '--default-character-set=utf8mb4')
        if ($Password) { $mysqlArgs = @("--password=$Password") + $mysqlArgs }
        # 使用 cmd 重定向，避免 PowerShell 管道破坏二进制/编码
        $cmd = "`"$($mysql.Source)`" $($mysqlArgs -join ' ') $Database < `"$tempSql`""
        & cmd /c $cmd
        if ($LASTEXITCODE -ne 0) { throw "mysql 导入失败，退出码 $LASTEXITCODE" }
    } else {
        $docker = (Get-Command docker -ErrorAction SilentlyContinue)
        if (-not $docker) { throw "未找到 mysql 客户端，也未找到 docker" }
        Write-Host "[restore] 使用 docker exec $Container 导入 ..." -ForegroundColor Cyan
        $dockerArgs = @('exec', '-i', $Container, 'mysql', "--user=$User", '--default-character-set=utf8mb4', $Database)
        if ($Password) { $dockerArgs = @('exec', '-i', $Container, 'mysql', "--user=$User", "--password=$Password", '--default-character-set=utf8mb4', $Database) }
        & cmd /c "`"$($docker.Source)`" $($dockerArgs -join ' ') < `"$tempSql`""
        if ($LASTEXITCODE -ne 0) { throw "docker exec mysql 导入失败，退出码 $LASTEXITCODE" }
    }
    Write-Host "[restore] 恢复完成：$Database" -ForegroundColor Green
} finally {
    Remove-Item $tempSql -Force -ErrorAction SilentlyContinue
}
