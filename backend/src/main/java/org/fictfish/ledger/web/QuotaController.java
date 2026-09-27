package org.fictfish.ledger.web;

import jakarta.validation.Valid;
import org.fictfish.ledger.repo.*;
import org.fictfish.ledger.service.QuotaService;
import org.fictfish.ledger.service.ViewAssembler;
import org.fictfish.ledger.web.dto.Requests.*;
import org.fictfish.ledger.web.dto.Views;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class QuotaController {

    private final QuotaService quotaService;
    private final QuotaBalanceRepository balanceRepo;
    private final VoyageRepository voyageRepo;
    private final ViewAssembler assembler;

    public QuotaController(QuotaService quotaService, QuotaBalanceRepository balanceRepo,
                           VoyageRepository voyageRepo, ViewAssembler assembler) {
        this.quotaService = quotaService;
        this.balanceRepo = balanceRepo;
        this.voyageRepo = voyageRepo;
        this.assembler = assembler;
    }

    // ---- balances & trace

    @GetMapping("/balances")
    public List<Views.BalanceView> balances(
            @RequestParam(required = false) Long vesselId,
            @RequestParam(required = false) Long speciesId,
            @RequestParam(required = false) Long areaId,
            @RequestParam(required = false) Long seasonId) {
        return assembler.balances(
                balanceRepo.search(vesselId, speciesId, areaId, seasonId));
    }

    /** Drill-down from a balance: ledger entries, voyages and transfers. */
    @GetMapping("/balances/{id}/trace")
    public Views.TraceView trace(@PathVariable Long id) {
        return quotaService.trace(id);
    }

    // ---- allocations / voyages / landings / transfers

    @PostMapping("/allocations")
    public Views.BalanceView allocate(@Valid @RequestBody AllocationRequest req) {
        return quotaService.allocate(req);
    }

    @PostMapping("/voyages")
    public QuotaService.VoyageDeclareResult declareVoyage(
            @Valid @RequestBody VoyageRequest req) {
        return quotaService.declareVoyage(req);
    }

    @GetMapping("/voyages")
    public List<Views.VoyageView> voyages() {
        return assembler.voyages(voyageRepo.findAllWithItems());
    }

    @PostMapping("/landings")
    public QuotaService.LandingResult recordLanding(@Valid @RequestBody LandingRequest req) {
        return quotaService.recordLanding(req);
    }

    @PostMapping("/transfers")
    public Views.TransferView transfer(@Valid @RequestBody TransferRequest req) {
        return quotaService.transfer(req);
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP",
                "notice", "Fictional data & rules only — not a real fishing permit.");
    }
}
