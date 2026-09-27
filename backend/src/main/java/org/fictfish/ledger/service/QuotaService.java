package org.fictfish.ledger.service;

import org.fictfish.ledger.domain.*;
import org.fictfish.ledger.repo.*;
import org.fictfish.ledger.web.ApiException;
import org.fictfish.ledger.web.dto.Requests.*;
import org.fictfish.ledger.web.dto.Views;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Core quota ledger rules (all fictional, demo only):
 * <ul>
 *   <li>Declaration records ESTIMATED usage for forecast display; it does not debit.</li>
 *   <li>Landing records ACTUAL weights and debits balances; the actual amount
 *       must be covered by the matching vessel+species+area+season balance.</li>
 *   <li>Landing certificate numbers are unique: a repeated certificate is
 *       rejected and never debits twice.</li>
 *   <li>A transfer is always one debit/credit pair between two balances of the
 *       SAME species, sea area and season, with an explicit effective period.</li>
 * </ul>
 */
@Service
public class QuotaService {

    private final VesselRepository vesselRepo;
    private final SpeciesRepository speciesRepo;
    private final SeaAreaRepository areaRepo;
    private final FishingSeasonRepository seasonRepo;
    private final QuotaBalanceRepository balanceRepo;
    private final LedgerEntryRepository ledgerRepo;
    private final VoyageRepository voyageRepo;
    private final LandingRepository landingRepo;
    private final QuotaTransferRepository transferRepo;
    private final ViewAssembler assembler;

    public QuotaService(VesselRepository vesselRepo, SpeciesRepository speciesRepo,
                        SeaAreaRepository areaRepo, FishingSeasonRepository seasonRepo,
                        QuotaBalanceRepository balanceRepo, LedgerEntryRepository ledgerRepo,
                        VoyageRepository voyageRepo, LandingRepository landingRepo,
                        QuotaTransferRepository transferRepo, ViewAssembler assembler) {
        this.vesselRepo = vesselRepo;
        this.speciesRepo = speciesRepo;
        this.areaRepo = areaRepo;
        this.seasonRepo = seasonRepo;
        this.balanceRepo = balanceRepo;
        this.ledgerRepo = ledgerRepo;
        this.voyageRepo = voyageRepo;
        this.landingRepo = landingRepo;
        this.transferRepo = transferRepo;
        this.assembler = assembler;
    }

    private static BigDecimal kg(BigDecimal v) {
        return v.setScale(3, RoundingMode.HALF_UP);
    }

    // ---------------------------------------------------------------- reference

    private Vessel vessel(String code) {
        return vesselRepo.findByCode(code)
                .orElseThrow(() -> ApiException.notFound("未知船舶: " + code));
    }

    private Species species(String code) {
        return speciesRepo.findByCode(code)
                .orElseThrow(() -> ApiException.notFound("未知物种: " + code));
    }

    private SeaArea area(String code) {
        return areaRepo.findByCode(code)
                .orElseThrow(() -> ApiException.notFound("未知海区: " + code));
    }

    private FishingSeason season(String code) {
        return seasonRepo.findByCode(code)
                .orElseThrow(() -> ApiException.notFound("未知季节: " + code));
    }

    private QuotaBalance balance(Vessel v, Species s, SeaArea a, FishingSeason se) {
        return balanceRepo
                .findByVesselIdAndSpeciesIdAndAreaIdAndSeasonId(v.getId(), s.getId(),
                        a.getId(), se.getId())
                .orElseThrow(() -> ApiException.invalid(
                        "船舶 " + v.getCode() + " 不存在物种 " + s.getCode()
                                + " / 海区 " + a.getCode() + " / 季节 " + se.getCode()
                                + " 的额度账户；物种、海区或季节不匹配的额度不能使用"));
    }

    // ------------------------------------------------------------ allocation

    @Transactional
    public Views.BalanceView allocate(AllocationRequest req) {
        Vessel v = vessel(req.vesselCode());
        Species s = species(req.speciesCode());
        SeaArea a = area(req.areaCode());
        FishingSeason se = season(req.seasonCode());
        BigDecimal amount = kg(req.amountKg());

        QuotaBalance b = balanceRepo
                .findByVesselIdAndSpeciesIdAndAreaIdAndSeasonId(v.getId(), s.getId(),
                        a.getId(), se.getId())
                .orElseGet(() -> balanceRepo.save(
                        new QuotaBalance(v.getId(), s.getId(), a.getId(), se.getId(),
                                BigDecimal.ZERO.setScale(3))));
        b = balanceRepo.lockById(b.getId()).orElseThrow();
        b.applyDelta(amount);
        ledgerRepo.save(new LedgerEntry(b.getId(), LedgerType.ALLOCATION, amount,
                b.getRemainingKg(), OffsetDateTime.now(), null, null, null,
                req.note() != null ? req.note() : "初始/追加分配"));
        return assembler.balance(b, assembler.forecastOccupied());
    }

