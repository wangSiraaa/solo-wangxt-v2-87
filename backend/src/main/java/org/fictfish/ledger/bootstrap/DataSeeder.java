package org.fictfish.ledger.bootstrap;

import org.fictfish.ledger.domain.*;
import org.fictfish.ledger.repo.*;
import org.fictfish.ledger.service.QuotaService;
import org.fictfish.ledger.web.dto.Requests.AllocationRequest;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Inserts fictional reference data (vessels, invented species, fictional
 * areas/seasons) and opening quota allocations. Idempotent.
 * THIS IS DEMO DATA ONLY — the species and permit rules are invented and
 * this system is not a real fishing permit nor connected to any regulator.
 */
@Component
@Order(1)
public class DataSeeder implements ApplicationRunner {

    private final VesselRepository vesselRepo;
    private final SpeciesRepository speciesRepo;
    private final SeaAreaRepository areaRepo;
    private final FishingSeasonRepository seasonRepo;
    private final QuotaBalanceRepository balanceRepo;
    private final QuotaService quotaService;

    public DataSeeder(VesselRepository vesselRepo, SpeciesRepository speciesRepo,
                      SeaAreaRepository areaRepo, FishingSeasonRepository seasonRepo,
                      QuotaBalanceRepository balanceRepo, QuotaService quotaService) {
        this.vesselRepo = vesselRepo;
        this.speciesRepo = speciesRepo;
        this.areaRepo = areaRepo;
        this.seasonRepo = seasonRepo;
        this.balanceRepo = balanceRepo;
        this.quotaService = quotaService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        vesselRepo.findByCode("FV-MARLIN").orElseGet(() -> vesselRepo.save(new Vessel(
                "FV-MARLIN", "蓝枪鱼号（虚构）", "北镜港",
                "虚构许可证 F-DEMO-M：仅可在 SFA-A1 捕捞月鳞银鳕与红鳍石鲷（演示用，非真实许可）")));
        vesselRepo.findByCode("FV-ALBATROSS").orElseGet(() -> vesselRepo.save(new Vessel(
                "FV-ALBATROSS", "信天翁号（虚构）", "北镜港",
                "虚构许可证 F-DEMO-A：SFA-A1 月鳞银鳕/红鳍石鲷、SFA-B2 雾隐鳕（演示用，非真实许可）")));
        vesselRepo.findByCode("FV-KELP").orElseGet(() -> vesselRepo.save(new Vessel(
                "FV-KELP", "海带号（虚构）", "星砂港",
                "虚构许可证 F-DEMO-K：仅可在 SFA-B2 捕捞雾隐鳕（演示用，非真实许可）")));

        speciesRepo.findByCode("MOK").orElseGet(() -> speciesRepo.save(new Species(
                "MOK", "月鳞银鳕（虚构物种）",
                "虚构规则：仅 SFA-A1，季节 S2026；网目 ≥120mm（仅演示，无法律效力）")));
        speciesRepo.findByCode("RUBYFIN").orElseGet(() -> speciesRepo.save(new Species(
                "RUBYFIN", "红鳍石鲷（虚构物种）",
                "虚构规则：仅 SFA-A1，季节 S2026；副渔获占比 ≤5%（仅演示）")));
        speciesRepo.findByCode("GHOSTHAKE").orElseGet(() -> speciesRepo.save(new Species(
                "GHOSTHAKE", "雾隐鳕（虚构物种）",
                "虚构规则：仅 SFA-B2，季节 S2026（仅演示）")));

        areaRepo.findByCode("SFA-A1").orElseGet(() -> areaRepo.save(
                new SeaArea("SFA-A1", "北镜湾（虚构海区）")));
        areaRepo.findByCode("SFA-B2").orElseGet(() -> areaRepo.save(
                new SeaArea("SFA-B2", "星砂浅滩（虚构海区）")));

        seasonRepo.findByCode("S2026").orElseGet(() -> seasonRepo.save(
                new FishingSeason("S2026", LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 12, 31))));

        // Opening allocations (posted through the service so each one creates
        // an append-only ALLOCATION ledger entry).
        if (balanceRepo.count() == 0) {
            allocate("FV-MARLIN", "MOK", "SFA-A1", "S2026", "1000.000", "开季分配");
            allocate("FV-MARLIN", "RUBYFIN", "SFA-A1", "S2026", "300.000", "开季分配");
            allocate("FV-ALBATROSS", "MOK", "SFA-A1", "S2026", "150.000", "开季分配");
            allocate("FV-ALBATROSS", "RUBYFIN", "SFA-A1", "S2026", "80.000", "开季分配");
            allocate("FV-ALBATROSS", "GHOSTHAKE", "SFA-B2", "S2026", "60.000", "开季分配");
            allocate("FV-KELP", "GHOSTHAKE", "SFA-B2", "S2026", "500.000", "开季分配");
        }
    }

    private void allocate(String vessel, String species, String area, String season,
                          String kg, String note) {
        quotaService.allocate(new AllocationRequest(vessel, species, area, season,
                new BigDecimal(kg), note));
    }
}
