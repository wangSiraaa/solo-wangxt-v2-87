package org.fictfish.ledger.service;

import org.fictfish.ledger.domain.*;
import org.fictfish.ledger.repo.*;
import org.fictfish.ledger.web.dto.Views;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Assembles read-model views (entity ids -> codes/names, estimates, totals). */
@Component
@Transactional(readOnly = true)
public class ViewAssembler {

    private final VesselRepository vesselRepo;
    private final SpeciesRepository speciesRepo;
    private final SeaAreaRepository areaRepo;
    private final FishingSeasonRepository seasonRepo;
    private final VoyageRepository voyageRepo;
    private final LandingRepository landingRepo;
    private final LandingItemRepository landingItemRepo;
    private final QuotaBalanceRepository balanceRepo;
    private final QuotaTransferRepository transferRepo;

    public ViewAssembler(VesselRepository vesselRepo, SpeciesRepository speciesRepo,
                         SeaAreaRepository areaRepo, FishingSeasonRepository seasonRepo,
                         VoyageRepository voyageRepo,
                         LandingRepository landingRepo, LandingItemRepository landingItemRepo,
                         QuotaBalanceRepository balanceRepo,
                         QuotaTransferRepository transferRepo) {
        this.vesselRepo = vesselRepo;
        this.speciesRepo = speciesRepo;
        this.areaRepo = areaRepo;
        this.seasonRepo = seasonRepo;
        this.voyageRepo = voyageRepo;
        this.landingRepo = landingRepo;
        this.landingItemRepo = landingItemRepo;
        this.balanceRepo = balanceRepo;
        this.transferRepo = transferRepo;
    }

    public Map<Long, Vessel> vesselsById(Collection<Long> ids) {
        return vesselRepo.findAllById(ids).stream()
                .collect(Collectors.toMap(Vessel::getId, Function.identity()));
    }

    public Map<Long, Species> speciesById(Collection<Long> ids) {
        return speciesRepo.findAllById(ids).stream()
                .collect(Collectors.toMap(Species::getId, Function.identity()));
    }

    public Map<Long, SeaArea> areasById(Collection<Long> ids) {
        return areaRepo.findAllById(ids).stream()
                .collect(Collectors.toMap(SeaArea::getId, Function.identity()));
    }

    public Map<Long, FishingSeason> seasonsById(Collection<Long> ids) {
        return seasonRepo.findAllById(ids).stream()
                .collect(Collectors.toMap(FishingSeason::getId, Function.identity()));
    }

    /** Outstanding estimated usage per balance key: sum(estimated) - sum(landed)
     *  across all voyages, floored at zero. A declaration does NOT debit balances;
     *  this is used only for the forecast-remaining display. */
    public Map<BalanceKey, BigDecimal> forecastOccupied() {
        List<Voyage> voyages = voyageRepo.findAllWithItems();
        Map<Long, BigDecimal> landedByItem = landedByVoyageItem();
        Map<BalanceKey, BigDecimal> est = new HashMap<>();
        Map<BalanceKey, BigDecimal> landed = new HashMap<>();
        for (Voyage v : voyages) {
            for (VoyageItem vi : v.getItems()) {
                BalanceKey k = new BalanceKey(v.getVesselId(), vi.getSpeciesId(),
                        vi.getAreaId(), v.getSeasonId());
                est.merge(k, vi.getEstimatedKg(), BigDecimal::add);
                landed.merge(k, landedByItem.getOrDefault(vi.getId(), BigDecimal.ZERO),
                        BigDecimal::add);
            }
        }
        Map<BalanceKey, BigDecimal> out = new HashMap<>();
        est.forEach((k, e) -> {
            BigDecimal diff = e.subtract(landed.getOrDefault(k, BigDecimal.ZERO));
            out.put(k, diff.signum() > 0 ? diff : BigDecimal.ZERO);
        });
        return out;
    }

    /** voyageItemId -> summed actual kg across all landings. */
    public Map<Long, BigDecimal> landedByVoyageItem() {
        return landingItemRepo.findAll().stream()
                .collect(Collectors.groupingBy(LandingItem::getVoyageItemId,
                        Collectors.reducing(BigDecimal.ZERO, LandingItem::getActualKg,
                                BigDecimal::add)));
    }

    public Views.BalanceView balance(QuotaBalance b, Map<BalanceKey, BigDecimal> occupied) {
        Species sp = speciesRepo.findById(b.getSpeciesId()).orElseThrow();
        SeaArea ar = areaRepo.findById(b.getAreaId()).orElseThrow();
        FishingSeason se = seasonRepo.findById(b.getSeasonId()).orElseThrow();
        Vessel ve = vesselRepo.findById(b.getVesselId()).orElseThrow();
        BigDecimal occ = occupied.getOrDefault(
                new BalanceKey(b.getVesselId(), b.getSpeciesId(), b.getAreaId(), b.getSeasonId()),
                BigDecimal.ZERO);
        return new Views.BalanceView(b.getId(), ve.getCode(), ve.getName(),
                sp.getCode(), sp.getCommonName(), ar.getCode(), ar.getName(),
                se.getCode(), se.getSeasonStart(), se.getSeasonEnd(),
                b.getRemainingKg(), occ, b.getRemainingKg().subtract(occ), b.getUpdatedAt());
    }