    // --------------------------------------------------------------- voyage

    @Transactional
    public VoyageDeclareResult declareVoyage(VoyageRequest req) {
        Vessel v = vessel(req.vesselCode());
        FishingSeason se = season(req.seasonCode());
        if (voyageRepo.findByVoyageNo(req.voyageNo()).isPresent()) {
            throw ApiException.duplicate("航次编号已存在: " + req.voyageNo());
        }
        if (req.items().isEmpty()) {
            throw ApiException.invalid("航次申报至少包含一个物种/海区条目");
        }

        // Resolve and validate every line up-front: the vessel must hold a
        // matching species+area+season quota account.
        record Line(Species species, SeaArea area, BigDecimal estimated) {
        }
        Set<String> seen = new HashSet<>();
        List<Line> lines = new ArrayList<>();
        for (var item : req.items()) {
            Species sp = species(item.speciesCode());
            SeaArea ar = area(item.areaCode());
            if (!seen.add(sp.getCode() + "|" + ar.getCode())) {
                throw ApiException.invalid(
                        "同一航次物种/海区条目重复: " + sp.getCode() + " / " + ar.getCode());
            }
            balance(v, sp, ar, se); // throws if no matching quota account
            lines.add(new Line(sp, ar, kg(item.estimatedKg())));
        }

        Voyage voyage = new Voyage(req.voyageNo(), v.getId(), se.getId(),
                req.departedAt(), req.note());
        for (Line l : lines) {
            voyage.addItem(new VoyageItem(l.species().getId(), l.area().getId(),
                    l.estimated()));
        }
        voyage = voyageRepo.save(voyage);

        // Forecast warnings (declaration never debits and is never blocked):
        List<String> warnings = forecastWarnings(v, se);
        return new VoyageDeclareResult(
                assembler.voyage(voyageRepo.findWithItemsById(voyage.getId()).orElseThrow()),
                warnings);
    }

    private List<String> forecastWarnings(Vessel v, FishingSeason se) {
        Map<ViewAssembler.BalanceKey, BigDecimal> occ = assembler.forecastOccupied();
        List<String> warnings = new ArrayList<>();
        balanceRepo.search(v.getId(), null, null, se.getId()).forEach(b -> {
            Species sp = speciesRepo.findById(b.getSpeciesId()).orElseThrow();
            SeaArea ar = areaRepo.findById(b.getAreaId()).orElseThrow();
            BigDecimal used = occ.getOrDefault(
                    new ViewAssembler.BalanceKey(v.getId(), sp.getId(), ar.getId(),
                            se.getId()),
                    BigDecimal.ZERO);
            BigDecimal projected = b.getRemainingKg().subtract(used);
            if (projected.signum() < 0) {
                warnings.add("预计用量超出余额：" + sp.getCode() + " / " + ar.getCode()
                        + " 预计缺口 " + projected.negate().toPlainString() + " kg");
            }
        });
        return warnings;
    }

    // -------------------------------------------------------------- landing

    public record LandingResult(Views.VoyageView voyage, List<String> warnings) {
    }

