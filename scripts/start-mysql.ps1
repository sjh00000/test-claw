$mysqlBase = Join-Path $env:ProgramFiles "MySQL\MySQL Server 8.4"
$dataDir = Join-Path $env:LOCALAPPDATA "KeyframeVideoStudio\mysql-data"
$mysqld = Join-Path $mysqlBase "bin\mysqld.exe"
$mysqladmin = Join-Path $mysqlBase "bin\mysqladmin.exe"

if (!(Test-Path $mysqld)) {
    Write-Error "MySQL server not found: $mysqld"
    exit 1
}

if (!(Test-Path $dataDir)) {
    Write-Error "MySQL data directory not found: $dataDir"
    exit 1
}

$listener = Get-NetTCPConnection -LocalPort 3306 -State Listen -ErrorAction SilentlyContinue
if (!$listener) {
    Start-Process -FilePath $mysqld -ArgumentList @(
        "--datadir=$dataDir",
        "--port=3306",
        "--bind-address=127.0.0.1",
        "--mysqlx=0"
    ) -WindowStyle Hidden
    Start-Sleep -Seconds 8
}

& $mysqladmin -uroot --protocol=tcp --host=127.0.0.1 --port=3306 ping
Get-NetTCPConnection -LocalPort 3306 -State Listen -ErrorAction SilentlyContinue |
    Select-Object LocalAddress, LocalPort, OwningProcess, State
