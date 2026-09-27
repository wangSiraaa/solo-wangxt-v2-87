package com.example.quota.api;

import com.example.quota.api.Dtos.*;
import com.example.quota.service.LedgerService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for the fictional quota workbench.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin
public class QuotaController {

    private final LedgerService ledger;
    private final JdbcTemplate jdbc;

    public QuotaController(LedgerService ledger, JdbcTemplate jdbc) {
        this.ledger = ledger;
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------- reference data

    @GetMapping("/reference")
    public Map<String, Object> reference() {
        return Map.of(
                "species", jdbc.query("SELECT code, name, note FROM species ORDER BY code",
                        (rs, n) -> new Species(rs.getString("code"), rs.getString("name"),
                                rs.getString("note"))),
                "areas", jdbc.query("SELECT code, name, note FROM sea_area ORDER BY code",
                        (rs, n) -> new Area(rs.getString("code"), rs.getString("name"),
                                rs.getString("note"))),
                "seasons", jdbc.query(
                        "SELECT code, name, starts_on, ends_on FROM season ORDER BY code",
                        (rs, n) -> new Season(rs.getString("code"), rs.getString("name"),
                                rs.getDate("starts_on").toLocalDate(),
                                rs.getDate("ends_on").toLocalDate())),
                "vessels", jdbc.query("SELECT code, name, home_port FROM vessel ORDER BY code",
                        (rs, n) -> new Vessel(rs.getString("code"), rs.getString("name"),
                                rs.getString("home_port"))),
                "permits", jdbc.query(
                        "SELECT species_code, area_code, season_code FROM species_permit "
                                + "ORDER BY species_code, area_code, season_code",
                        (rs, n) -> new PermitKey(rs.getString("species_code"),
                                rs.getString("area_code"), rs.getString("season_code"))),
                "disclaimer",
                "虚构演示系统：物种、许可规则与额度均为虚构，不连接监管系统，不构成真实捕捞许可。");
    }

    // ------------------------------------------------------------- accounts

    @GetMapping("/accounts")
    public List<AccountView> accounts(
            @RequestParam(required = false) String vesselCode,
            @RequestParam(required = false) String speciesCode,
            @RequestParam(required = false) String areaCode,
            @RequestParam(required = false) String seasonCode) {
        return ledger.accounts(vesselCode, speciesCode, areaCode, seasonCode);
    }

    @GetMapping("/accounts/{id}")
    public AccountView account(@PathVariable long id) {
        return ledger.getAccount(id);
    }

    /** Drill-down from a balance: voyages, transfers and ledger entries touching it. */
    @GetMapping("/accounts/{id}/trace")
    public Map<String, Object> trace(@PathVariable long id) {
        AccountView acct = ledger.getAccount(id);
        return Map.of(
                "account", acct,
                "ledger", ledger.ledger(id),
                "voyages", ledger.voyages(acct.vesselCode()).stream()
                        .filter(v -> v.lines().stream()
                                .anyMatch(l -> l.speciesCode().equals(acct.speciesCode()))
                                && v.areaCode().equals(acct.areaCode())
                                && v.seasonCode().equals(acct.seasonCode()))
                        .toList(),
                "transfers", ledger.transfers(id));
    }

    @PostMapping("/accounts/issue")
    public AccountView issue(@RequestBody IssueRequest req) {
        return ledger.issueQuota(req);
    }

    // -------------------------------------------------------------- ledger

    @GetMapping("/ledger")
    public List<LedgerEntryView> ledger(@RequestParam(required = false) Long accountId) {
        return ledger.ledger(accountId);
    }

    // ------------------------------------------------------------- voyages

    @GetMapping("/voyages")
    public List<VoyageView> voyages(@RequestParam(required = false) String vesselCode) {
        return ledger.voyages(vesselCode);
    }

    @GetMapping("/voyages/{voyageNo}")
    public VoyageView voyage(@PathVariable String voyageNo) {
        return ledger.voyageByNo(voyageNo);
    }

    @PostMapping("/voyages")
    public VoyageView declare(@RequestBody DeclareVoyageRequest req) {
        return ledger.declareVoyage(req);
    }

    @PostMapping("/voyages/{voyageNo}/close")
    public VoyageView close(@PathVariable String voyageNo) {
        return ledger.closeVoyage(voyageNo);
    }

    // ------------------------------------------------------------ landings

    @PostMapping("/landings")
    public LandingView landing(@RequestBody LandingRequest req) {
        return ledger.recordLanding(req);
    }

    // ------------------------------------------------------------ transfers

    @GetMapping("/transfers")
    public List<TransferView> transfers(@RequestParam(required = false) Long accountId) {
        return ledger.transfers(accountId);
    }

    @PostMapping("/transfers")
    public TransferView transfer(@RequestBody TransferRequest req) {
        return ledger.transferQuota(req);
    }
}
