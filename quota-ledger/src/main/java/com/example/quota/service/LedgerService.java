package com.example.quota.service;

import com.example.quota.api.Dtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Quota ledger operations. One balance per (vessel, species, area, season)
 * account. Declarations RESERVE estimate, landings deduct ACTUAL weight,
 * voyage close settles the estimate/actual difference. Transfers post two
 * balanced ledger legs and never mutate a single balance on its own.
 */
@Service
public class LedgerService {

    private final JdbcTemplate jdbc;

    public LedgerService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ read

    public List<AccountView> accounts(String vesselCode, String speciesCode,
                                      String areaCode, String seasonCode) {
        StringBuilder sql = new StringBuilder("""
                SELECT a.id, a.vessel_code, a.species_code, a.area_code, a.season_code,
                       a.issued_qty, a.reserved_qty, a.landed_qty,
                       a.transferred_in_qty, a.transferred_out_qty,
                       (a.issued_qty + a.transferred_in_qty - a.transferred_out_qty
                        - a.reserved_qty - a.landed_qty) AS available_qty
                FROM quota_account a WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        addEq(sql, args, "a.vessel_code", vesselCode);
        addEq(sql, args, "a.species_code", speciesCode);
        addEq(sql, args, "a.area_code", areaCode);
        addEq(sql, args, "a.season_code", seasonCode);
        sql.append(" ORDER BY a.vessel_code, a.species_code, a.area_code");
        return jdbc.query(sql.toString(), ACCOUNT_ROW, args.toArray());
    }

    public AccountView getAccount(long id) {
        List<AccountView> list = jdbc.query("""
                SELECT a.id, a.vessel_code, a.species_code, a.area_code, a.season_code,
                       a.issued_qty, a.reserved_qty, a.landed_qty,
                       a.transferred_in_qty, a.transferred_out_qty,
                       (a.issued_qty + a.transferred_in_qty - a.transferred_out_qty
                        - a.reserved_qty - a.landed_qty) AS available_qty
                FROM quota_account a WHERE a.id = ?
                """, ACCOUNT_ROW, id);
        if (list.isEmpty()) throw new LedgerRuleException("配额账户不存在: id=" + id);
        return list.get(0);
    }

    public List<LedgerEntryView> ledger(Long accountId) {
        String sql = """
                SELECT id, account_id, event_type, amount, balance_after, event_date,
                       ref_type, ref_id, memo
                FROM quota_ledger_entry
                WHERE (?::bigint IS NULL OR account_id = ?)
                ORDER BY id
                """;
        return jdbc.query(sql, LEDGER_ROW, accountId, accountId);
    }

    public List<VoyageView> voyages(String vesselCode) {
        String sql = """
                SELECT id, voyage_no, vessel_code, area_code, season_code,
                       departs_on, returns_on, status
                FROM voyage WHERE (?::text IS NULL OR vessel_code = ?)
                ORDER BY id
                """;
        List<VoyageView> voyages = jdbc.query(sql, VOYAGE_ROW, vesselCode, vesselCode);
        List<VoyageView> result = new ArrayList<>();
        for (VoyageView v : voyages) result.add(loadVoyageDetail(v));
        return result;
    }

    public VoyageView voyageByNo(String voyageNo) {
        List<VoyageView> list = jdbc.query("""
                SELECT id, voyage_no, vessel_code, area_code, season_code,
                       departs_on, returns_on, status
                FROM voyage WHERE voyage_no = ?
                """, VOYAGE_ROW, voyageNo);
        if (list.isEmpty()) throw new LedgerRuleException("航次不存在: " + voyageNo);
        return loadVoyageDetail(list.get(0));
    }

    public List<TransferView> transfers(Long accountId) {
        String sql = """
                SELECT t.id, t.transfer_no, t.species_code, t.area_code, t.season_code,
                       t.qty, t.from_account_id, fv.code AS from_vessel,
                       t.to_account_id, tv.code AS to_vessel,
                       t.effective_from, t.effective_to, t.status, t.memo, t.created_at
                FROM quota_transfer t
                JOIN quota_account fa ON fa.id = t.from_account_id
                JOIN vessel fv ON fv.code = fa.vessel_code
                JOIN quota_account ta ON ta.id = t.to_account_id
                JOIN vessel tv ON tv.code = ta.vessel_code
                WHERE (?::bigint IS NULL OR t.from_account_id = ? OR t.to_account_id = ?)
                ORDER BY t.id
                """;
        return jdbc.query(sql, TRANSFER_ROW, accountId, accountId, accountId);
    }

    // ------------------------------------------------------------- declare

    @Transactional
    public VoyageView declareVoyage(DeclareVoyageRequest req) {
        if (req.lines() == null || req.lines().isEmpty()) {
            throw new LedgerRuleException("航次申报至少要包含一个物种的预计用量");
        }
        assertVessel(req.vesselCode());
        assertArea(req.areaCode());
        DateRange season = assertSeason(req.seasonCode());
        if (req.departsOn() == null) {
            throw new LedgerRuleException("必须填写出海日期");
        }
        if (req.departsOn().isBefore(season.from()) || req.departsOn().isAfter(season.to())) {
            throw new LedgerRuleException(
                    "出海日期 %s 不在季节 %s (%s~%s) 内".formatted(
                            req.departsOn(), req.seasonCode(), season.from(), season.to()));
        }
        Integer dup = jdbc.queryForObject(
                "SELECT count(*) FROM voyage WHERE voyage_no = ?", Integer.class, req.voyageNo());
        if (dup != null && dup > 0) {
            throw new LedgerRuleException("航次编号重复: " + req.voyageNo());
        }

        // Lock and validate every account up front: declaration is atomic,
        // either all species reserve or none.
        record Line(DeclareLine in, AccountView acct) {}
        List<Line> lines = new ArrayList<>();
        for (DeclareLine line : req.lines()) {
            if (line.estimatedQty() == null || line.estimatedQty().signum() <= 0) {
                throw new LedgerRuleException("预计用量必须为正数: " + line.speciesCode());
            }
            assertPermit(line.speciesCode(), req.areaCode(), req.seasonCode());
            AccountView acct = lockAccountForUpdate(
                    req.vesselCode(), line.speciesCode(), req.areaCode(), req.seasonCode());
            if (acct.availableQty().compareTo(line.estimatedQty()) < 0) {
                throw new LedgerRuleException(
                        "额度不足: 船舶 %s 物种 %s %s %s 可用 %s kg，预计用量需要 %s kg"
                                .formatted(req.vesselCode(), line.speciesCode(), req.areaCode(),
                                        req.seasonCode(), acct.availableQty().toPlainString(),
                                        line.estimatedQty().toPlainString()));
            }
            lines.add(new Line(line, acct));
        }

        jdbc.update("""
                INSERT INTO voyage(voyage_no, vessel_code, area_code, season_code, departs_on)
                VALUES (?, ?, ?, ?, ?)
                """, req.voyageNo(), req.vesselCode(), req.areaCode(), req.seasonCode(),
                Date.valueOf(req.departsOn()));
        Long voyageId = jdbc.queryForObject(
                "SELECT id FROM voyage WHERE voyage_no = ?", Long.class, req.voyageNo());

        for (Line line : lines) {
            jdbc.update("""
                    INSERT INTO voyage_declaration
                        (voyage_id, species_code, estimated_qty, reserved_qty)
                    VALUES (?, ?, ?, ?)
                    """, voyageId, line.in().speciesCode(), line.in().estimatedQty(),
                    line.in().estimatedQty());
            BigDecimal newReserved = line.acct.reservedQty().add(line.in().estimatedQty());
            updateAccountColumn(line.acct.id(), "reserved_qty", newReserved);
            BigDecimal availableAfter = computeAvailable(line.acct.id());
            postLedger(line.acct.id(), "RESERVE", line.in().estimatedQty().negate(),
                    availableAfter, "VOYAGE_DECLARATION", voyageId,
                    "航次 %s 预留预计用量 %s kg".formatted(
                            req.voyageNo(), line.in().estimatedQty().toPlainString()));
        }
        return voyageByNo(req.voyageNo());
    }

    // ------------------------------------------------------------- landing

    @Transactional
    public LandingView recordLanding(LandingRequest req) {
        if (req.landedQty() == null || req.landedQty().signum() <= 0) {
            throw new LedgerRuleException("实际卸货重量必须为正数");
        }
        // Idempotency: voucher number is the unique de-duplication key.
        List<Long> existing = jdbc.queryForList(
                "SELECT id FROM landing_voucher WHERE voucher_no = ?", Long.class, req.voucherNo());
        if (!existing.isEmpty()) {
            // Same voucher posted again: return the stored record, do NOT deduct twice.
            return getLanding(existing.get(0));
        }

        List<Long> voyageIds = jdbc.queryForList(
                "SELECT id FROM voyage WHERE voyage_no = ? AND status = 'OPEN'",
                Long.class, req.voyageNo());
        if (voyageIds.isEmpty()) {
            throw new LedgerRuleException(
                    "找不到未关闭航次: " + req.voyageNo() + "（航次不存在或已结算关闭）");
        }
        long voyageId = voyageIds.get(0);

        VesselSeason voyage = jdbc.queryForObject("""
                SELECT vessel_code, area_code, season_code FROM voyage WHERE id = ?
                """, (rs, n) -> new VesselSeason(
                        rs.getString("vessel_code"),
                        rs.getString("area_code"),
                        rs.getString("season_code")), voyageId);

        // The species must be on this voyage's declaration first; the permit
        // matrix was already enforced at declaration time and is re-checked
        // here as defence in depth.
        List<Long> declIds = jdbc.queryForList("""
                SELECT id FROM voyage_declaration
                WHERE voyage_id = ? AND species_code = ?
                """, Long.class, voyageId, req.speciesCode());
        if (declIds.isEmpty()) {
            throw new LedgerRuleException(
                    "航次 %s 未申报物种 %s，不能对该物种卸货".formatted(
                            req.voyageNo(), req.speciesCode()));
        }
        assertPermit(req.speciesCode(), voyage.areaCode(), voyage.seasonCode());
        long declId = declIds.get(0);

        AccountView acct = lockAccountForUpdate(
                voyage.vesselCode(), req.speciesCode(), voyage.areaCode(), voyage.seasonCode());

        BigDecimal estimated = jdbc.queryForObject(
                "SELECT estimated_qty FROM voyage_declaration WHERE id = ?",
                BigDecimal.class, declId);
        BigDecimal declReserve = nvl(jdbc.queryForObject(
                "SELECT reserved_qty FROM voyage_declaration WHERE id = ?",
                BigDecimal.class, declId));
        BigDecimal alreadyLanded = nvl(jdbc.queryForObject(
                "SELECT landed_qty FROM voyage_declaration WHERE id = ?",
                BigDecimal.class, declId));
        BigDecimal newLandedTotal = alreadyLanded.add(req.landedQty());

        // Up to the estimate, the landing consumes the reservation directly.
        // Anything above the estimate needs free (unreserved) balance.
        BigDecimal coveredByReserve = req.landedQty().min(declReserve);
        BigDecimal extraNeeded = req.landedQty().subtract(coveredByReserve);
        if (acct.availableQty().compareTo(extraNeeded) < 0) {
            throw new LedgerRuleException(
                    "额度不足: 本航次物种 %s 预计 %s kg、已卸 %s kg，本次实捕 %s kg 中"
                            + "有 %s kg 超出剩余预留；账户可用仅 %s kg，预计之外的实捕没有额度"
                            .formatted(req.speciesCode(), estimated.toPlainString(),
                                    alreadyLanded.toPlainString(), req.landedQty().toPlainString(),
                                    extraNeeded.toPlainString(), acct.availableQty().toPlainString()));
        }

        jdbc.update("""
                INSERT INTO landing_voucher
                    (voucher_no, voyage_id, species_code, area_code, season_code, landed_qty)
                VALUES (?, ?, ?, ?, ?, ?)
                """, req.voucherNo(), voyageId, req.speciesCode(),
                voyage.areaCode(), voyage.seasonCode(), req.landedQty());
        Long landingId = jdbc.queryForObject(
                "SELECT id FROM landing_voucher WHERE voucher_no = ?", Long.class, req.voucherNo());

        // Declaration-level reserve shrinks by the part this landing covers.
        jdbc.update("UPDATE voyage_declaration SET landed_qty = ?, reserved_qty = ? WHERE id = ?",
                newLandedTotal, declReserve.subtract(coveredByReserve), declId);

        BigDecimal newLandedAcct = acct.landedQty().add(req.landedQty());
        updateAccountColumn(acct.id(), "landed_qty", newLandedAcct);
        BigDecimal newReservedAcct = acct.reservedQty().subtract(coveredByReserve);
        updateAccountColumn(acct.id(), "reserved_qty", newReservedAcct);
        BigDecimal availableAfter = computeAvailable(acct.id());
        String memo;
        if (coveredByReserve.signum() > 0 && extraNeeded.signum() > 0) {
            memo = "卸货凭证 %s / 航次 %s 实捕 %s kg：%s kg 冲减预留、%s kg 占用额外额度"
                    .formatted(req.voucherNo(), req.voyageNo(), req.landedQty().toPlainString(),
                            coveredByReserve.toPlainString(), extraNeeded.toPlainString());
        } else if (extraNeeded.signum() > 0) {
            memo = "卸货凭证 %s / 航次 %s 实捕 %s kg，全部超出预计、占用额外额度"
                    .formatted(req.voucherNo(), req.voyageNo(), req.landedQty().toPlainString());
        } else {
            memo = "卸货凭证 %s / 航次 %s 实捕 %s kg，冲减预留"
                    .formatted(req.voucherNo(), req.voyageNo(), req.landedQty().toPlainString());
        }
        postLedger(acct.id(), "LANDING", req.landedQty().negate(), availableAfter,
                "LANDING_VOUCHER", landingId, memo);
        return getLanding(landingId);
    }

    // -------------------------------------------------------------- close

    @Transactional
    public VoyageView closeVoyage(String voyageNo) {
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM voyage WHERE voyage_no = ?", Long.class, voyageNo);
        if (ids.isEmpty()) throw new LedgerRuleException("航次不存在: " + voyageNo);
        long voyageId = ids.get(0);
        String status = jdbc.queryForObject(
                "SELECT status FROM voyage WHERE id = ?", String.class, voyageId);
        if ("CLOSED".equals(status)) {
            throw new LedgerRuleException("航次已结算关闭: " + voyageNo);
        }

        record Decl(long id, String species, BigDecimal estimated,
                    BigDecimal landed, BigDecimal reserved) {}
        List<Decl> decls = jdbc.query("""
                SELECT id, species_code, estimated_qty, landed_qty, reserved_qty
                FROM voyage_declaration WHERE voyage_id = ?
                """, (rs, n) -> new Decl(rs.getLong("id"), rs.getString("species_code"),
                        rs.getBigDecimal("estimated_qty"), rs.getBigDecimal("landed_qty"),
                        rs.getBigDecimal("reserved_qty")), voyageId);

        VesselSeason voyage = jdbc.queryForObject("""
                SELECT vessel_code, area_code, season_code FROM voyage WHERE id = ?
                """, (rs, n) -> new VesselSeason(rs.getString("vessel_code"),
                        rs.getString("area_code"), rs.getString("season_code")), voyageId);

        for (Decl d : decls) {
            AccountView acct = lockAccountForUpdate(
                    voyage.vesselCode(), d.species(), voyage.areaCode(), voyage.seasonCode());
            // reserved_qty now holds only the estimate not yet consumed by landings;
            // release whatever remains (estimated > actual case).
            BigDecimal release = d.reserved().max(BigDecimal.ZERO);
            if (release.signum() > 0) {
                BigDecimal newReserved = acct.reservedQty().subtract(release);
                updateAccountColumn(acct.id(), "reserved_qty", newReserved);
                BigDecimal availableAfter = computeAvailable(acct.id());
                postLedger(acct.id(), "SETTLE_RESERVE", release, availableAfter,
                        "VOYAGE_SETTLEMENT", voyageId,
                        "航次 %s 结算释放预计未用量 %s kg（预计 %s - 实捕 %s）"
                                .formatted(voyageNo, release.toPlainString(),
                                        d.estimated().toPlainString(), d.landed().toPlainString()));
            }
            jdbc.update("UPDATE voyage_declaration SET reserved_qty = 0, settled = TRUE WHERE id = ?",
                    d.id());
        }
        jdbc.update("UPDATE voyage SET status = 'CLOSED', returns_on = CURRENT_DATE WHERE id = ?",
                voyageId);
        return voyageByNo(voyageNo);
    }

    // ------------------------------------------------------------ transfer

    @Transactional
    public TransferView transferQuota(TransferRequest req) {
        if (req.qty() == null || req.qty().signum() <= 0) {
            throw new LedgerRuleException("调拨数量必须为正数");
        }
        if (req.fromVesselCode().equals(req.toVesselCode())) {
            throw new LedgerRuleException("调出与调入船舶不能相同");
        }
        assertVessel(req.fromVesselCode());
        assertVessel(req.toVesselCode());
        assertSpecies(req.speciesCode());
        assertArea(req.areaCode());
        DateRange season = assertSeason(req.seasonCode());
        if (req.effectiveFrom() == null || req.effectiveTo() == null) {
            throw new LedgerRuleException("调拨必须填写生效期间");
        }
        if (req.effectiveTo().isBefore(req.effectiveFrom())) {
            throw new LedgerRuleException("生效结束日不能早于开始日");
        }
        if (req.effectiveFrom().isBefore(season.from())
                || req.effectiveTo().isAfter(season.to())) {
            throw new LedgerRuleException(
                    "调拨生效期间 %s~%s 必须落在季节 %s (%s~%s) 内".formatted(
                            req.effectiveFrom(), req.effectiveTo(), req.seasonCode(),
                            season.from(), season.to()));
        }

        // Read both accounts first, then lock in a deterministic id order
        // (avoids deadlocks when two transfers run in opposite directions).
        AccountView from = findAccount(
                req.fromVesselCode(), req.speciesCode(), req.areaCode(), req.seasonCode());
        AccountView to = findAccount(
                req.toVesselCode(), req.speciesCode(), req.areaCode(), req.seasonCode());
        AccountView first = from.id() < to.id() ? from : to;
        AccountView second = from.id() < to.id() ? to : from;
        lockById(first.id());
        lockById(second.id());
        from = getAccount(from.id());
        to = getAccount(to.id());
        if (from.availableQty().compareTo(req.qty()) < 0) {
            throw new LedgerRuleException(
                    "调拨失败: 调出方可用额度仅 %s kg，无法调出 %s kg"
                            .formatted(from.availableQty().toPlainString(),
                                    req.qty().toPlainString()));
        }

        jdbc.update("""
                INSERT INTO quota_transfer(transfer_no, species_code, area_code, season_code,
                    qty, from_account_id, to_account_id,
                    effective_from, effective_to, status, memo)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'EFFECTIVE', ?)
                """, req.transferNo(), req.speciesCode(), req.areaCode(), req.seasonCode(),
                req.qty(), from.id(), to.id(),
                Date.valueOf(req.effectiveFrom()), Date.valueOf(req.effectiveTo()), req.memo());
        Long transferId = jdbc.queryForObject(
                "SELECT id FROM quota_transfer WHERE transfer_no = ?", Long.class,
                req.transferNo());

        updateAccountColumn(from.id(), "transferred_out_qty",
                from.transferredOutQty().add(req.qty()));
        BigDecimal fromAfter = computeAvailable(from.id());
        postLedger(from.id(), "TRANSFER_OUT", req.qty().negate(), fromAfter,
                "QUOTA_TRANSFER", transferId,
                "调拨 %s 出向 %s，期间 %s~%s".formatted(req.transferNo(),
                        req.toVesselCode(), req.effectiveFrom(), req.effectiveTo()));

        updateAccountColumn(to.id(), "transferred_in_qty",
                to.transferredInQty().add(req.qty()));
        BigDecimal toAfter = computeAvailable(to.id());
        postLedger(to.id(), "TRANSFER_IN", req.qty(), toAfter,
                "QUOTA_TRANSFER", transferId,
                "调拨 %s 来自 %s，期间 %s~%s".formatted(req.transferNo(),
                        req.fromVesselCode(), req.effectiveFrom(), req.effectiveTo()));

        return transfers(null).stream()
                .filter(t -> t.id() == transferId).findFirst().orElseThrow();
    }

    // --------------------------------------------------------------- issue

    @Transactional
    public AccountView issueQuota(IssueRequest req) {
        if (req.qty() == null || req.qty().signum() <= 0) {
            throw new LedgerRuleException("发放数量必须为正数");
        }
        assertSpecies(req.speciesCode());
        assertArea(req.areaCode());
        assertSeason(req.seasonCode());
        assertVessel(req.vesselCode());
        AccountView acct = findOrCreateAccount(
                req.vesselCode(), req.speciesCode(), req.areaCode(), req.seasonCode());
        BigDecimal newIssued = acct.issuedQty().add(req.qty());
        updateAccountColumn(acct.id(), "issued_qty", newIssued);
        BigDecimal availableAfter = computeAvailable(acct.id());
        postLedger(acct.id(), "ISSUE", req.qty(), availableAfter, "MANUAL_ISSUE", null,
                req.memo() == null ? "追加额度发放" : req.memo());
        return getAccount(acct.id());
    }

    // ------------------------------------------------------------- helpers

    private AccountView lockAccountForUpdate(String vessel, String species, String area,
                                             String season) {
        List<AccountView> list = jdbc.query("""
                SELECT a.id, a.vessel_code, a.species_code, a.area_code, a.season_code,
                       a.issued_qty, a.reserved_qty, a.landed_qty,
                       a.transferred_in_qty, a.transferred_out_qty,
                       (a.issued_qty + a.transferred_in_qty - a.transferred_out_qty
                        - a.reserved_qty - a.landed_qty) AS available_qty
                FROM quota_account a
                WHERE a.vessel_code = ? AND a.species_code = ?
                  AND a.area_code = ? AND a.season_code = ?
                FOR UPDATE
                """, ACCOUNT_ROW, vessel, species, area, season);
        if (list.isEmpty()) {
            throw new LedgerRuleException(
                    "无此配额账户: 船舶 %s 物种 %s 海区 %s 季节 %s —— "
                            + "该组合没有可用额度，物种/海区/季节不匹配的额度不能使用"
                            .formatted(vessel, species, area, season));
        }
        return list.get(0);
    }

    private AccountView findAccount(String vessel, String species, String area, String season) {
        List<AccountView> list = jdbc.query("""
                SELECT a.id, a.vessel_code, a.species_code, a.area_code, a.season_code,
                       a.issued_qty, a.reserved_qty, a.landed_qty,
                       a.transferred_in_qty, a.transferred_out_qty,
                       (a.issued_qty + a.transferred_in_qty - a.transferred_out_qty
                        - a.reserved_qty - a.landed_qty) AS available_qty
                FROM quota_account a
                WHERE a.vessel_code = ? AND a.species_code = ?
                  AND a.area_code = ? AND a.season_code = ?
                """, ACCOUNT_ROW, vessel, species, area, season);
        if (list.isEmpty()) {
            throw new LedgerRuleException(
                    "无此配额账户: 船舶 %s 物种 %s 海区 %s 季节 %s —— "
                            + "该组合没有可用额度，物种/海区/季节不匹配的额度不能使用"
                            .formatted(vessel, species, area, season));
        }
        return list.get(0);
    }

    private void lockById(long accountId) {
        jdbc.queryForObject("SELECT id FROM quota_account WHERE id = ? FOR UPDATE",
                Long.class, accountId);
    }

    private AccountView findOrCreateAccount(String vessel, String species, String area,
                                            String season) {
        List<AccountView> list = jdbc.query("""
                SELECT a.id, a.vessel_code, a.species_code, a.area_code, a.season_code,
                       a.issued_qty, a.reserved_qty, a.landed_qty,
                       a.transferred_in_qty, a.transferred_out_qty,
                       (a.issued_qty + a.transferred_in_qty - a.transferred_out_qty
                        - a.reserved_qty - a.landed_qty) AS available_qty
                FROM quota_account a
                WHERE a.vessel_code = ? AND a.species_code = ?
                  AND a.area_code = ? AND a.season_code = ?
                """, ACCOUNT_ROW, vessel, species, area, season);
        if (!list.isEmpty()) return list.get(0);
        jdbc.update("""
                INSERT INTO quota_account
                    (vessel_code, species_code, area_code, season_code)
                VALUES (?, ?, ?, ?)
                """, vessel, species, area, season);
        long id = jdbc.queryForObject("""
                SELECT id FROM quota_account
                WHERE vessel_code=? AND species_code=? AND area_code=? AND season_code=?
                """, Long.class, vessel, species, area, season);
        return getAccount(id);
    }

    private void updateAccountColumn(long accountId, String column, BigDecimal value) {
        int n = jdbc.update(
                "UPDATE quota_account SET " + column + " = ?, version = version + 1 WHERE id = ?",
                value, accountId);
        if (n != 1) throw new IllegalStateException("account update failed: " + accountId);
    }

    private BigDecimal computeAvailable(long accountId) {
        return jdbc.queryForObject("""
                SELECT issued_qty + transferred_in_qty - transferred_out_qty
                       - reserved_qty - landed_qty
                FROM quota_account WHERE id = ?
                """, BigDecimal.class, accountId);
    }

    private void postLedger(long accountId, String eventType, BigDecimal signedAmount,
                            BigDecimal balanceAfter, String refType, Long refId, String memo) {
        jdbc.update("""
                INSERT INTO quota_ledger_entry
                    (account_id, event_type, amount, balance_after, ref_type, ref_id, memo)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, accountId, eventType, signedAmount, balanceAfter, refType, refId, memo);
    }

    private void addEq(StringBuilder sql, List<Object> args, String column, String value) {
        if (value != null && !value.isBlank()) {
            sql.append(" AND ").append(column).append(" = ?");
            args.add(value);
        }
    }

    private void assertSpecies(String code) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM species WHERE code = ?", Integer.class, code);
        if (n == null || n == 0) throw new LedgerRuleException("未知虚构物种: " + code);
    }

    private void assertArea(String code) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM sea_area WHERE code = ?", Integer.class, code);
        if (n == null || n == 0) throw new LedgerRuleException("未知海区: " + code);
    }

    private DateRange assertSeason(String code) {
        List<DateRange> list = jdbc.query(
                "SELECT starts_on, ends_on FROM season WHERE code = ?",
                (rs, i) -> new DateRange(
                        rs.getDate("starts_on").toLocalDate(),
                        rs.getDate("ends_on").toLocalDate()), code);
        if (list.isEmpty()) throw new LedgerRuleException("未知季节: " + code);
        return list.get(0);
    }

    private void assertVessel(String code) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM vessel WHERE code = ?", Integer.class, code);
        if (n == null || n == 0) throw new LedgerRuleException("未知船舶: " + code);
    }

    private void assertPermit(String species, String area, String season) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM species_permit
                WHERE species_code = ? AND area_code = ? AND season_code = ?
                """, Integer.class, species, area, season);
        if (n == null || n == 0) {
            throw new LedgerRuleException(
                    "许可规则不允许: 虚构物种 %s 在 %s %s 捕捞（物种/海区/季节不匹配）"
                            .formatted(species, area, season));
        }
    }

    private LandingView getLanding(long id) {
        return jdbc.queryForObject("""
                SELECT id, voucher_no, voyage_id, species_code, area_code, season_code,
                       landed_qty, recorded_at
                FROM landing_voucher WHERE id = ?
                """, LANDING_ROW, id);
    }

    private VoyageView loadVoyageDetail(VoyageView v) {
        List<DeclarationLineView> lines = jdbc.query("""
                SELECT species_code, estimated_qty, landed_qty, reserved_qty, settled
                FROM voyage_declaration WHERE voyage_id = ? ORDER BY id
                """, DECL_LINE_ROW, v.id());
        List<LandingView> landings = jdbc.query("""
                SELECT id, voucher_no, voyage_id, species_code, area_code, season_code,
                       landed_qty, recorded_at
                FROM landing_voucher WHERE voyage_id = ? ORDER BY id
                """, LANDING_ROW, v.id());
        return new VoyageView(v.id(), v.voyageNo(), v.vesselCode(), v.areaCode(),
                v.seasonCode(), v.departsOn(), v.returnsOn(), v.status(), lines, landings);
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private record VesselSeason(String vesselCode, String areaCode, String seasonCode) {}
    private record DateRange(LocalDate from, LocalDate to) {}

    // ---------------------------------------------------------------- row maps

    private static final RowMapper<AccountView> ACCOUNT_ROW = (rs, n) -> new AccountView(
            rs.getLong("id"), rs.getString("vessel_code"), rs.getString("species_code"),
            rs.getString("area_code"), rs.getString("season_code"),
            rs.getBigDecimal("issued_qty"), rs.getBigDecimal("reserved_qty"),
            rs.getBigDecimal("landed_qty"), rs.getBigDecimal("transferred_in_qty"),
            rs.getBigDecimal("transferred_out_qty"), rs.getBigDecimal("available_qty"));

    private static final RowMapper<LedgerEntryView> LEDGER_ROW = (rs, n) -> {
        Timestamp ts = rs.getTimestamp("event_date");
        Long refId = (Long) rs.getObject("ref_id");
        return new LedgerEntryView(rs.getLong("id"), rs.getLong("account_id"),
                rs.getString("event_type"), rs.getBigDecimal("amount"),
                rs.getBigDecimal("balance_after"),
                ts == null ? null : ts.toLocalDateTime(),
                rs.getString("ref_type"), refId, rs.getString("memo"));
    };

    private static final RowMapper<VoyageView> VOYAGE_ROW = (rs, n) -> {
        Date d = rs.getDate("returns_on");
        return new VoyageView(rs.getLong("id"), rs.getString("voyage_no"),
                rs.getString("vessel_code"), rs.getString("area_code"),
                rs.getString("season_code"),
                rs.getDate("departs_on").toLocalDate(),
                d == null ? null : d.toLocalDate(),
                rs.getString("status"), List.of(), List.of());
    };

    private static final RowMapper<DeclarationLineView> DECL_LINE_ROW = (rs, n) -> {
        BigDecimal estimated = rs.getBigDecimal("estimated_qty");
        BigDecimal landed = rs.getBigDecimal("landed_qty");
        BigDecimal reserved = rs.getBigDecimal("reserved_qty");
        boolean settled = rs.getBoolean("settled");
        return new DeclarationLineView(rs.getString("species_code"), estimated, landed,
                reserved, landed.subtract(estimated), settled);
    };

    private static final RowMapper<LandingView> LANDING_ROW = (rs, n) -> {
        Timestamp ts = rs.getTimestamp("recorded_at");
        return new LandingView(rs.getLong("id"), rs.getString("voucher_no"),
                rs.getLong("voyage_id"), rs.getString("species_code"),
                rs.getString("area_code"), rs.getString("season_code"),
                rs.getBigDecimal("landed_qty"),
                ts == null ? null : ts.toLocalDateTime());
    };

    private static final RowMapper<TransferView> TRANSFER_ROW = (rs, n) -> {
        Timestamp ts = rs.getTimestamp("created_at");
        return new TransferView(rs.getLong("id"), rs.getString("transfer_no"),
                rs.getString("species_code"), rs.getString("area_code"),
                rs.getString("season_code"), rs.getBigDecimal("qty"),
                rs.getLong("from_account_id"), rs.getString("from_vessel"),
                rs.getLong("to_account_id"), rs.getString("to_vessel"),
                rs.getDate("effective_from").toLocalDate(),
                rs.getDate("effective_to").toLocalDate(),
                rs.getString("status"), rs.getString("memo"),
                ts == null ? null : ts.toLocalDateTime());
    };
}
