package org.fictfish.ledger.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** All request payloads of the quota workbench API. */
public final class Requests {

    private Requests() {
    }

    public record AllocationRequest(
            @NotBlank String vesselCode,
            @NotBlank String speciesCode,
            @NotBlank String areaCode,
            @NotBlank String seasonCode,
            @NotNull @Positive BigDecimal amountKg,
            String note) {
    }

    public record VoyageItemRequest(
            @NotBlank String speciesCode,
            @NotBlank String areaCode,
            @NotNull @Positive BigDecimal estimatedKg) {
    }

    public record VoyageRequest(
            @NotBlank String voyageNo,
            @NotBlank String vesselCode,
            @NotBlank String seasonCode,
            @NotNull OffsetDateTime departedAt,
            String note,
            @NotNull List<VoyageItemRequest> items) {
    }

    public record LandingItemRequest(
            @NotBlank String speciesCode,
            @NotBlank String areaCode,
            @NotNull @Positive BigDecimal actualKg) {
    }

    public record LandingRequest(
            @NotBlank String certificateNo,
            @NotBlank String voyageNo,
            @NotNull OffsetDateTime landedAt,
            @NotBlank String portName,
            @NotNull List<LandingItemRequest> items) {
    }

    public record TransferRequest(
            String transferNo,
            @NotBlank String sourceVesselCode,
            @NotBlank String destVesselCode,
            @NotBlank String speciesCode,
            @NotBlank String areaCode,
            @NotBlank String seasonCode,
            @NotNull @Positive BigDecimal amountKg,
            @NotNull LocalDate effectiveFrom,
            @NotNull LocalDate effectiveTo,
            String note) {
    }
}
