param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

function Invoke-ApiPost($Path, $Body = $null) {
    $params = @{
        Uri = "$BaseUrl$Path"
        Method = "POST"
    }
    if ($null -ne $Body) {
        $params.ContentType = "application/json"
        $params.Body = ($Body | ConvertTo-Json -Depth 8)
    }
    $response = Invoke-RestMethod @params
    if ($response.success -eq $false) {
        throw "POST $Path failed: $($response.message)"
    }
    return $response.data
}

function Invoke-ApiGet($Path) {
    $response = Invoke-RestMethod -Uri "$BaseUrl$Path" -Method "GET"
    if ($response.success -eq $false) {
        throw "GET $Path failed: $($response.message)"
    }
    return $response.data
}

function Assert-Equal($Name, $Actual, $Expected) {
    if ("$Actual" -ne "$Expected") {
        throw "$Name expected <$Expected>, got <$Actual>"
    }
    Write-Host "[OK] $Name = $Actual"
}

function Assert-True($Name, $Condition) {
    if (-not $Condition) {
        throw "$Name assertion failed"
    }
    Write-Host "[OK] $Name"
}

Write-Host "== EV charging acceptance check =="
Write-Host "BaseUrl: $BaseUrl"

$health = Invoke-ApiGet "/api/health"
Assert-Equal "health.status" $health.status "UP"

Invoke-ApiPost "/api/admin/clock/reset?time=06:00" | Out-Null
Invoke-ApiPost "/api/admin/config" @{ waitingCapacity = 10; pileSlotCapacity = 3 } | Out-Null
$snapshot = Invoke-ApiPost "/api/acceptance/run-default?strategy=TIME_ORDER"
$config = Invoke-ApiGet "/api/admin/config"
$report = Invoke-ApiGet "/api/admin/reports/summary"
$counts = Invoke-ApiGet "/api/admin/persistence/counts"
$v21Bills = Invoke-ApiGet "/api/vehicles/V21/bills"

Assert-Equal "snapshot.now" ([string]$snapshot.now).Substring(0, 16) "2026-05-12T10:50"
Assert-Equal "config.waitingCapacity(N)" $config.waitingCapacity 10
Assert-Equal "config.pileSlotCapacity(M)" $config.pileSlotCapacity 3
Assert-Equal "waitingFast" $snapshot.waitingFast.Count 0
Assert-Equal "waitingSlow" $snapshot.waitingSlow.Count 0
$redispatchSlow = $snapshot.requests | Where-Object { $_.mode -eq "SLOW" -and $_.status -eq "REDISPATCHING" } | ForEach-Object { $_.vehicleId }
Assert-Equal "redispatchSlow.Count" $redispatchSlow.Count 3
Assert-True "redispatchSlow keeps fault overflow vehicles" (($redispatchSlow -contains "V13") -and ($redispatchSlow -contains "V16") -and ($redispatchSlow -contains "V17"))

$f1 = $snapshot.piles | Where-Object { $_.id -eq "F1" } | Select-Object -First 1
$f2 = $snapshot.piles | Where-Object { $_.id -eq "F2" } | Select-Object -First 1
$f3 = $snapshot.piles | Where-Object { $_.id -eq "F3" } | Select-Object -First 1
$t1 = $snapshot.piles | Where-Object { $_.id -eq "T1" } | Select-Object -First 1
$t2 = $snapshot.piles | Where-Object { $_.id -eq "T2" } | Select-Object -First 1

Assert-Equal "F1.status" $f1.status "FAULT"
Assert-Equal "T1.status" $t1.status "FAULT"
Assert-Equal "F2.currentVehicle" $f2.currentVehicle "V15"
Assert-Equal "F3.currentVehicle" $f3.currentVehicle "V19"
Assert-Equal "T2.currentVehicle" $t2.currentVehicle "V12"
Assert-Equal "T2.queuedVehicles" $t2.queuedVehicles.Count 2

Assert-Equal "report.requestCount" $report.requestCount 22
Assert-Equal "report.activeRequestCount" $report.activeRequestCount 8
Assert-Equal "report.chargingCount" $report.chargingCount 3
Assert-Equal "report.pileQueueCount" $report.pileQueueCount 2
Assert-Equal "report.faultPileCount" $report.faultPileCount 2
Assert-Equal "report.detailCount" $report.detailCount 13
Assert-Equal "report.totalKwh" $report.totalKwh "405.00"
Assert-Equal "report.totalFee" $report.totalFee "607.75"

Assert-Equal "db.station_state" $counts.station_state 1
Assert-Equal "db.charging_piles" $counts.charging_piles 5
Assert-Equal "db.charging_requests" $counts.charging_requests 22
Assert-Equal "db.charging_details" $counts.charging_details 13
Assert-Equal "db.fault_events" $counts.fault_events 2

$v21Bill = $v21Bills | Select-Object -First 1
Assert-True "V21 bill exists" ($null -ne $v21Bill)
Assert-Equal "V21.totalFee" $v21Bill.totalFee "16.50"
Assert-True "V21.priceBreakdown has normal and peak windows" (($v21Bill.priceBreakdown -like "*09:50-10:00*") -and ($v21Bill.priceBreakdown -like "*10:00-10:10*"))

$payment = Invoke-ApiPost "/api/vehicles/V21/bills/pay"
$v21Status = Invoke-ApiGet "/api/vehicles/V21/status"
Assert-Equal "V21.paidAmount" $payment.paidAmount "16.50"
Assert-True "V21 bill paid" ($v21Status.bills[0].paid -eq $true)

Write-Host "== Acceptance check passed =="
