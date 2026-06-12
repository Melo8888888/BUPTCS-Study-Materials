# 智能充电桩调度计费系统

> Spring Boot 3.3.5 + Java 22 + Three.js 0.164.1 实现的智能充电站调度计费系统。
> 覆盖设计文档 24 条指令、跨段计费、故障两策略、超时停车费、JWT 鉴权、3D 实时调度可视化、院线版 3D（GLB + Bloom）。

| 指标 | 状态 |
|---|---|
| JUnit 测试 | **5 / 5 ✓** |
| 28 项 acceptance 断言 | **28 / 28 ✓** |
| 24 条指令显式实现 | **22 / 24 ✓** + 2 简化 / 隐式 |
| 默认验收 `totalFee` | **607.75** ✓（精确匹配题目期望） |

---

## 1. 复现步骤（zip → 可跑）

> 适用于 `se-final-project-FINAL-*.zip` 解压后从零开始

```bash
# 1. 解压
unzip se-final-project-FINAL-*.zip
cd se-final-project

# 2. 准备 Java 22（必需）
java -version  # 若不是 22，可下载 https://download.oracle.com/java/22/archive/jdk-22.0.2_linux-x64_bin.tar.gz

# 3. 用 Maven Wrapper 构建后端（首次 ~2 分钟下载依赖）
cd backend
chmod +x mvnw
./mvnw package -DskipTests        # 跳过测试更快；如要跑 5 个 JUnit：./mvnw test

# 4. 启动（默认 H2 内存数据库；端口 8080；CWD 不挑剔，B0 已修）
java -jar target/ev-charge-backend-0.1.0.jar
# 或绝对路径 + 自定义 frontend 位置：
# java -jar target/ev-charge-backend-0.1.0.jar --evcharge.frontend.path=/abs/path/to/frontend

# 5. 浏览器
open http://localhost:8080/0_login.html
```

**注意事项：**
- `7_cinema_3d.html`（院线 3D 视图）需要外网（threejs.org / jsdelivr / github raw 三选一 CDN）；若内网部署，给 URL 加 `?nocdn=1` 强制走 primitive 兜底
- 其他 6 个页面（0~6）+ 1 个 e2e 自检页本地资源即可

---

## 2. 启动 3 种模式

```bash
# A) 完整鉴权（生产推荐）
java -jar backend/target/ev-charge-backend-0.1.0.jar

# B) 验收脚本兼容：一键关 auth（PowerShell 验收脚本 acceptance-check.ps1 用）
EVCHARGE_AUTH_ENABLED=false java -jar backend/target/ev-charge-backend-0.1.0.jar

# C) 切到 MySQL（profile=mysql；env 给账号密码）
MYSQL_USER=evcharge MYSQL_PASSWORD=meiyoumima \
  java -jar backend/target/ev-charge-backend-0.1.0.jar \
       -Dspring-boot.run.profiles=mysql
```

---

## 3. 24 条指令对照表

> 与设计文档（概要设计 v3 第 3 章 + 题目 PDF 第 5 节）逐条对照

