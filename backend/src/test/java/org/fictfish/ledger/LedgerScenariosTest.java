package org.fictfish.ledger;

import org.fictfish.ledger.domain.*;
import org.fictfish.ledger.repo.*;
import org.fictfish.ledger.service.QuotaService;
import org.fictfish.ledger.web.ApiException;
import org.fictfish.ledger.web.dto.Requests.*;
import org.fictfish.ledger.web.dto.Views;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * End-to-end ledger rule tests against an in-memory PostgreSQL-mode database.
 * Each test rolls back, starting from the fictional seed allocations:
 * FV-MARLIN    MOK/SFA-A1=1000, RUBYFIN/SFA-A1=300
 * FV-ALBATROSS MOK/SFA-A1=150,  RUBYFIN/SFA-A1=80, GHOSTHAKE/SFA-B2=60
 * FV-KELP      GHOSTHAKE/SFA-B2=500
 */
@SpringBootTest
@Transactional
class LedgerScenariosTest {

    @Autowired
    QuotaService quotaService;
    @Autowired
    QuotaBalanceRepository balanceRepo;
    @Autowired
    LandingRepository landingRepo;
    @Autowired
    QuotaTransferRepository transferRepo;
    @Autowired
    VesselRepository vesselRepo;
    @Autowired
    SpeciesRepository speciesRepo;
    @Autowired
    SeaAreaRepository areaRepo;
    @Autowired
    FishingSeasonRepository seasonRepo;

    private static VoyageItemRequest vi(String sp, String area, String kg) {
        return new VoyageItemRequest(sp, area, new BigDecimal(kg));
    }

    private static LandingItemRequest li(String sp, String area, String kg) {
        return new LandingItemRequest(sp, area, new BigDecimal(kg));
    }

    private static OffsetDateTime dt(int day) {
        return OffsetDateTime.of(2026, 6, day, 8, 0, 0, 0, ZoneOffset.UTC);
    }

    private QuotaBalance balance(String vesselCode, String speciesCode,
                                 String areaCode, String seasonCode) {
        Long v = vesselRepo.findByCode(vesselCode).orElseThrow().getId();
        Long s = speciesRepo.findByCode(speciesCode).orElseThrow().getId();
        Long a = areaRepo.findByCode(areaCode).orElseThrow().getId();
        Long se = seasonRepo.findByCode(seasonCode).orElseThrow().getId();
        return balanceRepo.findByVesselIdAndSpeciesIdAndAreaIdAndSeasonId(v, s, a, se)
                .orElseThrow();
    }

    // ---------------- 预计 > 实捕 + 来源追溯 ----------------

    @Test
    void estimatedGreaterThanActual_showsDifferenceAndDebitsOnlyActual() {
        // seed balance FV-MARLIN MOK/SFA-A1 = 1000
        quotaService.declareVoyage(new VoyageRequest(
                "T-EST-1", "FV-MARLIN", "S2026", dt(1), "预计大于实捕",
                List.of(vi("MOK", "SFA-A1", "300.000"))));
        var result = quotaService.recordLanding(new LandingRequest(
                "LC-EST-1", "T-EST-1", dt(5), "北镜港",
                List.of(li("MOK", "SFA-A1", "250.000"))));

        // difference = estimated - landed is clearly shown
        Views.VoyageItemView line = result.voyage().items().get(0);
        assertThat(line.estimatedKg()).isEqualByComparingTo("300.000");
        assertThat(line.landedKg()).isEqualByComparingTo("250.000");
        assertThat(line.differenceKg()).isEqualByComparingTo("50.000");

        // only the ACTUAL 250 was debited, not the 300 estimate
        assertThat(balance("FV-MARLIN", "MOK", "SFA-A1", "S2026").getRemainingKg())
                .isEqualByComparingTo("750.000");

        // source trace: seed allocation + landing entry, landing carries cert
        var trace = quotaService.trace(
                balance("FV-MARLIN", "MOK", "SFA-A1", "S2026").getId());
        assertThat(trace.ledger()).hasSize(2);
        assertThat(trace.ledger()).extracting(Views.LedgerView::entryType)
                .containsExactly("ALLOCATION", "LANDING");
        assertThat(trace.ledger().get(1).refDoc()).isEqualTo("LC-EST-1");
        assertThat(trace.ledger().get(1).deltaKg()).isEqualByComparingTo("-250.000");
        assertThat(trace.voyages()).hasSize(1);
        assertThat(trace.voyages().get(0).voyageNo()).isEqualTo("T-EST-1");
    }

    // ---------------- 同船两次卸货 ----------------

