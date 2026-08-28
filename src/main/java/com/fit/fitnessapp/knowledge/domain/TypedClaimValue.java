package com.fit.fitnessapp.knowledge.domain;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;

public record TypedClaimValue(Type type, String canonicalValue, String unit) {

    public TypedClaimValue {
        if (type == null) {
            throw new IllegalArgumentException("value type is required");
        }
        canonicalValue = canonicalize(type, canonicalValue);
        if (unit != null) {
            if (unit.isBlank()) {
                throw new IllegalArgumentException("unit must not be blank");
            }
            unit = ClaimTextNormalizer.display(unit, "unit", 32).toLowerCase(Locale.ROOT);
        }
        if (unit != null && type != Type.INTEGER && type != Type.DECIMAL) {
            throw new IllegalArgumentException("unit is only valid for numeric values");
        }
    }

    public static TypedClaimValue text(String value) {
        return new TypedClaimValue(Type.TEXT, value, null);
    }

    public static TypedClaimValue text(String value, String unit) {
        return new TypedClaimValue(Type.TEXT, value, unit);
    }

    public static TypedClaimValue bool(boolean value) {
        return new TypedClaimValue(Type.BOOLEAN, Boolean.toString(value), null);
    }

    public static TypedClaimValue integer(long value, String unit) {
        return new TypedClaimValue(Type.INTEGER, Long.toString(value), unit);
    }

    public static TypedClaimValue decimal(BigDecimal value, String unit) {
        if (value == null) {
            throw new IllegalArgumentException("decimal value is required");
        }
        return new TypedClaimValue(Type.DECIMAL, value.toPlainString(), unit);
    }

    public static TypedClaimValue date(LocalDate value) {
        if (value == null) {
            throw new IllegalArgumentException("date value is required");
        }
        return new TypedClaimValue(Type.DATE, value.toString(), null);
    }

    public static TypedClaimValue instant(Instant value) {
        if (value == null) {
            throw new IllegalArgumentException("instant value is required");
        }
        return new TypedClaimValue(Type.INSTANT, value.toString(), null);
    }

    public String normalizedValue() {
        return type == Type.TEXT
                ? ClaimTextNormalizer.comparison(canonicalValue)
                : canonicalValue;
    }

    public Object databaseValue() {
        return switch (type) {
            case TEXT, DATE, INSTANT -> canonicalValue;
            case BOOLEAN -> Boolean.valueOf(canonicalValue);
            case INTEGER -> new BigInteger(canonicalValue);
            case DECIMAL -> new BigDecimal(canonicalValue);
        };
    }

    private static String canonicalize(Type type, String value) {
        if (value == null) {
            throw new IllegalArgumentException("value is required");
        }
        try {
            return switch (type) {
                case TEXT -> ClaimTextNormalizer.display(value, "value", 4_000);
                case BOOLEAN -> {
                    String normalized = value.trim().toLowerCase(Locale.ROOT);
                    if (!normalized.equals("true") && !normalized.equals("false")) {
                        throw new IllegalArgumentException("boolean value must be true or false");
                    }
                    yield normalized;
                }
                case INTEGER -> new BigInteger(value.trim()).toString();
                case DECIMAL -> {
                    BigDecimal decimal = new BigDecimal(value.trim()).stripTrailingZeros();
                    yield decimal.signum() == 0 ? "0" : decimal.toPlainString();
                }
                case DATE -> LocalDate.parse(value.trim()).toString();
                case INSTANT -> Instant.parse(value.trim()).toString();
            };
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException
                    && exception.getMessage() != null
                    && exception.getMessage().startsWith("value ")) {
                throw exception;
            }
            throw new IllegalArgumentException("value does not match type " + type, exception);
        }
    }

    public enum Type {
        TEXT,
        BOOLEAN,
        INTEGER,
        DECIMAL,
        DATE,
        INSTANT
    }
}
