# 渔业配额核对工作台（虚构演示系统）

> ⚠️ **重要声明**：本系统仅用于演示。所有船舶、物种（月鳞银鳕、红鳍石鲷、雾隐鳕）、
> 海区、季节与许可规则均为**虚构**；系统**不连接任何监管系统**，也**不构成真实捕捞许可**。

渔业合作组织按 **船舶 × 物种 × 海区 × 季节** 核对捕捞配额：

- **React 工作台**（Vite）：余额总览与筛选、余额→航次/调拨来源追溯、航次申报、
  靠港实捕录入、配额调拨登记。
- **Spring Boot 服务**：配额账本规则引擎（申报/卸货/调拨/追溯），重量一律使用
  `BigDecimal`（`NUMERIC(14,3)`，kg）。
- **PostgreSQL 配额账本**：`quota_balance` 余额表 + `ledger_entry` **只追加**流水表，
  任何余额变化都必须落一条带单据号的流水。

## 核心规则

| 规则 | 实现 |
| --- | --- |
| 每个 船舶+物种+海区+季节 组合一个余额 | `quota_balance` 唯一约束 |
| 航次申报只记录**预计用量**，不扣余额 | `voyage` / `voyage_item`；界面展示「预测占用/预测可用」 |
| 靠港后按**实捕重量**扣减余额 | `landing` + `landing_item` → `LANDING` 流水 + 余额扣减 |
| 预计与实际差额清楚显示 | 航次视图每行：预计 / 已实捕 / 差额（预计−实捕） |
| 调拨记录来源、去向、生效期间，配对扣补 | `quota_transfer` + `TRANSFER_OUT`/`TRANSFER_IN` 两条流水，绝不单边改余额 |
| 物种/海区/季节不匹配的额度不能使用 | 卸货条目必须落在申报维度内；调拨双方必须持有同维度账户，否则拒绝 |
| 重复录入同一卸货凭证不能重复扣减 | `landing.certificate_no` 唯一约束 + 前置检查 → HTTP 409 `DUPLICATE` |
| 额度不足不能卸货 | 余额校验 → HTTP 409 `QUOTA_EXCEEDED`，余额与流水不变 |
| 从余额查看航次与调拨 | `GET /api/balances/{id}/trace` → 流水 + 关联航次 + 关联调拨 |

## 目录结构

```
backend/    Spring Boot 3.3（Java 17，JPA，BigDecimal 重量）
frontend/   React 18 + Vite 工作台
scripts/    demo-scenario.sh 端到端演示脚本
docker-compose.yml  PostgreSQL 16
```

## 运行

### 1) 数据库（PostgreSQL）

```bash
docker compose up -d postgres          # 或自建 PostgreSQL，按 backend/src/main/resources/application.yml 配置
```

### 2) 后端

```bash
cd backend
# 生产形态（PostgreSQL，默认 profile）
mvn spring-boot:run

# 无 PostgreSQL 的快速体验（H2 内存库，仅演示）
mvn spring-boot:run -Dspring-boot.run.profiles=h2

# 体验 + 自动写入完整演示场景（航次/卸货/调拨/违规拒绝）
mvn spring-boot:run -Dspring-boot.run.profiles=h2,demo
```

服务默认 `http://localhost:8080`，健康检查 `GET /api/health`。

### 3) 前端

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173，/api 代理到 8080
```

### 4) 测试与演示

```bash
cd backend && mvn test                    # 8 个集成测试（H2 PostgreSQL 模式）
./scripts/demo-scenario.sh                # 对运行中的后端执行端到端演示（需全新库）
```

## 演示场景（JUnit + 演示脚本 + demo profile 三重验证）

1. **预计 > 实捕**：信天翁号申报 MOK 100 kg，实捕 80 kg → 差额 20 kg 显示，
   余额只按 80 kg 扣减（`estimatedGreaterThanActual_…`）。
2. **同船两次卸货**：蓝枪鱼号一张申报对应两张卸货凭证 180 + 100 kg，
   余额 300→20，两条 LANDING 流水均可溯源（`sameVesselTwoLandings_…`）。
3. **两个航次额度不足**：信天翁号 MOK 余额 150，场景 1 已扣 80 余 70；
   第一航次再扣 50 余 20；第二航次申报 30 即预警「预计缺口 10.000」，
   卸货 30 被拒（`QUOTA_EXCEEDED`），余额不变
   （JUnit 中以独立数据验证 `twoVoyages_insufficientQuota_…`）。
4. **重复凭证**：同一卸货凭证号再次录入 → HTTP 409 `DUPLICATE`，不重复扣减
   （`duplicateCertificate_…`）。
5. **调拨配对**：海带号→信天翁号 100 kg，双方各一条流水并互见对方船号与生效期间
   （`transfer_createsPairedEntries…`）。
6. **维度不匹配**：跨物种/海区调拨、卸货条目越出申报维度均被拒
   （`mismatchedDimensions_…`）。
7. **超额调出**：调出量大于来源余额被拒，双方余额不变
   （`transferBeyondSourceBalance_…`）。

## API 摘要

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/reference/{vessels,species,areas,seasons}` | 虚构基础数据 |
| GET | `/api/balances?vesselId&speciesId&areaId&seasonId` | 余额 + 预测占用/预测可用 |
| GET | `/api/balances/{id}/trace` | 流水 + 关联航次 + 关联调拨 |
| POST | `/api/allocations` | 额度分配（生成 ALLOCATION 流水） |
| POST | `/api/voyages` | 航次申报（预计用量，返回缺口预警） |
| GET | `/api/voyages` | 航次列表（预计/实捕/差额/凭证） |
| POST | `/api/landings` | 卸货录入（实捕扣减；凭证唯一） |
| POST | `/api/transfers` | 调拨（配对扣补 + 生效期间校验） |

错误响应统一为 `{code, message}`：`DUPLICATE`（409）、`QUOTA_EXCEEDED`（409）、
`INVALID`（400）、`NOT_FOUND`（404）。
