package com.fit.fitnessapp.knowledge.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypedClaimValueTest {

    @Test
    void canonicalizesEverySupportedValueTypeAndNumericUnits() {
        assertThat(TypedClaimValue.text("  Evening   Workout "))
                .extracting(TypedClaimValue::type, TypedClaimValue::canonicalValue,
                        TypedClaimValue::normalizedValue, TypedClaimValue::unit)
                .containsExactly(TypedClaimValue.Type.TEXT, "Evening Workout", "evening workout", null);
        assertThat(TypedClaimValue.decimal(new BigDecimal("80.500"), " KG "))
                .extracting(TypedClaimValue::type, TypedClaimValue::canonicalValue,
                        TypedClaimValue::normalizedValue, TypedClaimValue::unit)
                .containsExactly(TypedClaimValue.Type.DECIMAL, "80.5", "80.5", "kg");
        assertThat(TypedClaimValue.integer(7, "days"))
                .extracting(TypedClaimValue::type, TypedClaimValue::canonicalValue)
                .containsExactly(TypedClaimValue.Type.INTEGER, "7");
        assertThat(TypedClaimValue.bool(true).canonicalValue()).isEqualTo("true");
        assertThat(TypedClaimValue.date(LocalDate.of(2026, 8, 28)).canonicalValue())
                .isEqualTo("2026-08-28");
        assertThat(TypedClaimValue.instant(Instant.parse("2026-08-28T10:15:30Z")).canonicalValue())
                .isEqualTo("2026-08-28T10:15:30Z");
    }

    @Test
    void rejectsBlankValuesAndUnitsOnNonNumericTypes() {
        assertThatThrownBy(() -> TypedClaimValue.text("  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TypedClaimValue.text("sleep", "hours"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TypedClaimValue.decimal(BigDecimal.ONE, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
