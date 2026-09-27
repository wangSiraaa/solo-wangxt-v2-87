package org.fictfish.ledger.web;

import org.fictfish.ledger.repo.*;
import org.fictfish.ledger.web.dto.Views;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reference")
public class ReferenceDataController {

    private final VesselRepository vesselRepo;
    private final SpeciesRepository speciesRepo;
    private final SeaAreaRepository areaRepo;
    private final FishingSeasonRepository seasonRepo;

    public ReferenceDataController(VesselRepository vesselRepo, SpeciesRepository speciesRepo,
                                   SeaAreaRepository areaRepo,
                                   FishingSeasonRepository seasonRepo) {
        this.vesselRepo = vesselRepo;
        this.speciesRepo = speciesRepo;
        this.areaRepo = areaRepo;
        this.seasonRepo = seasonRepo;
    }

    @GetMapping("/vessels")
    public List<Views.VesselView> vessels() {
        return vesselRepo.findAll().stream()
                .map(v -> new Views.VesselView(v.getId(), v.getCode(), v.getName(),
                        v.getHomePort(), v.getPermitNote()))
                .toList();
    }

    @GetMapping("/species")
    public List<Views.SpeciesView> species() {
        return speciesRepo.findAll().stream()
                .map(s -> new Views.SpeciesView(s.getId(), s.getCode(), s.getCommonName(),
                        s.getRuleNote()))
                .toList();
    }

    @GetMapping("/areas")
    public List<Views.AreaView> areas() {
        return areaRepo.findAll().stream()
                .map(a -> new Views.AreaView(a.getId(), a.getCode(), a.getName()))
                .toList();
    }

    @GetMapping("/seasons")
    public List<Views.SeasonView> seasons() {
        return seasonRepo.findAll().stream()
                .map(s -> new Views.SeasonView(s.getId(), s.getCode(), s.getSeasonStart(),
                        s.getSeasonEnd()))
                .toList();
    }
}