    @Test
    void sameVesselTwoLandings_bothDebitAndBothShowOnTrace() {
        // seed balance FV-MARLIN RUBYFIN/SFA-A1 = 300
        quotaService.declareVoyage(new VoyageRequest(
                "T-TWO-1", "FV-MARLIN", "S2026", dt(2), "两次卸货",
                List.of(vi("RUBYFIN", "SFA-A1", "300.000"))));
        quotaService.recordLanding(new LandingRequest(
                "LC-TWO-A", "T-TWO-1", dt(6), "北镜港",
                List.of(li("RUBYFIN", "SFA-A1", "180.000"))));
        var second = quotaService.recordLanding(new LandingRequest(
                "LC-TWO-B", "T-TWO-1", dt(9), "星砂港",
                List.of(li("RUBYFIN", "SFA-A1", "100.000"))));

        assertThat(balance("FV-MARLIN", "RUBYFIN", "SFA-A1", "S2026").getRemainingKg())
                .isEqualByComparingTo("20.000"); // 300 - 180 - 100

        var voyage = second.voyage();
        assertThat(voyage.landings()).hasSize(2);
        assertThat(voyage.items().get(0).landedKg()).isEqualByComparingTo("280.000");
        assertThat(voyage.items().get(0).differenceKg()).isEqualByComparingTo("20.000");

        var trace = quotaService.trace(
                balance("FV-MARLIN", "RUBYFIN", "SFA-A1", "S2026").getId());
        assertThat(trace.ledger()).extracting(Views.LedgerView::entryType)
                .containsExactly("ALLOCATION", "LANDING", "LANDING");
        assertThat(trace.ledger()).extracting(Views.LedgerView::refDoc)
                .contains("LC-TWO-A", "LC-TWO-B");
    }

    // ---------------- 两个航次额度不足 ----------------

    @Test
    void twoVoyages_insufficientQuota_secondLandingRejectedBalanceUntouched() {
        // seed balance FV-ALBATROSS MOK/SFA-A1 = 150
        quotaService.declareVoyage(new VoyageRequest(
                "T-SHORT-1", "FV-ALBATROSS", "S2026", dt(3), "第一航次",
                List.of(vi("MOK", "SFA-A1", "100.000"))));
        quotaService.recordLanding(new LandingRequest(
                "LC-SHORT-1", "T-SHORT-1", dt(7), "北镜港",
                List.of(li("MOK", "SFA-A1", "100.000"))));

        // second voyage of 100 against only 50 remaining: declaration warns
        var declare = quotaService.declareVoyage(new VoyageRequest(
                "T-SHORT-2", "FV-ALBATROSS", "S2026", dt(10), "第二航次",
                List.of(vi("MOK", "SFA-A1", "100.000"))));
        assertThat(declare.warnings()).anyMatch(w -> w.contains("预计缺口 50.000"));

        assertThatThrownBy(() -> quotaService.recordLanding(new LandingRequest(
                "LC-SHORT-2", "T-SHORT-2", dt(14), "北镜港",
                List.of(li("MOK", "SFA-A1", "100.000")))))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo("QUOTA_EXCEEDED");

        // balance not changed, no extra LANDING ledger row, no landing stored
        assertThat(balance("FV-ALBATROSS", "MOK", "SFA-A1", "S2026").getRemainingKg())
                .isEqualByComparingTo("50.000");
        var trace = quotaService.trace(
                balance("FV-ALBATROSS", "MOK", "SFA-A1", "S2026").getId());
        assertThat(trace.ledger()).filteredOn(l -> l.entryType().equals("LANDING"))
                .hasSize(1);
        assertThat(landingRepo.findByCertificateNo("LC-SHORT-2")).isEmpty();
    }

    // ---------------- 重复卸货凭证 ----------------

    @Test
    void duplicateCertificate_isRejectedAndNeverDebitsTwice() {
        // seed balance FV-ALBATROSS RUBYFIN/SFA-A1 = 80
        quotaService.declareVoyage(new VoyageRequest(
                "T-DUP-1", "FV-ALBATROSS", "S2026", dt(4), "重复凭证",
                List.of(vi("RUBYFIN", "SFA-A1", "40.000"))));
        quotaService.recordLanding(new LandingRequest(
                "LC-DUP-1", "T-DUP-1", dt(8), "北镜港",
                List.of(li("RUBYFIN", "SFA-A1", "40.000"))));

        assertThatThrownBy(() -> quotaService.recordLanding(new LandingRequest(
                "LC-DUP-1", "T-DUP-1", dt(8), "北镜港",
                List.of(li("RUBYFIN", "SFA-A1", "40.000")))))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo("DUPLICATE");

        assertThat(balance("FV-ALBATROSS", "RUBYFIN", "SFA-A1", "S2026").getRemainingKg())
                .isEqualByComparingTo("40.000"); // still only debited once
        assertThat(landingRepo.findAll()).filteredOn(
                l -> l.getCertificateNo().equals("LC-DUP-1")).hasSize(1);
    }