| # | 指令 | HTTP 端点 / 入口 | 状态 |
|--:|---|---|---|
| 1 | Request_Recharge | `POST /api/vehicles/{id}/requests` | ✅ |
| 2 | Request_Queue | `GET /api/vehicles/{id}/status` | ✅ |
| 3 | Start_Recharge | 内部：`schedule() → assignToPile()` | ⚠️ 隐式（自动触发） |
| 4 | Change_RequestMode | `POST /api/vehicles/{id}/requests/change`（充电中拒绝 400） | ✅ |
| 5 | Cancel_Recharge | `POST /api/vehicles/{id}/requests/cancel` | ✅ |
| 6 | End_Recharge | `POST /api/vehicles/{id}/charging/end` | ✅ |
| 7 | Pay | `POST /api/vehicles/{id}/bills/pay` | ✅ |
| 8 | Request_Bill | `GET /api/vehicles/{id}/bills` + `/bill-aggregate?scope=...` | ✅ |
| 9 | Register | `POST /api/auth/register` | ✅ |
| 10 | Login | `POST /api/auth/login` · `GET /api/auth/whoami` | ✅ |
| 11 | Start_ChargeStation | `POST /api/admin/station/start` | ✅ |
| 12 | Stop_ChargeStation | `POST /api/admin/station/stop` | ✅ |
| 13 | Set_Parameters | `POST /api/admin/config`（含改桩数 + 停车费率） | ✅ |
| 14 | Schedule_Request | `POST /api/admin/schedule` | ✅ |
| 15 | Call_Number | `POST /api/admin/piles/{id}/call-number` | ✅ |
| 16 | GetQueue | `GET /api/admin/snapshot` · `/piles/{id}/queue-details` · `/waiting-area/details` | ✅ |
| 17 | Check_ChargingPile | `GET /api/admin/snapshot` · `/reports/summary` | ✅ |
| 18 | Check_ChargingPileQueue | `GET /api/admin/piles/{id}/queue-details`（含电池容量 + 排队时长 4 字段） | ✅ |
| 19 | Create_Bill | `GET /api/vehicles/{id}/bill-aggregate?scope=DAY\|WEEK\|MONTH\|ALL` | ✅（按需聚合，未独立持久化为 Bill 表） |
| 20 | Create_DetailList | `BillingService.createDetail()` 在 `finishCharging()` 自动触发 | ✅ |
| 21 | Create_ParkingFee | `POST /api/admin/parking-fee/scan` · `/vehicles/{id}/parking-fees` · `/parking-fee/pay/{feeId}` | ✅ |
| 22 | Create_Report | `GET /api/admin/reports/summary` · `/reports/details` | ✅ |
| 23 | Report_Fault | `POST /api/admin/piles/{id}/fault?minutes=&strategy=PRIORITY\|TIME_ORDER` | ✅ |
| 24 | Recover_Fault | `POST /api/admin/piles/{id}/recover?strategy=PRIORITY\|TIME_ORDER`（默认 TIME_ORDER，满足 PDF 7-c） | ✅ |

---

## 4. 文件结构

