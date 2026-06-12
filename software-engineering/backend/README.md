# EV Charge Backend

Spring Boot backend for the smart charging scheduling and billing system.

## Environment

- Java 22
- Spring Boot 3.3.5
- Maven Wrapper: `mvnw.cmd`
- Default database: file-based H2 at `backend/data/ev-charge.*`
- Optional database: MySQL profile `mysql`

## Run

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Health check:

```powershell
curl.exe -s http://localhost:8080/api/health
```

PowerShell tip: when posting JSON with `curl.exe`, use `--%` so PowerShell does not strip JSON quotes:

```powershell
curl.exe --% -s -X POST http://localhost:8080/api/vehicles/V100/requests -H "Content-Type: application/json" -d "{\"mode\":\"F\",\"amountKwh\":30}"
```

## Acceptance Check

Start the backend first, then run the acceptance check from the project root:

```powershell
cd D:\Computer\Program\se-final-project
powershell.exe -ExecutionPolicy Bypass -File .\scripts\acceptance-check.ps1
```

The script will reset the station to `06:00`, run the default acceptance events, and assert the key queue, fault, bill, report, and persistence values. You can also pass another server URL when doing joint acceptance:

```powershell
powershell.exe -ExecutionPolicy Bypass -File .\scripts\acceptance-check.ps1 -BaseUrl "http://192.168.1.20:8080"
```

## MySQL Run

Create the database first:

```sql
CREATE DATABASE ev_charge CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ev_charge;
SOURCE D:/Computer/Program/se-final-project/backend/src/main/resources/schema-mysql.sql;
```

If your local MySQL account is not `root/root`, set environment variables before running. This avoids editing source files:

```powershell
$env:MYSQL_USER = "root"
$env:MYSQL_PASSWORD = "your_mysql_password"
```

Run the backend with the MySQL profile:

```powershell
cd backend
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=mysql
```

Verify that MySQL persistence is active:

```powershell
curl.exe -s http://localhost:8080/api/health
powershell.exe -ExecutionPolicy Bypass -File ..\scripts\acceptance-check.ps1
curl.exe -s http://localhost:8080/api/admin/persistence/counts
```

Expected counts after `run-default`:

```text
station_state=1
charging_piles=5
charging_requests=22
charging_details=13
fault_events=2
```

Restart the backend with the same MySQL profile and call `GET /api/admin/persistence/counts` again. If the counts stay the same, the station state, requests, details, pile states, and fault events are being restored from MySQL.

You can also verify from the MySQL CLI:

```powershell
mysql -u$env:MYSQL_USER -p$env:MYSQL_PASSWORD ev_charge -e "SELECT COUNT(*) AS requests FROM charging_requests; SELECT COUNT(*) AS details FROM charging_details; SELECT COUNT(*) AS faults FROM fault_events;"
```

Hibernate is configured with `ddl-auto: update`, so it can also create/update tables automatically. The manual schema in `src/main/resources/schema-mysql.sql` is provided for controlled setup during acceptance.

Frontend pages are served by the backend. After startup, open:

```text
http://localhost:8080/
```

## Main APIs

Vehicle side:

- `POST /api/vehicles/{vehicleId}/requests`
- `POST /api/vehicles/{vehicleId}/requests/change`
- `POST /api/vehicles/{vehicleId}/requests/cancel`
- `POST /api/vehicles/{vehicleId}/charging/end`
- `GET /api/vehicles/{vehicleId}/status`
- `GET /api/vehicles/{vehicleId}/bills`
- `POST /api/vehicles/{vehicleId}/bills/pay`

Compatibility charge APIs:

- `POST /api/charge/request`
- `POST /api/charge/change`
- `POST /api/charge/cancel/{vehicleId}`
- `POST /api/charge/end/{vehicleId}`
- `GET /api/charge/bills/{vehicleId}`
- `POST /api/charge/pay/{vehicleId}`

Admin side:

- `GET /api/admin/snapshot`
- `GET /api/admin/reports/summary`
- `GET /api/admin/persistence/counts`
- `POST /api/admin/clock/reset?time=06:00`
- `POST /api/admin/clock/advance?minutes=10`
- `POST /api/admin/piles/{pileId}/fault?minutes=60`
- `POST /api/admin/piles/{pileId}/recover`

Acceptance:

- `POST /api/acceptance/event`
- `POST /api/acceptance/run-default`

WebSocket:

- STOMP endpoint: `ws://localhost:8080/ws`
- Subscribe: `/topic/station`
- Request snapshot: send to `/app/station/snapshot`

Every state-changing operation publishes a realtime message to `/topic/station`.
