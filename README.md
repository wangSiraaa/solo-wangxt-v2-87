# 渔业配额核对工作台（虚构演示）

> **免责声明：** 本系统为技术演示，物种（玉鳞鲛 / 赤纹鳕 / 星河鲀）、海区、季节与许可规则全部为虚构，
> **不连接任何监管系统，也不构成真实捕捞许可**。所有重量以千克（kg）表示，数据库使用
> `NUMERIC(14,3)`，Java 端全程使用 `BigDecimal`，不使用浮点数。

## 组成

| 模块 | 目录 | 技术 |
| --- | --- | --- |
| 配额账本服务 | `quota-ledger/` | Spring Boot 3.3、Spring JDBC、PostgreSQL 16 |
| 核对工作台 | `workbench/` | React 18 + Vite（原生 fetch，无重型 UI 依赖） |
| 本地数据库 | `docker-compose.yml` | PostgreSQL 16 |

## 领域与账本规则

* **余额账户**：每个「船舶 × 物种 × 海区 × 季节」组合一个独立账户
  （`quota_account`），可用余额 =
  `发放 + 调入 − 调出 − 预留 − 实捕扣减`。海区是账户维度之一，
  因此**不匹配物种 / 海区 / 季节的额度天然无法互相挪用**。
* **航次申报**：校验虚构许可规则（`species_permit`）与出海日期后，
  按各物种预计用量登记 `RESERVE`（预留，占用余额但不做实捕扣减）；
  整单原子——任一物种额度不足则整单失败。
* **靠港卸货**：按卸货凭证号录入实际重量，登记 `LANDING` 并扣减余额。
  实捕先冲减该航次的预留；**实际超出预计的部分必须有额外可用额度**，否则拒绝。
  预计与实际的差额在申报行以 `实捕 − 预计` 明确显示（负数＝预计大于实捕）。
* **航次结算关闭**：释放剩余预留（`SETTLE_RESERVE`），预计大于实捕的差额回到可用余额。
* **凭证幂等**：`landing_voucher.voucher_no` 唯一；重复提交同一凭证只返回原记录，
  **不会二次扣减**。
* **配额调拨**：一张调拨单（`quota_transfer`）记录物种、海区、季节、数量、
  **来源船、去向船、生效起止日期**；同一事务内生成
  `TRANSFER_OUT` / `TRANSFER_IN` 两条台账，**绝不只改一个余额**。
  生效期间必须落在季节内，调出方余额不足则拒绝。
* **不可篡改台账**：`quota_ledger_entry` 只追加，PostgreSQL 触发器禁止
  `UPDATE` / `DELETE`；账户表触发器保证可用余额不可能为负。

## 启动方式

### 1. 启动 PostgreSQL

```bash
docker compose up -d postgres
```

### 2. 启动后端（首次自动建表并写入虚构种子数据）

```bash
cd quota-ledger
export JAVA_HOME=/path/to/jdk-21
mvn spring-boot:run
# 服务监听 http://localhost:8080
```

可用环境变量覆盖：`QUOTA_DB_URL`、`QUOTA_DB_USER`、`QUOTA_DB_PASSWORD`。

### 3. 启动工作台

```bash
cd workbench
npm install
npm run dev
# 打开 http://localhost:5173 （/api 已代理到 8080）
```

## 验证样例（真实 PostgreSQL 集成测试）

测试使用 Zonky 嵌入式 PostgreSQL 16（无需 Docker）：

```bash
cd quota-ledger
mvn test
```

`QuotaScenariosTest` 依次验证：

1. **预计 > 实捕**：申报 300 kg、卸货 250 kg，差额 **−50.000 kg** 清楚显示，
   结算后释放 50 kg 预留；
2. **重复凭证不重复扣减**：同一凭证 `VCH-01` 提交两次，实捕仍为 250 kg、凭证仅 1 条；
3. **同船两次卸货**：同一航次 120 + 70 = 190 kg（预计 200，差额 −10）；
4. **两个航次额度不足**：第一航次预留 400 成功，第二航次要 250 而可用仅 200，
   返回 409 且第二航次不存在、无任何部分预留；
5. **实捕 > 预计**：超预计部分消耗额外额度；额度不足时整笔卸货失败、余额不变；
6. **调拨双向追溯**：150 kg 由 V02/CL-E 调往 V01/CL-E，两侧余额与
   `TRANSFER_OUT`/`TRANSFER_IN` 台账均可从余额钻取查看，并核对全局余额守恒；
7. **物种/海区/季节不匹配拒绝**：无许可组合申报拒绝、跨海区余额不可使用、
   未申报物种不能卸货；调拨生效期间超出季节、超额调拨均拒绝；
8. 台账 append-only：直接 `UPDATE`/`DELETE` 被 PostgreSQL 触发器拒绝。

## 主要 API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/reference` | 虚构物种/海区/季节/船舶/许可规则 |
| GET | `/api/accounts` | 余额列表（可按船舶/物种/海区/季节过滤） |
| GET | `/api/accounts/{id}/trace` | **从余额钻取**：航次（含卸货凭证）、调拨、台账流水 |
| POST | `/api/voyages` | 航次申报（预计用量预留） |
| POST | `/api/voyages/{no}/close` | 结算关闭，释放剩余预留 |
| POST | `/api/landings` | 录入卸货实际重量（凭证号幂等） |
| GET/POST | `/api/transfers` | 调拨查询/登记（来源、去向、生效期间） |
| POST | `/api/accounts/issue` | 追加发放额度 |