```
se-final-project/
├── README.md                          ← 本文件（项目主入口）
├── backend/
│   ├── pom.xml                        Maven 配置（Spring Boot 3.3.5 + JJWT 0.12.6）
│   ├── mvnw / mvnw.cmd                Maven Wrapper（首次自动下载 maven）
│   └── src/
│       ├── main/
│       │   ├── java/com/evcharge/
│       │   │   ├── EvChargeBackendApplication.java
│       │   │   ├── config/            AuthInterceptor / AuthWebMvcConfig / WebSocketConfig
│       │   │   │                       FrontendResourceConfig（B0 多策略路径） · CorsConfig · ApiExceptionHandler
│       │   │   ├── controller/        Auth / Admin / Vehicle / Charging
│       │   │   │                       Acceptance（验收回放白名单） · Health
│       │   │   │                       StationOverview（任意登录可读，B1 修复） · StationWebSocketController
│       │   │   ├── service/           StationService（核心 1100+ 行）
│       │   │   │                       BillingService（跨段计费）· AuthService（JWT + PBKDF2）
│       │   │   │                       ParkingFeeService（超时停车）· WebSocketStationEventPublisher
│       │   │   ├── domain/            ChargeMode / ChargingPile / ChargingRequest / ChargingDetail
│       │   │   │                       FaultEvent / FaultStrategy（PRIORITY|TIME_ORDER）
│       │   │   │                       PileStatus / RequestStatus / Role / StationState
│       │   │   ├── dto/               18 个 record（ApiResponse / AuthDto / BillAggregateDto
│       │   │   │                       QueueDetailDto / ParkingFeeDto / SystemSnapshot ... ）
│       │   │   └── persistence/       JPA repo + record + JpaStationStateStore
│       │   │                          PersistenceReportService · UserRecord / UserRepository
│       │   └── resources/
│       │       ├── application.yml    H2 默认 + MySQL profile + JWT + 停车费配置
│       │       └── schema-mysql.sql   MySQL 建表（含 users / parking_fees）
│       └── test/
│           └── java/com/evcharge/     StationServiceTest · ApiIntegrationTest（5 测试）
├── frontend/                          静态 HTML+JS（Spring Boot 直接 serve）
│   ├── 0_login.html                   登录 / 注册（用户名+密码+角色+车号+手机）
│   ├── 1_charge_request.html          申请充电（含电池总容量字段）
│   ├── 2_queue_status.html            队列状态 / 修改 / 取消 / 结束
│   ├── 3_my_bills.html                账单 + 详单 + 超时停车费支付
│   ├── 4_admin_piles.html             桩看板 + 故障策略下拉 + 院线 3D 入口
│   ├── 5_admin_queue.html             调度沙盘 + 时间轴回放 + 3D（基础版）
│   ├── 6_admin_report.html            报表 + 停车费扫描入口 + CSV 导出
│   ├── 7_cinema_3d.html              ★ 院线版实时 3D（GLB + Bloom + WS）
│   ├── e2e_acceptance_runner.html     9 步浏览器自检
│   ├── common-auth.js                 统一 fetch + JWT token 注入 + 401 自动跳转
│   ├── common.css                     全局样式
│   └── 5.html                         5_admin_queue 全屏 iframe 包装
├── docs/
│   ├── 题目要求理解.md                题目 PDF + 概要设计 + Excel 三份文档结构化解读
│   ├── 验收.md                        功能 / 端点 / 操作 / 截图详细手册
│   ├── 概要设计v3.md                  小组提交版概要设计
│   ├── 联合验收操作手册.md            PowerShell 验收脚本说明
│   ├── 智能充电桩调度计费系统详细需求 参考答案 20260508.pdf
│   ├── 概要设计v3.pdf
│   ├── 第一次作业G6组.pdf
│   ├── 作业验收用例（包含参数说明）.xlsx
│   └── screenshots/                   ~13 张 playwright 截图
├── scripts/
│   ├── acceptance-check.ps1           PowerShell 28 项断言
│   └── reset-mysql-password-admin.ps1
└── data/                              本地 H2 文件数据库（运行时生成，不入 zip）
```

---

## 5. 核心架构图

```
┌──────────────────────────────────────────────────────────────────┐
│   浏览器（角色 USER 或 ADMIN）                                       │
│   ┌──────────────────┐   ┌────────────────┐  ┌─────────────────┐  │
│   │  0/1/2/3 用户页   │   │ 4/5/6 管理员页 │  │ 7_cinema_3d.html │  │
│   │  鉴权 JWT          │   │  鉴权 JWT       │  │  GLB + Bloom + WS│  │
│   └─────────┬────────┘   └────────┬───────┘  └────────┬─────────┘  │
│             │  REST/JSON              │ REST/WS           │ REST/WS  │
└─────────────┼─────────────────────────┼───────────────────┼──────────┘
              ▼                         ▼                   ▼
┌──────────────────────────────────────────────────────────────────┐
│   Spring Boot 8080                                                 │
│   ┌─────────────────────────────────────────────────────────┐     │
│   │  AuthInterceptor （白名单 / ADMIN / vid-match）          │     │
│   └────────────┬────────────────────────────────────────────┘     │
│   ┌────────────┴────────────────────────────────────────────┐     │
│   │ Controllers: Auth / Vehicle / Admin / Charging /         │     │
│   │              StationOverview / Acceptance / Health       │     │
│   └────────────┬────────────────────────────────────────────┘     │
│   ┌────────────┴────────────────────────────────────────────┐     │
│   │ Services: StationService (1100 行) · BillingService ·    │     │
│   │           AuthService (JWT) · ParkingFeeService          │     │
│   └────────────┬────────────────────────────────────────────┘     │
│   ┌────────────┴────────────────────────────────────────────┐     │
│   │ Persistence: JpaStationStateStore  + 7 个 Repository     │     │
│   │   ─── deleteAllInBatch + saveAll 整体一致性写            │     │
│   └────────────┬────────────────────────────────────────────┘     │
└────────────────┼─────────────────────────────────────────────────┘
                 ▼
            H2 (文件) / MySQL 8.0
```