    @Transactional
    public LandingResult recordLanding(LandingRequest req) {
        if (landingRepo.findByCertificateNo(req.certificateNo()).isPresent()) {
            throw ApiException.duplicate(
                    "卸货凭证号已录入，禁止重复扣减: " + req.certificateNo());
        }
        Voyage voyage = voyageRepo.findByVoyageNo(req.voyageNo())
                .orElseThrow(() -> ApiException.notFound("未知航次: " + req.voyageNo()));
        Vessel v = vesselRepo.findById(voyage.getVesselId()).orElseThrow();
        FishingSeason se = seasonRepo.findById(voyage.getSeasonId()).orElseThrow();

        List<VoyageItem> voyageItems =
                voyageRepo.findWithItemsById(voyage.getId()).orElseThrow().getItems();

        // Match every landing line to a declared voyage line by species+area.
        // Quota of a mismatching species/area/season can never be consumed.
        record Line(VoyageItem voyageItem, Species species, SeaArea area,
                    BigDecimal actual) {
        }
        Set<Long> usedItems = new HashSet<>();
        List<Line> lines = new ArrayList<>();
        for (var item : req.items()) {
            Species sp = species(item.speciesCode());
            SeaArea ar = area(item.areaCode());
            VoyageItem vi = voyageItems.stream()
                    .filter(x -> x.getSpeciesId().equals(sp.getId())
                            && x.getAreaId().equals(ar.getId()))
                    .findFirst()
                    .orElseThrow(() -> ApiException.invalid(
                            "卸货条目 " + sp.getCode() + " / " + ar.getCode()
                                    + " 不在航次申报范围内：不匹配物种或海区的额度不能使用"));
            if (!usedItems.add(vi.getId())) {
                throw ApiException.invalid(
                        "同一卸货凭证内物种/海区条目重复: " + sp.getCode() + " / "
                                + ar.getCode());
            }
            lines.add(new Line(vi, sp, ar, kg(item.actualKg())));
        }

        // Lock every affected balance in id order, verify coverage, then debit.
        Map<Long, QuotaBalance> balances = new HashMap<>();
        for (Line l : lines) {
            QuotaBalance b = balance(v, l.species(), l.area(), se);
            balances.putIfAbsent(b.getId(), b);
        }
        List<QuotaBalance> locked = new ArrayList<>();
        for (Long id : balances.keySet().stream().sorted().toList()) {
            locked.add(balanceRepo.lockById(id).orElseThrow());
        }
        // Aggregate actual weight per balance (one line per species/area here,
        // but keep the aggregation explicit for clarity).
        Map<Long, BigDecimal> debitByBalance = new HashMap<>();
        Map<Long, Line> firstLineByBalance = new HashMap<>();
        for (Line l : lines) {
            QuotaBalance b = balance(v, l.species(), l.area(), se);
            debitByBalance.merge(b.getId(), l.actual(), BigDecimal::add);
            firstLineByBalance.putIfAbsent(b.getId(), l);
        }
        for (QuotaBalance b : locked) {
            BigDecimal debit = debitByBalance.get(b.getId());
            if (b.getRemainingKg().compareTo(debit) < 0) {
                Species sp = speciesRepo.findById(b.getSpeciesId()).orElseThrow();
                SeaArea ar = areaRepo.findById(b.getAreaId()).orElseThrow();
                throw ApiException.quotaExceeded(
                        "额度不足：" + sp.getCode() + " / " + ar.getCode() + " 余额 "
                                + b.getRemainingKg().toPlainString() + " kg，本次实捕 "
                                + debit.toPlainString() + " kg，缺口 "
                                + debit.subtract(b.getRemainingKg()).toPlainString()
                                + " kg；请先在同物种/同海区/同季节内调拨或追加额度");
            }
        }

        Landing landing = new Landing(req.certificateNo(), voyage.getId(),
                req.landedAt(), req.portName(), OffsetDateTime.now());
        for (Line l : lines) {
            landing.addItem(new LandingItem(l.voyageItem().getId(), l.actual()));
        }
        landing = landingRepo.save(landing);

        for (QuotaBalance b : locked) {
            BigDecimal debit = debitByBalance.get(b.getId());
            b.applyDelta(debit.negate());
            ledgerRepo.save(new LedgerEntry(b.getId(), LedgerType.LANDING,
                    debit.negate(), b.getRemainingKg(), OffsetDateTime.now(),
                    req.certificateNo(), landing.getId(), null,
                    "航次 " + voyage.getVoyageNo() + " 靠港 " + req.portName()
                            + " 实捕扣减"));
        }

        if (voyage.getStatus() == VoyageStatus.DECLARED) {
            voyage.markLanded();
        }
        voyageRepo.save(voyage);

        List<String> warnings = forecastWarnings(v, se);
        return new LandingResult(
                assembler.voyage(voyageRepo.findWithItemsById(voyage.getId()).orElseThrow()),
                warnings);
    }

    // ------------------------------------------------------------- transfer

