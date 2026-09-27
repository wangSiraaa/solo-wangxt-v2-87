package com.example.quota.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Request / response DTOs for the quota workbench API. All weights are BigDecimal kg. */
public final class Dtos {
    private Dtos() {}

    public record DeclareLine(String speciesCode, BigDecimal estimatedQty) {}

    public record DeclareVoyageRequest(
            String voyageNo, String vesselCode, String areaCode, String seasonCode,
            LocalDate departsOn, java.util.List<DeclareLine> lines) {}

    public record LandingRequest(
            String voucherNo, String voyageNo, String speciesCode, BigDecimal landedQty) {}

    public record TransferRequest(
            String transferNo, String speciesCode, String areaCode, String seasonCode,
            String fromVesselCode, String toVesselCode, BigDecimal qty,
            LocalDate effectiveFrom, LocalDate effectiveTo, String memo) {}

    public record IssueRequest(
            String vesselCode, String speciesCode, String areaCode, String seasonCode,
            BigDecimal qty, String memo) {}

    public record Species(String code, String name, String note) {}
    public record Area(String code, String name, String note) {}
    public record Season(String code, String name, LocalDate startsOn, LocalDate endsOn) {}
    public record Vessel(String code, String name, String homePort) {}
    public record PermitKey(String speciesCode, String areaCode, String seasonCode) {}

    public record AccountView(
            long id, String vesselCode, String speciesCode, String areaCode, String seasonCode,
            BigDecimal issuedQty, BigDecimal reservedQty, BigDecimal landedQty,
            BigDecimal transferredInQty, BigDecimal transferredOutQty, BigDecimal availableQty) {}

    public record LedgerEntryView(
            long id, long accountId, String eventType, BigDecimal amount,
            BigDecimal balanceAfter, LocalDateTime eventDate,
            String refType, Long refId, String memo) {}

    public record DeclarationLineView(
            String speciesCode, BigDecimal estimatedQty, BigDecimal landedQty,
            BigDecimal reservedQty, BigDecimal varianceQty, boolean settled) {}

    public record LandingView(
            long id, String voucherNo, long voyageId, String speciesCode,
            String areaCode, String seasonCode, BigDecimal landedQty, LocalDateTime recordedAt) {}

    public record VoyageView(
            long id, String voyageNo, String vesselCode, String areaCode, String seasonCode,
            LocalDate departsOn, LocalDate returnsOn, String status,
            java.util.List<DeclarationLineView> lines,
            java.util.List<LandingView> landings) {}

    public record TransferView(
            long id, String transferNo, String speciesCode, String areaCode, String seasonCode,
            BigDecimal qty,
            long fromAccountId, String fromVesselCode,
            long toAccountId, String toVesselCode,
            LocalDate effectiveFrom, LocalDate effectiveTo, String status,
            String memo, LocalDateTime createdAt) {}
}
