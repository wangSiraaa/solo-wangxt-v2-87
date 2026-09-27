package org.fictfish.ledger.bootstrap;

import org.fictfish.ledger.repo.VoyageRepository;
import org.fictfish.ledger.service.QuotaService;
import org.fictfish.ledger.web.ApiException;
import org.fictfish.ledger.web.dto.Requests.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Optional end-to-end demo scenario (enable with profile "demo").
 * Posts fictional voyages/landings/transfers and deliberately attempts the
 * illegal operations (cross-dimension transfer, duplicate certificate,
 * over-quota landing) to show they are rejected. All data is fictional.
 */
@Component
@Order(2)
public class DemoScenarioRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoScenarioRunner.class);

    private final QuotaService quotaService;
    private final VoyageRepository voyageRepo;
    private final org.springframework.core.env.Environment env;

    public DemoScenarioRunner(QuotaService quotaService, VoyageRepository voyageRepo,
                              org.springframework.core.env.Environment env) {
        this.quotaService = quotaService;
        this.voyageRepo = voyageRepo;
        this.env = env;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!env.matchesProfiles("demo") || voyageRepo.count() > 0) {
            return;
        }
        log.info("===== 开始写入虚构演示场景（非真实捕捞许可）=====");

        // 1) 预计 > 实捕：信天翁号 V-2601 申报 MOK 100 + RUBYFIN 40，
        //    实捕 MOK 80 / RUBYFIN 30，差额 +20 / +10 在航次视图中展示。
        var declare1 = quotaService.declareVoyage(new VoyageRequest(
                "V-2601", "FV-ALBATROSS", "S2026",
                OffsetDateTime.of(2026, 3, 2, 6, 0, 0, 0, ZoneOffset.UTC),
                "演示航次：预计大于实捕",
                List.of(
                        new VoyageItemRequest("MOK", "SFA-A1", new BigDecimal("100.000")),
                        new VoyageItemRequest("RUBYFIN", "SFA-A1", new BigDecimal("40.000")))));
        declare1.warnings().forEach(w -> log.info("[V-2601 申报预警] {}", w));
        quotaService.recordLanding(new LandingRequest(
                "LC-2601-A", "V-2601",
                OffsetDateTime.of(2026, 3, 10, 14, 0, 0, 0, ZoneOffset.UTC),
                "北镜港",
                List.of(
                        new LandingItemRequest("MOK", "SFA-A1", new BigDecimal("80.000")),
                        new LandingItemRequest("RUBYFIN", "SFA-A1", new BigDecimal("30.000")))));
        log.info("[V-2601] 实捕 80/30 < 预计 100/40，预计用量差额已释放，未多扣余额");

        // 2) 同船两次卸货：蓝枪鱼号 V-2602 一张申报对应两张卸货凭证。
        quotaService.declareVoyage(new VoyageRequest(
                "V-2602", "FV-MARLIN", "S2026",
                OffsetDateTime.of(2026, 4, 5, 5, 30, 0, 0, ZoneOffset.UTC),
                "演示航次：同船两次卸货",
                List.of(new VoyageItemRequest("MOK", "SFA-A1",
                        new BigDecimal("300.000")))));
        quotaService.recordLanding(new LandingRequest(
                "LC-2602-A", "V-2602",
                OffsetDateTime.of(2026, 4, 12, 9, 0, 0, 0, ZoneOffset.UTC),
                "北镜港",
                List.of(new LandingItemRequest("MOK", "SFA-A1",
                        new BigDecimal("180.000")))));
        quotaService.recordLanding(new LandingRequest(
                "LC-2602-B", "V-2602",
                OffsetDateTime.of(2026, 4, 15, 16, 0, 0, 0, ZoneOffset.UTC),
                "星砂港",
                List.of(new LandingItemRequest("MOK", "SFA-A1",
                        new BigDecimal("100.000")))));
        log.info("[V-2602] 两次卸货 180 + 100 = 280 kg，分两条 LANDING 流水扣减");

        // 3) 调拨（配对流水 + 生效期间）：海带号 -> 信天翁号，GHOSTHAKE/SFA-B2。
        quotaService.transfer(new TransferRequest(
                "TR-2603-01", "FV-KELP", "FV-ALBATROSS",
                "GHOSTHAKE", "SFA-B2", "S2026",
                new BigDecimal("100.000"),
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 10, 31),
                "演示调拨：同物种同海区同季节，配对扣补"));
        log.info("[TR-2603-01] 调出/调入各一条流水，双方余额可见来源去向");

        // 4) 两个航次额度不足：信天翁号 MOK 在 V-2601 后余 70，V-2604 扣 50 后余 20；
        //    V-2605 再卸 30 必须被拒。
        quotaService.declareVoyage(new VoyageRequest(
                "V-2604", "FV-ALBATROSS", "S2026",
                OffsetDateTime.of(2026, 5, 4, 6, 0, 0, 0, ZoneOffset.UTC),
                "演示航次：第一个航次",
                List.of(new VoyageItemRequest("MOK", "SFA-A1",
                        new BigDecimal("50.000")))));
        quotaService.recordLanding(new LandingRequest(
                "LC-2604-A", "V-2604",
                OffsetDateTime.of(2026, 5, 11, 12, 0, 0, 0, ZoneOffset.UTC),
                "北镜港",
                List.of(new LandingItemRequest("MOK", "SFA-A1",
                        new BigDecimal("50.000")))));
        quotaService.declareVoyage(new VoyageRequest(
                "V-2605", "FV-ALBATROSS", "S2026",
                OffsetDateTime.of(2026, 5, 18, 6, 0, 0, 0, ZoneOffset.UTC),
                "演示航次：第二个航次（预计即预警余额不足）",
                List.of(new VoyageItemRequest("MOK", "SFA-A1",
                        new BigDecimal("30.000")))));
        try {
            quotaService.recordLanding(new LandingRequest(
                    "LC-2605-A", "V-2605",
                    OffsetDateTime.of(2026, 5, 25, 12, 0, 0, 0, ZoneOffset.UTC),
                    "北镜港",
                    List.of(new LandingItemRequest("MOK", "SFA-A1",
                            new BigDecimal("30.000")))));
            log.warn("[V-2605] 未预期：额度不足的卸货竟然通过了");
        } catch (ApiException ex) {
            log.info("[V-2605 已拒绝 code={}] {}", ex.getCode(), ex.getMessage());
        }

        // 5) 维度不匹配的调拨必须被拒（MOK/SFA-A1 调到只有 SFA-B2 账户的海带号）。
        try {
            quotaService.transfer(new TransferRequest(
                    "TR-BAD-DIM", "FV-MARLIN", "FV-KELP",
                    "MOK", "SFA-A1", "S2026",
                    new BigDecimal("10.000"),
                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 30),
                    "应被拒绝：物种/海区不匹配"));
            log.warn("[TR-BAD-DIM] 未预期：维度不匹配的调拨竟然通过了");
        } catch (ApiException ex) {
            log.info("[TR-BAD-DIM 已拒绝 code={}] {}", ex.getCode(), ex.getMessage());
        }

        // 6) 重复录入同一卸货凭证不能重复扣减。
        try {
            quotaService.recordLanding(new LandingRequest(
                    "LC-2601-A", "V-2601",
                    OffsetDateTime.of(2026, 3, 10, 14, 0, 0, 0, ZoneOffset.UTC),
                    "北镜港",
                    List.of(new LandingItemRequest("MOK", "SFA-A1",
                            new BigDecimal("80.000")))));
            log.warn("[LC-2601-A 重复] 未预期：重复凭证竟然扣减成功");
        } catch (ApiException ex) {
            log.info("[LC-2601-A 重复已拒绝 code={}] {}", ex.getCode(), ex.getMessage());
        }

        log.info("===== 虚构演示场景写入完成 =====");
    }
}