    @Transactional
    public Views.TransferView transfer(TransferRequest req) {
        Vessel src = vessel(req.sourceVesselCode());
        Vessel dst = vessel(req.destVesselCode());
        if (src.getId().equals(dst.getId())) {
            throw ApiException.invalid("调拨来源与去向不能是同一船舶");
        }
        Species sp = species(req.speciesCode());
        SeaArea ar = area(req.areaCode());
        FishingSeason se = season(req.seasonCode());
        BigDecimal amount = kg(req.amountKg());

        if (req.effectiveFrom().isAfter(req.effectiveTo())) {
            throw ApiException.invalid("生效期间起始日不能晚于结束日");
        }
        if (req.effectiveFrom().isBefore(se.getSeasonStart())
                || req.effectiveTo().isAfter(se.getSeasonEnd())) {
            throw ApiException.invalid(
                    "生效期间必须位于季节 " + se.getCode() + " (" + se.getSeasonStart()
                            + " ~ " + se.getSeasonEnd() + ") 之内");
        }
        if (req.effectiveTo().isBefore(LocalDate.now())) {
            throw ApiException.invalid("调拨生效期间已过，不能登记");
        }

        // Lookups enforce the dimension rule: both legs must be accounts of the
        // exact same species, sea area and season.
        QuotaBalance source = balance(src, sp, ar, se);
        QuotaBalance dest = balance(dst, sp, ar, se);

        // Lock ordered by id to avoid deadlocks between concurrent transfers.
        List<Long> order = List.of(source.getId(), dest.getId()).stream().sorted().toList();
        Map<Long, QuotaBalance> locked = new HashMap<>();
        for (Long id : order) {
            locked.put(id, balanceRepo.lockById(id).orElseThrow());
        }
        source = locked.get(source.getId());
        dest = locked.get(dest.getId());

        if (source.getRemainingKg().compareTo(amount) < 0) {
            throw ApiException.quotaExceeded(
                    "调出方余额不足：余额 " + source.getRemainingKg().toPlainString()
                            + " kg，拟调出 " + amount.toPlainString() + " kg");
        }

        String transferNo = (req.transferNo() == null || req.transferNo().isBlank())
                ? "TR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()
                : req.transferNo();
        if (transferRepo.findByTransferNo(transferNo).isPresent()) {
            throw ApiException.duplicate("调拨单号已存在: " + transferNo);
        }

        QuotaTransfer transfer = new QuotaTransfer(transferNo, source.getId(),
                dest.getId(), sp.getId(), ar.getId(), se.getId(), amount,
                req.effectiveFrom(), req.effectiveTo(), req.note());
        try {
            transfer = transferRepo.saveAndFlush(transfer);
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.duplicate("调拨单登记失败（可能重复）: " + transferNo);
        }

        // One paired debit/credit — never just a single balance edit.
        source.applyDelta(amount.negate());
        dest.applyDelta(amount);
        OffsetDateTime now = OffsetDateTime.now();
        ledgerRepo.save(new LedgerEntry(source.getId(), LedgerType.TRANSFER_OUT,
                amount.negate(), source.getRemainingKg(), now, transferNo, null,
                transfer.getId(),
                "调出至 " + dst.getCode() + "，生效期 " + req.effectiveFrom() + " ~ "
                        + req.effectiveTo()));
        ledgerRepo.save(new LedgerEntry(dest.getId(), LedgerType.TRANSFER_IN,
                amount, dest.getRemainingKg(), now, transferNo, null, transfer.getId(),
                "自 " + src.getCode() + " 调入，生效期 " + req.effectiveFrom() + " ~ "
                        + req.effectiveTo()));

        return assembler.transfer(transfer);
    }

    // ----------------------------------------------------------------- trace

    @Transactional(readOnly = true)
    public Views.TraceView trace(Long balanceId) {
        QuotaBalance b = balanceRepo.findById(balanceId)
                .orElseThrow(() -> ApiException.notFound("未知额度账户: " + balanceId));
        Views.BalanceView balanceView =
                assembler.balance(b, assembler.forecastOccupied());

        List<Views.LedgerView> ledger = ledgerRepo
                .findByBalanceIdOrderByEntryDateAscIdAsc(balanceId).stream()
                .map(assembler::ledger).toList();

        List<QuotaTransfer> transfers = transferRepo.findInvolvingBalance(balanceId);
        List<Views.TransferView> transferViews = assembler.transfers(transfers);

        // Voyages whose declaration/landing touched this exact
        // vessel+species+area+season combination.
        List<Views.VoyageView> voyageViews = voyageRepo.findAllWithItems().stream()
                .filter(v -> v.getVesselId().equals(b.getVesselId())
                        && v.getSeasonId().equals(b.getSeasonId())
                        && v.getItems().stream().anyMatch(vi ->
                        vi.getSpeciesId().equals(b.getSpeciesId())
                                && vi.getAreaId().equals(b.getAreaId())))
                .map(assembler::voyage)
                .toList();

        return new Views.TraceView(balanceView, ledger, voyageViews, transferViews);
    }

    public record VoyageDeclareResult(Views.VoyageView voyage, List<String> warnings) {
    }
}