    public List<Views.BalanceView> balances(List<QuotaBalance> list) {
        Map<BalanceKey, BigDecimal> occ = forecastOccupied();
        return list.stream().map(b -> balance(b, occ)).toList();
    }

    public Views.VoyageView voyage(Voyage v) {
        Vessel ve = vesselRepo.findById(v.getVesselId()).orElseThrow();
        FishingSeason se = seasonRepo.findById(v.getSeasonId()).orElseThrow();
        Map<Long, Species> species = speciesById(v.getItems().stream()
                .map(VoyageItem::getSpeciesId).toList());
        Map<Long, SeaArea> areas = areasById(v.getItems().stream()
                .map(VoyageItem::getAreaId).toList());

        List<Landing> landings = landingRepo.findByVoyageIdOrderByLandedAtAscIdAsc(v.getId());
        List<Long> itemIds = v.getItems().stream().map(VoyageItem::getId).toList();
        Map<Long, BigDecimal> landed = landingItemRepo.findByVoyageItemIdIn(itemIds).stream()
                .collect(Collectors.groupingBy(LandingItem::getVoyageItemId,
                        Collectors.reducing(BigDecimal.ZERO, LandingItem::getActualKg,
                                BigDecimal::add)));

        List<Views.VoyageItemView> itemViews = v.getItems().stream()
                .map(vi -> {
                    BigDecimal landedKg = landed.getOrDefault(vi.getId(), BigDecimal.ZERO);
                    return new Views.VoyageItemView(
                            species.get(vi.getSpeciesId()).getCode(),
                            species.get(vi.getSpeciesId()).getCommonName(),
                            areas.get(vi.getAreaId()).getCode(),
                            areas.get(vi.getAreaId()).getName(),
                            vi.getEstimatedKg(), landedKg,
                            vi.getEstimatedKg().subtract(landedKg),
                            landedKg.compareTo(vi.getEstimatedKg()) >= 0);
                })
                .toList();

        Map<Long, VoyageItem> itemById = v.getItems().stream()
                .collect(Collectors.toMap(VoyageItem::getId, Function.identity()));
        List<Views.LandingView> landingViews = landings.stream()
                .map(l -> {
                    List<Views.LandingView.LandedLine> lines = l.getItems().stream()
                            .map(li -> {
                                VoyageItem vi = itemById.get(li.getVoyageItemId());
                                return new Views.LandingView.LandedLine(
                                        species.get(vi.getSpeciesId()).getCode(),
                                        areas.get(vi.getAreaId()).getCode(),
                                        li.getActualKg());
                            })
                            .toList();
                    return new Views.LandingView(l.getId(), l.getCertificateNo(),
                            l.getLandedAt(), l.getPortName(), l.getRecordedAt(), lines);
                })
                .toList();

        return new Views.VoyageView(v.getId(), v.getVoyageNo(), ve.getCode(), ve.getName(),
                se.getCode(), v.getDepartedAt(), v.getStatus().name(), v.getDeclaredNote(),
                itemViews, landingViews);
    }

    public List<Views.VoyageView> voyages(List<Voyage> list) {
        return list.stream().map(this::voyage).toList();
    }

    public Views.TransferView transfer(QuotaTransfer t) {
        Vessel src = vesselRepo.findById(
                        balanceRepo.findById(t.getSourceBalanceId()).orElseThrow().getVesselId())
                .orElseThrow();
        Vessel dst = vesselRepo.findById(
                        balanceRepo.findById(t.getDestBalanceId()).orElseThrow().getVesselId())
                .orElseThrow();
        Species sp = speciesRepo.findById(t.getSpeciesId()).orElseThrow();
        SeaArea ar = areaRepo.findById(t.getAreaId()).orElseThrow();
        FishingSeason se = seasonRepo.findById(t.getSeasonId()).orElseThrow();
        return new Views.TransferView(t.getId(), t.getTransferNo(),
                src.getCode(), dst.getCode(), sp.getCode(), ar.getCode(), se.getCode(),
                t.getAmountKg(), t.getEffectiveFrom(), t.getEffectiveTo(),
                t.getStatus().name(), t.getCreatedAt(), t.getNote());
    }

    public List<Views.TransferView> transfers(List<QuotaTransfer> list) {
        return list.stream().map(this::transfer).toList();
    }

    public Views.LedgerView ledger(LedgerEntry e) {
        String counterparty = null;
        if (e.getTransferId() != null) {
            QuotaTransfer t = transferRepo.findById(e.getTransferId()).orElse(null);
            if (t != null) {
                Long other = t.getSourceBalanceId().equals(e.getBalanceId())
                        ? t.getDestBalanceId() : t.getSourceBalanceId();
                counterparty = balanceRepo.findById(other)
                        .flatMap(ob -> vesselRepo.findById(ob.getVesselId()))
                        .map(Vessel::getCode).orElse(null);
            }
        }
        return new Views.LedgerView(e.getId(), e.getBalanceId(), e.getEntryType().name(),
                e.getDeltaKg(), e.getRemainingKg(), e.getEntryDate(), e.getRefDoc(),
                e.getLandingId(), e.getTransferId(), counterparty, e.getNote());
    }

    public record BalanceKey(Long vesselId, Long speciesId, Long areaId, Long seasonId) {
    }
}