    // ---------------- 调拨：配对流水 + 双向追溯 ----------------

    @Test
    void transfer_createsPairedEntriesTraceableFromBothSides() {
        // seed: FV-KELP GHOSTHAKE/B2 = 500, FV-ALBATROSS GHOSTHAKE/B2 = 60
        var view = quotaService.transfer(new TransferRequest(
                "TR-TEST-1", "FV-KELP", "FV-ALBATROSS",
                "GHOSTHAKE", "SFA-B2", "S2026", new BigDecimal("100.000"),
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 12, 31),
                "测试调拨"));

        assertThat(view.sourceVesselCode()).isEqualTo("FV-KELP");
        assertThat(view.destVesselCode()).isEqualTo("FV-ALBATROSS");
        assertThat(balance("FV-KELP", "GHOSTHAKE", "SFA-B2", "S2026").getRemainingKg())
                .isEqualByComparingTo("400.000");
        assertThat(balance("FV-ALBATROSS", "GHOSTHAKE", "SFA-B2", "S2026").getRemainingKg())
                .isEqualByComparingTo("160.000");

        // source side shows TRANSFER_OUT with the counterparty and transfer no
        var srcTrace = quotaService.trace(
                balance("FV-KELP", "GHOSTHAKE", "SFA-B2", "S2026").getId());
        var out = srcTrace.ledger().stream()
                .filter(l -> l.entryType().equals("TRANSFER_OUT")).findFirst().orElseThrow();
        assertThat(out.deltaKg()).isEqualByComparingTo("-100.000");
        assertThat(out.refDoc()).isEqualTo("TR-TEST-1");
        assertThat(out.counterpartyVessel()).isEqualTo("FV-ALBATROSS");
        assertThat(srcTrace.transfers()).hasSize(1);

        // destination side shows TRANSFER_IN pointing back to the source
        var dstTrace = quotaService.trace(
                balance("FV-ALBATROSS", "GHOSTHAKE", "SFA-B2", "S2026").getId());
        var in = dstTrace.ledger().stream()
                .filter(l -> l.entryType().equals("TRANSFER_IN")).findFirst().orElseThrow();
        assertThat(in.deltaKg()).isEqualByComparingTo("100.000");
        assertThat(in.counterpartyVessel()).isEqualTo("FV-KELP");
        assertThat(dstTrace.transfers()).hasSize(1);
        assertThat(dstTrace.transfers().get(0).transferNo()).isEqualTo("TR-TEST-1");
    }

    // ---------------- 维度不匹配：调拨与卸货均拒绝 ----------------

    @Test
    void mismatchedDimensions_cannotBeUsed() {
        // FV-KELP holds no MOK/SFA-A1 account at all
        assertThat(balanceRepo.findByVesselIdAndSpeciesIdAndAreaIdAndSeasonId(
                vesselRepo.findByCode("FV-KELP").orElseThrow().getId(),
                speciesRepo.findByCode("MOK").orElseThrow().getId(),
                areaRepo.findByCode("SFA-A1").orElseThrow().getId(),
                seasonRepo.findByCode("S2026").orElseThrow().getId())).isEmpty();

        assertThatThrownBy(() -> quotaService.transfer(new TransferRequest(
                "TR-BAD-1", "FV-MARLIN", "FV-KELP",
                "MOK", "SFA-A1", "S2026", new BigDecimal("10.000"),
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 12, 31),
                "维度不匹配")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo("INVALID");

        // landing line outside the voyage's declared species/area is rejected
        quotaService.declareVoyage(new VoyageRequest(
                "T-BAD-1", "FV-MARLIN", "S2026", dt(11), "错误海区卸货",
                List.of(vi("MOK", "SFA-A1", "50.000"))));
        assertThatThrownBy(() -> quotaService.recordLanding(new LandingRequest(
                "LC-BAD-1", "T-BAD-1", dt(15), "星砂港",
                List.of(li("GHOSTHAKE", "SFA-B2", "50.000")))))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo("INVALID");
    }

    // ---------------- 超额源方调拨被拒 ----------------

    @Test
    void transferBeyondSourceBalance_isRejected() {
        // seed FV-KELP GHOSTHAKE/B2 = 500; attempt to move 600
        assertThatThrownBy(() -> quotaService.transfer(new TransferRequest(
                "TR-OVER-1", "FV-KELP", "FV-ALBATROSS",
                "GHOSTHAKE", "SFA-B2", "S2026", new BigDecimal("600.000"),
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 12, 31),
                "超额调出")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo("QUOTA_EXCEEDED");
        assertThat(transferRepo.findByTransferNo("TR-OVER-1")).isEmpty();
        assertThat(balance("FV-KELP", "GHOSTHAKE", "SFA-B2", "S2026").getRemainingKg())
                .isEqualByComparingTo("500.000");
    }
}