---

## 6. 测试与验收

### 6.1 JUnit（自动）

```bash
cd backend && ./mvnw test
# [INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
```

### 6.2 PowerShell 验收脚本（28 项断言）

```powershell
# Windows / PowerShell
cd se-final-project
.\scripts\acceptance-check.ps1
# == Acceptance check passed ==
```

关键期望值（验收手册硬性要求）：

```
station_state    = 1
charging_piles   = 5
charging_requests= 22
charging_details = 13   ← 故障 + 取消 + 模式变更使详单数 > 完成车辆数
fault_events     = 2
totalFee         = 607.75
V21.totalFee     = 16.50（跨段：09:50-10:00 平 + 10:00-10:10 峰）
```

### 6.3 浏览器自检（9 步）

```
http://localhost:8080/e2e_acceptance_runner.html?autorun=1
```

---

## 7. 已知偏离与限制

| 项 | 说明 |
|---|---|
| Bill 独立持久化为表 | 已用 `bill-aggregate` 端点按需聚合（period filter）；未单独建 bills 表 |
| WebSocket 鉴权 | 仅 token-less 广播（演示场景） |
| 充电中改请求 HTTP | 严格 400 拒绝（满足 PDF 字面）；验收 C 事件链路内部自动等价 cancel + submit |
| 报表 scope=DAY 在 reset 后立即看 0 | 正常（reset 清空 details） |
| 院线 3D 需外网 CDN | 三资产分别 jsdelivr / GitHub raw 双源 + primitive 兜底；`?nocdn=1` 强制全 primitive |

---

## 8. 进一步阅读

- 详细操作手册（含 13+ 张截图）→ [docs/验收.md](docs/验收.md)
- 题目要求逐条解读 → [docs/题目要求理解.md](docs/题目要求理解.md)
- 小组提交版概要设计 → [docs/概要设计v3.md](docs/概要设计v3.md)
- 联合验收 PowerShell 脚本说明 → [docs/联合验收操作手册.md](docs/联合验收操作手册.md)
- 原题 PDF → [docs/智能充电桩调度计费系统详细需求 参考答案 20260508.pdf](docs/智能充电桩调度计费系统详细需求%20参考答案%2020260508.pdf)

---

## 9. 实施历程（变更记录）

按四个交付批次落地：

| 批次 | 内容 | 文件覆盖 |
|---|---|---|
| 第一轮 | 4 切片：Auth + ParkingFee + 改桩数 + 故障策略 | backend 新 13 文件 + frontend 7 文件改 |
| 第二轮 | 4 个 gap：电池容量 + 排队时长 / 整站启停 / 显式 Schedule+CallNumber / 故障恢复重调度 | StationService + QueueDetailDto + Frontend 1 个输入 |
| 第三轮 | B0 frontend 绝对路径 + B1 用户端 403 修复（StationOverviewController） + B2 3D 桩位动态 + e2e_runner 接通新登录 + G5 Bill 聚合 + G6 CHARGING 拒改 + 3D 模型材质升级 | 后端 1 新文件 + StationService + 前端 5 文件 |
| 第四轮 | 院线 3D（7_cinema_3d.html，全 GLB + Bloom + WS） + 4 号页入口 + 本 README | 前端 1 新文件 + 4_admin_piles 1 行入口 + 根 README |

详细 commit-level 变更见 docs/验收.md "实施历程" 段。
