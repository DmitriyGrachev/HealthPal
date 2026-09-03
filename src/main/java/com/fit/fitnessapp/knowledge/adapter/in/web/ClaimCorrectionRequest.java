package com.fit.fitnessapp.knowledge.adapter.in.web;

import com.fit.fitnessapp.knowledge.domain.TypedClaimValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record ClaimCorrectionRequest(
        @NotBlank @Size(max = 256) String subject,
        @NotBlank @Size(max = 128) String predicate,
        @NotNull @Valid ValueRequest value,
        @NotNull Instant observedAt,
        Instant validFrom,
        Instant validUntil,
        @NotNull @Min(0) Long expectedVersion,
        @NotBlank @Size(max = 128) String idempotencyKey) {

    public record ValueRequest(
            @NotNull TypedClaimValue.Type type,
            @NotBlank @Size(max = 4000) String canonicalValue,
            @Size(max = 32) String unit) {
        TypedClaimValue toDomain() {
            // Bound numeric expansion before parsing untrusted decimal/exponent input.
            if (type == TypedClaimValue.Type.DECIMAL
                    && !canonicalValue.trim().matches("[+-]?[0-9]{1,100}(\\.[0-9]{1,100})?")) {
                throw new IllegalArgumentException("decimal must use bounded plain notation");
            }
            if (type == TypedClaimValue.Type.INTEGER
                    && !canonicalValue.trim().matches("[+-]?[0-9]{1,100}")) {
                throw new IllegalArgumentException("integer exceeds bounds");
            }
            return new TypedClaimValue(type, canonicalValue, unit);
        }
    }
}
