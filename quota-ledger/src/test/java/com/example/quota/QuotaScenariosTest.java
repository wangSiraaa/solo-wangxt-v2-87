package com.example.quota;

import com.example.quota.api.Dtos.*;
import com.example.quota.service.LedgerRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end verification on real PostgreSQL. Covers:
 *  1. estimated > actual catch: difference clearly shown, reserve released;
 *  2. two landings against the same voyage;
 *  3. two voyages where the second cannot reserve (insufficient quota);
 *  4. reposting the same landing voucher never double-deducts;
 *  5. actual > estimate needs free quota beyond the reservation;
 *  6. transfer posts source + destination legs with an effective period,
 *     balances stay traceable;
 *  7. non-matching species / area / season quota cannot be used;
 *  8. ledger is append-only and every balance is BigDecimal exact.
 */
@TestMethodOrder(OrderAnnotation.class)
class QuotaScenariosTest extends BaseIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    private static final String Q3 = "S2026-Q3";
    private static final String Q1 = "S2026-Q1";

    @Test
    @Order(1)
    @DisplayName("场景1: 预计 300kg > 实捕 250kg，差额 -50kg 清楚显示，重复凭证不重复扣减")
    void estimateGreaterThanActual_withDuplicateVoucher() {
        // account 1: V01 / YLJ / CL-N / S2026-Q3, issued 1000
        declare("TRIP-01", "V01", "CL-N", Q3, "2026-07-10",
                line("YLJ", "300.000"));

        AccountView afterDeclare = account(1);
        assertThat(afterDeclare.reservedQty()).isEqualByComparingTo("300.000");
        assertThat(afterDeclare.landedQty()).isEqualByComparingTo("0");
        assertThat(afterDeclare.availableQty()).isEqualByComparingTo("700.000");

        // first posting of the voucher deducts 250
        LandingView first = postLanding(new LandingRequest(
                "VCH-01", "TRIP-01", "YLJ", new BigDecimal("250.000")));
        assertThat(first.voucherNo()).isEqualTo("VCH-01");

        BigDecimal landedAfterFirst = jdbc.queryForObject(
                "SELECT landed_qty FROM quota_account WHERE id=1", BigDecimal.class);
        assertThat(landedAfterFirst).isEqualByComparingTo("250.000");

        // reposting the SAME voucher: same record returned, no second deduction
        LandingView again = postLanding(new LandingRequest(
                "VCH-01", "TRIP-01", "YLJ", new BigDecimal("250.000")));
        assertThat(again.id()).isEqualTo(first.id());
        BigDecimal landedAfterRepost = jdbc.queryForObject(
                "SELECT landed_qty FROM quota_account WHERE id=1", BigDecimal.class);
        assertThat(landedAfterRepost).isEqualByComparingTo("250.000"); // still 250, not 500
        Long voucherCount = jdbc.queryForObject(
                "SELECT count(*) FROM landing_voucher WHERE voucher_no='VCH-01'", Long.class);
        assertThat(voucherCount).isEqualTo(1L);

        VoyageView v = close("TRIP-01");
        DeclarationLineView line = v.lines().get(0);
        // difference must be clearly displayed: landed - estimated = -50
        assertThat(line.estimatedQty()).isEqualByComparingTo("300.000");
        assertThat(line.landedQty()).isEqualByComparingTo("250.000");
        assertThat(line.varianceQty()).isEqualByComparingTo("-50.000");
        assertThat(line.settled()).isTrue();

        AccountView after = account(1);
        assertThat(after.landedQty()).isEqualByComparingTo("250.000");
        assertThat(after.reservedQty()).isEqualByComparingTo("0");
        assertThat(after.availableQty()).isEqualByComparingTo("750.000"); // 1000-250
    }

    @Test
    @Order(2)
    @DisplayName("场景2: 同一航次两次卸货 120+70=190，低于预计 200，释放 10")
    void twoLandingsOnSameVoyage() {
        declare("TRIP-02", "V01", "CL-N", Q3, "2026-07-20",
                line("YLJ", "200.000"));
        postLanding(new LandingRequest("VCH-02", "TRIP-02", "YLJ", new BigDecimal("120.000")));
        postLanding(new LandingRequest("VCH-03", "TRIP-02", "YLJ", new BigDecimal("70.000")));

        VoyageView v = close("TRIP-02");
        DeclarationLineView line = v.lines().get(0);
        assertThat(line.landedQty()).isEqualByComparingTo("190.000");
        assertThat(line.varianceQty()).isEqualByComparingTo("-10.000");
        assertThat(v.landings()).hasSize(2);

        // scenario 1 + 2 combined on account 1
        assertThat(account(1).landedQty()).isEqualByComparingTo("440.000"); // 250 + 190
        assertThat(account(1).availableQty()).isEqualByComparingTo("560.000"); // 1000-440
    }

    @Test
    @Order(3)
    @DisplayName("场景3: 两个航次额度不足——第一个 400 成功，第二个 250 被拒（可用仅 200）")
    void secondVoyageInsufficient() {
        // account 2: V01 / CHX / CL-N / Q3, issued 600
        declare("TRIP-03", "V01", "CL-N", Q3, "2026-08-01",
                line("CHX", "400.000"));
        assertThat(account(2).availableQty()).isEqualByComparingTo("200.000");

        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/voyages",
                new HttpEntity<>(new DeclareVoyageRequest(
                        "TRIP-04", "V01", "CL-N", Q3, LocalDate.of(2026, 8, 5),
                        List.of(line("CHX", "250.000")))),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(String.valueOf(resp.getBody().get("message"))).contains("额度不足");

        // second voyage must not exist: declaration is atomic, nothing was reserved
        Long n = jdbc.queryForObject(
                "SELECT count(*) FROM voyage WHERE voyage_no='TRIP-04'", Long.class);
        assertThat(n).isZero();
        assertThat(account(2).reservedQty()).isEqualByComparingTo("400.000");
        assertThat(account(2).availableQty()).isEqualByComparingTo("200.000");
    }

    @Test
    @Order(4)
    @DisplayName("场景4: 实捕大于预计——超出部分必须有额外额度，否则拒绝")
    void actualGreaterThanEstimate() {
        // account 6: V03 / XHT / CL-S / Q3, issued 400
        declare("TRIP-05", "V03", "CL-S", Q3, "2026-08-10",
                line("XHT", "100.000"));
        // landing 150: 100 covered by reservation, 50 needs free quota (300 available) -> OK
        postLanding(new LandingRequest("VCH-04", "TRIP-05", "XHT", new BigDecimal("150.000")));
        close("TRIP-05");
        assertThat(account(6).landedQty()).isEqualByComparingTo("150.000");
        assertThat(account(6).availableQty()).isEqualByComparingTo("250.000");

        // A voyage whose estimate is tiny but actual exceeds estimate + available:
        // account 5: V03 / YLJ / CL-N / Q3, issued 500, avail 500
        declare("TRIP-06", "V03", "CL-N", Q3, "2026-08-15",
                line("YLJ", "50.000"));
        ResponseEntity<Map> resp = attemptLanding(
                "VCH-05", "TRIP-06", "YLJ", "600.000");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        // nothing deducted: landing failed atomically before any insert
        assertThat(account(5).landedQty()).isEqualByComparingTo("0");
        assertThat(account(5).availableQty()).isEqualByComparingTo("450.000"); // only reserve 50
    }

    @Test
    @Order(5)
    @DisplayName("场景5: 调拨记录来源/去向/生效期间，两侧余额与台账双向可追溯，不能只改一个余额")
    void transferPostsTwoLegsAndIsTraceable() {
        // account 3: V02/YLJ/CL-E/Q3 issued 800  ->  account 7: V01/YLJ/CL-E/Q3 issued 0
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        ResponseEntity<TransferView> resp = rest.postForEntity(
                base() + "/api/transfers",
                new HttpEntity<>(new TransferRequest(
                        "TRF-01", "YLJ", "CL-E", Q3,
                        "V02", "V01", new BigDecimal("150.000"),
                        from, to, "渔季内部协作调拨")),
                TransferView.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        TransferView t = resp.getBody();
        assertThat(t.fromAccountId()).isEqualTo(3L);
        assertThat(t.toAccountId()).isEqualTo(7L);
        assertThat(t.fromVesselCode()).isEqualTo("V02");
        assertThat(t.toVesselCode()).isEqualTo("V01");
        assertThat(t.effectiveFrom()).isEqualTo(from);
        assertThat(t.effectiveTo()).isEqualTo(to);

        assertThat(account(3).transferredOutQty()).isEqualByComparingTo("150.000");
        assertThat(account(3).availableQty()).isEqualByComparingTo("650.000");
        assertThat(account(7).transferredInQty()).isEqualByComparingTo("150.000");
        assertThat(account(7).availableQty()).isEqualByComparingTo("150.000");

        // drill-down from the SOURCE balance
        @SuppressWarnings("unchecked")
        Map<String, Object> srcTrace = rest.getForObject(
                base() + "/api/accounts/3/trace", Map.class);
        List<Map<String, Object>> srcLegs = (List<Map<String, Object>>) srcTrace.get("ledger");
        Map<String, Object> outLeg = srcLegs.get(srcLegs.size() - 1);
        assertThat(outLeg.get("eventType")).isEqualTo("TRANSFER_OUT");
        assertThat(new BigDecimal(outLeg.get("amount").toString())).isEqualByComparingTo("-150.000");
        assertThat(new BigDecimal(outLeg.get("balanceAfter").toString())).isEqualByComparingTo("650.000");
        List<Map<String, Object>> srcTransfers =
                (List<Map<String, Object>>) srcTrace.get("transfers");
        assertThat(srcTransfers).hasSize(1);
        assertThat(srcTransfers.get(0).get("transferNo")).isEqualTo("TRF-01");

        // drill-down from the DESTINATION balance
        @SuppressWarnings("unchecked")
        Map<String, Object> dstTrace = rest.getForObject(
                base() + "/api/accounts/7/trace", Map.class);
        List<Map<String, Object>> dstLegs = (List<Map<String, Object>>) dstTrace.get("ledger");
        Map<String, Object> inLeg = dstLegs.get(dstLegs.size() - 1);
        assertThat(inLeg.get("eventType")).isEqualTo("TRANSFER_IN");
        assertThat(new BigDecimal(inLeg.get("amount").toString())).isEqualByComparingTo("150.000");

        // balances reconcile globally: total issued = total available + consumed
        BigDecimal totalIssued = jdbc.queryForObject(
                "SELECT coalesce(sum(issued_qty),0) FROM quota_account", BigDecimal.class);
        BigDecimal totals = jdbc.queryForObject("""
                SELECT coalesce(sum(issued_qty + transferred_in_qty - transferred_out_qty
                                   - reserved_qty - landed_qty),0)
                FROM quota_account
                """, BigDecimal.class);
        BigDecimal consumed = jdbc.queryForObject(
                "SELECT coalesce(sum(landed_qty + reserved_qty),0) FROM quota_account",
                BigDecimal.class);
        assertThat(totals.add(consumed)).isEqualByComparingTo(totalIssued);
    }

    @Test
    @Order(6)
    @DisplayName("场景6: 不匹配物种/海区/季节的额度不能使用")
    void mismatchedSpeciesAreaSeasonRejected() {
        // YLJ has no permit in CL-S during Q3 (only XHT does): declaration rejected
        ResponseEntity<Map> areaMismatch = rest.postForEntity(
                base() + "/api/voyages",
                new HttpEntity<>(new DeclareVoyageRequest(
                        "TRIP-BAD-AREA", "V03", "CL-S", Q3, LocalDate.of(2026, 8, 20),
                        List.of(line("YLJ", "10.000")))),
                Map.class);
        assertThat(areaMismatch.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(String.valueOf(areaMismatch.getBody().get("message")))
                .contains("许可规则不允许");

        // CHX/CL-E is only permitted in S2026-Q1, not Q3
        ResponseEntity<Map> seasonMismatch = rest.postForEntity(
                base() + "/api/voyages",
                new HttpEntity<>(new DeclareVoyageRequest(
                        "TRIP-BAD-SEASON", "V02", "CL-E", Q3, LocalDate.of(2026, 8, 20),
                        List.of(line("CHX", "10.000")))),
                Map.class);
        assertThat(seasonMismatch.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(String.valueOf(seasonMismatch.getBody().get("message")))
                .contains("许可规则不允许");

        // V02 holds YLJ quota in CL-E (650 after transfer), it must NOT be
        // usable for its CL-N voyage: the CL-N account is a separate ledger.
        declare("TRIP-07", "V02", "CL-N", Q3, "2026-08-21",
                line("YLJ", "100.000")); // account 8 has 200 -> OK
        ResponseEntity<Map> overArea = attemptLanding(
                "VCH-06", "TRIP-07", "YLJ", "250.000"); // only 200 on the CL-N account
        assertThat(overArea.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(String.valueOf(overArea.getBody().get("message"))).contains("额度不足");
        assertThat(account(8).availableQty()).isEqualByComparingTo("100.000"); // reserve only

        // direct service-level check: undeclared species on a voyage cannot land
        ResponseEntity<Map> wrongSpecies = attemptLanding(
                "VCH-07", "TRIP-07", "XHT", "1.000");
        assertThat(wrongSpecies.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(String.valueOf(wrongSpecies.getBody().get("message"))).contains("未申报");
    }

    @Test
    @Order(7)
    @DisplayName("场景7: 调拨的生效期间必须落在季节内；超额调拨被拒")
    void transferRules() {
        ResponseEntity<Map> outsideSeason = rest.postForEntity(
                base() + "/api/transfers",
                new HttpEntity<>(new TransferRequest(
                        "TRF-BAD-DATE", "YLJ", "CL-E", Q3,
                        "V02", "V01", new BigDecimal("10.000"),
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 31), null)),
                Map.class);
        assertThat(outsideSeason.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(String.valueOf(outsideSeason.getBody().get("message"))).contains("季节");

        // V01/YLJ/CL-E (account 7) only holds 150: trying to send 999 out fails,
        // and neither balance must change.
        ResponseEntity<Map> tooMuch = rest.postForEntity(
                base() + "/api/transfers",
                new HttpEntity<>(new TransferRequest(
                        "TRF-BAD-QTY", "YLJ", "CL-E", Q3,
                        "V01", "V02", new BigDecimal("999.000"),
                        LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30), null)),
                Map.class);
        assertThat(tooMuch.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(account(7).availableQty()).isEqualByComparingTo("150.000");
        assertThat(account(3).availableQty()).isEqualByComparingTo("650.000");
    }

    @Test
    @Order(8)
    @DisplayName("场景8: 台账 append-only——UPDATE/DELETE 被 PostgreSQL 触发器拒绝")
    void ledgerIsAppendOnly() {
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE quota_ledger_entry SET amount = 999 WHERE id = 1"))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update(
                "DELETE FROM quota_ledger_entry WHERE id = 1"))
                .hasMessageContaining("append-only");
    }

    @Test
    @Order(9)
    @DisplayName("场景9: 从余额可查看航次（含卸货）与调拨记录")
    void traceFromBalanceShowsVoyagesAndTransfers() {
        @SuppressWarnings("unchecked")
        Map<String, Object> trace = rest.getForObject(
                base() + "/api/accounts/1/trace", Map.class);
        List<Map<String, Object>> voyages =
                (List<Map<String, Object>>) trace.get("voyages");
        assertThat(voyages).extracting(v -> v.get("voyageNo"))
                .contains("TRIP-01", "TRIP-02");
        Map<String, Object> trip1 = voyages.stream()
                .filter(v -> "TRIP-01".equals(v.get("voyageNo"))).findFirst().orElseThrow();
        List<Map<String, Object>> landings =
                (List<Map<String, Object>>) trip1.get("landings");
        assertThat(landings).hasSize(1);
        assertThat(landings.get(0).get("voucherNo")).isEqualTo("VCH-01");

        List<Map<String, Object>> ledgerEntries =
                (List<Map<String, Object>>) trace.get("ledger");
        assertThat(ledgerEntries).extracting(e -> e.get("eventType"))
                .contains("ISSUE", "RESERVE", "LANDING", "SETTLE_RESERVE");
    }

    // --------------------------------------------------------------- helpers

    private String base() {
        return "http://localhost:" + port;
    }

    private static DeclareLine line(String species, String qty) {
        return new DeclareLine(species, new BigDecimal(qty));
    }

    private void declare(String no, String vessel, String area, String season,
                         String date, DeclareLine... lines) {
        DeclareVoyageRequest req = new DeclareVoyageRequest(
                no, vessel, area, season, LocalDate.parse(date), List.of(lines));
        ResponseEntity<Map> resp = rest.postForEntity(
                base() + "/api/voyages", new HttpEntity<>(req), Map.class);
        if (resp.getStatusCode().isError()) {
            throw new LedgerRuleException("declare failed: " + resp.getBody());
        }
    }

    private LandingView postLanding(LandingRequest req) {
        ResponseEntity<LandingView> resp = rest.postForEntity(
                base() + "/api/landings", new HttpEntity<>(req), LandingView.class);
        if (resp.getStatusCode().isError()) {
            throw new LedgerRuleException("landing failed: status=" + resp.getStatusCode());
        }
        return resp.getBody();
    }

    private ResponseEntity<Map> attemptLanding(String voucher, String trip,
                                               String species, String qty) {
        return rest.postForEntity(
                base() + "/api/landings",
                new HttpEntity<>(new LandingRequest(
                        voucher, trip, species, new BigDecimal(qty))),
                Map.class);
    }

    private VoyageView close(String no) {
        return rest.postForObject(base() + "/api/voyages/" + no + "/close",
                null, VoyageView.class);
    }

    private AccountView account(long id) {
        return rest.getForObject(base() + "/api/accounts/" + id, AccountView.class);
    }
}
