param(
    [string]$ServiceName = "MySQL93",
    [string]$DefaultsFile = "C:\ProgramData\MySQL\MySQL Server 9.3\my.ini",
    [string]$MySqlBin = "C:\Program Files\MySQL\MySQL Server 9.3\bin",
    [string]$RootPassword = "meiyoumima",
    [string]$AppUser = "evcharge",
    [string]$AppPassword = "meiyoumima",
    [string]$Database = "ev_charge"
)

$ErrorActionPreference = "Stop"

function Invoke-Native {
    param(
        [string]$FilePath,
        [string[]]$Arguments
    )
    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code $LASTEXITCODE`: $FilePath $($Arguments -join ' ')"
    }
}

function Invoke-MySql {
    param(
        [string]$User,
        [string]$Password,
        [string]$Sql,
        [switch]$Quiet
    )
    $previous = $env:MYSQL_PWD
    $outFile = Join-Path $env:TEMP "evcharge-mysql-client.out.log"
    $errFile = Join-Path $env:TEMP "evcharge-mysql-client.err.log"
    $previousErrorAction = $ErrorActionPreference
    try {
        $env:MYSQL_PWD = $Password
        $ErrorActionPreference = "Continue"
        & $mysql "-u$User" -e $Sql > $outFile 2> $errFile
        $exitCode = $LASTEXITCODE
        $stdout = if (Test-Path -LiteralPath $outFile) { Get-Content -LiteralPath $outFile -Raw } else { "" }
        $stderr = if (Test-Path -LiteralPath $errFile) { Get-Content -LiteralPath $errFile -Raw } else { "" }
        if ($exitCode -ne 0) {
            if (-not $Quiet) {
                if ($stdout) { Write-Host $stdout }
                if ($stderr) { Write-Host $stderr }
            }
            throw "mysql command failed for user $User with exit code $exitCode."
        }
        if (-not $Quiet -and $stdout) {
            Write-Host $stdout
        }
    } finally {
        if ($null -eq $previous) {
            Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
        } else {
            $env:MYSQL_PWD = $previous
        }
        $ErrorActionPreference = $previousErrorAction
        Remove-Item -LiteralPath $outFile -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $errFile -Force -ErrorAction SilentlyContinue
    }
}

function Test-MySqlLogin {
    param(
        [string]$User,
        [string]$Password
    )
    try {
        Invoke-MySql -User $User -Password $Password -Sql "SELECT 1;" -Quiet
        return $true
    } catch {
        return $false
    }
}

function Assert-Admin {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = [Security.Principal.WindowsPrincipal]::new($identity)
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw "Run PowerShell as Administrator, then execute this script again."
    }
}

function Wait-ServiceStatus($Name, $Status, $TimeoutSeconds = 30) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $svc = Get-Service -Name $Name
        if ($svc.Status -eq $Status) {
            return
        }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for service $Name to become $Status."
}

Assert-Admin

$mysqld = Join-Path $MySqlBin "mysqld.exe"
$mysql = Join-Path $MySqlBin "mysql.exe"
if (-not (Test-Path -LiteralPath $mysqld)) { throw "mysqld.exe not found: $mysqld" }
if (-not (Test-Path -LiteralPath $mysql)) { throw "mysql.exe not found: $mysql" }
if (-not (Test-Path -LiteralPath $DefaultsFile)) { throw "MySQL defaults file not found: $DefaultsFile" }

$initFile = Join-Path $env:TEMP "evcharge-mysql-init.sql"
$tempOut = Join-Path $env:TEMP "evcharge-mysql-reset.out.log"
$tempErr = Join-Path $env:TEMP "evcharge-mysql-reset.err.log"
@"
CREATE USER IF NOT EXISTS 'root'@'localhost' IDENTIFIED BY '$RootPassword';
ALTER USER 'root'@'localhost' IDENTIFIED BY '$RootPassword' ACCOUNT UNLOCK;
CREATE USER IF NOT EXISTS '$AppUser'@'localhost' IDENTIFIED BY '$AppPassword';
ALTER USER '$AppUser'@'localhost' IDENTIFIED BY '$AppPassword' ACCOUNT UNLOCK;
CREATE DATABASE IF NOT EXISTS $Database CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON $Database.* TO '$AppUser'@'localhost';
FLUSH PRIVILEGES;
"@ | Set-Content -LiteralPath $initFile -Encoding ASCII

Write-Host "Stopping $ServiceName ..."
Stop-Service -Name $ServiceName -Force
Wait-ServiceStatus $ServiceName "Stopped"

Write-Host "Cleaning up remaining mysqld processes ..."
Get-Process mysqld -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 3

Write-Host "Starting temporary MySQL with init-file ..."
$args = @("--defaults-file=`"$DefaultsFile`"", "--init-file=`"$initFile`"", "--console")
$process = Start-Process -FilePath $mysqld -ArgumentList $args -WindowStyle Hidden -RedirectStandardOutput $tempOut -RedirectStandardError $tempErr -PassThru
try {
    $deadline = (Get-Date).AddSeconds(45)
    $rootOk = $false
    do {
        Start-Sleep -Seconds 2
        if (Test-MySqlLogin -User "root" -Password $RootPassword) {
            $rootOk = $true
            break
        }
    } while ((Get-Date) -lt $deadline -and -not $process.HasExited)

    if (-not $rootOk) {
        Write-Host "Temporary MySQL stdout:"
        if (Test-Path -LiteralPath $tempOut) { Get-Content -LiteralPath $tempOut -Tail 80 }
        Write-Host "Temporary MySQL stderr:"
        if (Test-Path -LiteralPath $tempErr) { Get-Content -LiteralPath $tempErr -Tail 80 }
        throw "Temporary MySQL did not accept the new root password."
    }

    Write-Host "Verifying root and app user ..."
    Invoke-MySql -User "root" -Password $RootPassword -Sql "SELECT USER() AS user_name, VERSION() AS mysql_version;"
    Invoke-MySql -User $AppUser -Password $AppPassword -Sql "SELECT USER() AS user_name; SHOW DATABASES LIKE '$Database';"
} finally {
    if ($process -and -not $process.HasExited) {
        Stop-Process -Id $process.Id -Force
        Start-Sleep -Seconds 3
    }
    Remove-Item -LiteralPath $initFile -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $tempOut -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $tempErr -Force -ErrorAction SilentlyContinue
}

Write-Host "Cleaning up temporary mysqld processes ..."
Get-Process mysqld -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 3

Write-Host "Starting $ServiceName ..."
Start-Service -Name $ServiceName
Wait-ServiceStatus $ServiceName "Running"

Write-Host "Verifying service login ..."
Invoke-MySql -User "root" -Password $RootPassword -Sql "SELECT USER() AS user_name;"
Invoke-MySql -User $AppUser -Password $AppPassword -Sql "SHOW DATABASES LIKE '$Database';"

Write-Host "MySQL password reset complete."
Write-Host "root password: $RootPassword"
Write-Host "app user: $AppUser"
Write-Host "app password: $AppPassword"
Write-Host "database: $Database"
