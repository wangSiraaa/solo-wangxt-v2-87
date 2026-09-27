package org.fictfish.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Smoke test for the REST layer (JSON over HTTP, BigDecimal weights). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QuotaControllerHttpTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void referenceData_balances_andDuplicateLandingOverHttp() {
        assertThat(rest.getForEntity(url("/api/health"), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity(url("/api/reference/vessels"), List.class)
                .getBody()).isNotEmpty();

        // declare voyage via JSON and read forecasts on balances
        var voyageReq = Map.of(
                "voyageNo", "V-HTTP-1",
                "vesselCode", "FV-ALBATROSS",
                "seasonCode", "S2026",
                "departedAt", "2026-07-01T06:00:00Z",
                "note", "HTTP 冒烟测试",
                "items", List.of(Map.of(
                        "speciesCode", "RUBYFIN",
                        "areaCode", "SFA-A1",
                        "estimatedKg", new BigDecimal("20.000"))));
        ResponseEntity<Map> declared =
                rest.postForEntity(url("/api/voyages"), voyageReq, Map.class);
        assertThat(declared.getStatusCode()).isEqualTo(HttpStatus.OK);

        var landingReq = Map.of(
                "certificateNo", "LC-HTTP-1",
                "voyageNo", "V-HTTP-1",
                "landedAt", "2026-07-08T12:00:00Z",
                "portName", "北镜港",
                "items", List.of(Map.of(
                        "speciesCode", "RUBYFIN",
                        "areaCode", "SFA-A1",
                        "actualKg", new BigDecimal("20.000"))));
        assertThat(rest.postForEntity(url("/api/landings"), landingReq, Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> dup =
                rest.postForEntity(url("/api/landings"), landingReq, Map.class);
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((String) dup.getBody().get("code")).isEqualTo("DUPLICATE");

        // drill-down trace endpoint is reachable for the balance list
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> balances =
                rest.getForEntity(url("/api/balances"), List.class).getBody();
        Object balanceId = balances.get(0).get("id");
        ResponseEntity<Map> trace =
                rest.getForEntity(url("/api/balances/" + balanceId + "/trace"), Map.class);
        assertThat(trace.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(trace.getBody()).containsKeys("balance", "ledger", "voyages",
                "transfers");
    }
}
