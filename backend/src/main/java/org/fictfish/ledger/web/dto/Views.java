package org.fictfish.ledger.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** All read-model views of the quota workbench API. */
public final class Views {

    private Views() {
    }

    public record VesselView(Long id, String code, String name, String homePort, String permitNote) {
    }

    public record SpeciesView(Long id, String code, String commonName, String ruleNote) {
    }

    public record AreaView(Long id, String code, String name) {
    }

    public record SeasonView(Long id, String code, LocalDate seasonStart, LocalDate seasonEnd) {
    }

    /** One balance row. forecastOccupiedKg = sum of estimated - already landed,
     *  forecastRemainingKg = remaining - outstanding estimates (may be negative). */
    public record BalanceView(Long id, String vesselCode, String vesselName,
                              String speciesCode, String speciesName,
                              String areaCode, String areaName,
                              String seasonCode, LocalDate seasonStart, LocalDate seasonEnd,
                              BigDecimal remainingKg,
                              BigDecimal forecastOccupiedKg,
                              BigDecimal forecastRemainingKg,
                              OffsetDateTime updatedAt) {
    }

    public record LedgerView(Long id, Long balanceId, String entryType,
                             BigDecimal deltaKg, BigDecimal remainingKg,
                             OffsetDateTime entryDate, String refDoc,
                             Long landingId, Long transferId,
                             String counterpartyVessel, String note) {
    }

    /** Voyage line with estimated / landed / difference weights (BigDecimal, kg). */
    public record VoyageItemView(String speciesCode, String speciesName,
                                 String areaCode, String areaName,
                                 BigDecimal estimatedKg,
                                 BigDecimal landedKg,
                                 BigDecimal differenceKg,
                                 boolean fullyLanded) {
    }

    public record LandingView(Long id, String certificateNo, OffsetDateTime landedAt,
                              String portName, OffsetDateTime recordedAt,
                              List<LandedLine> lines) {
        public record LandedLine(String speciesCode, String areaCode, BigDecimal actualKg) {
        }
    }

    public record VoyageView(Long id, String voyageNo, String vesselCode, String vesselName,
                             String seasonCode, OffsetDateTime departedAt, String status,
                             String note, List<VoyageItemView> items,
                             List<LandingView> landings) {
    }

    public record TransferView(Long id, String transferNo,
                               String sourceVesselCode, String destVesselCode,
                               String speciesCode, String areaCode, String seasonCode,
                               BigDecimal amountKg,
                               LocalDate effectiveFrom, LocalDate effectiveTo,
                               String status, OffsetDateTime createdAt, String note) {
    }

    /** Drill-down from a balance: the append-only ledger plus voyages and
     *  transfers that touched the balance. */
    public record TraceView(BalanceView balance,
                            List<LedgerView> ledger,
                            List<VoyageView> voyages,
                            List<TransferView> transfers) {
    }
}
