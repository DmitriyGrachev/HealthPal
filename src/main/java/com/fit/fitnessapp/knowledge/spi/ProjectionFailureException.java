package com.fit.fitnessapp.knowledge.spi;

/** Stable, content-free failure. Never retains a provider exception message or cause. */
public class ProjectionFailureException extends RuntimeException {
    public enum Code { EGRESS_DENIED, WRITE_FAILED, TRANSACTION_FORBIDDEN, OWNER_MISSING, FENCE_REJECTED, SOURCE_CHANGED }
    private final Code code;
    public ProjectionFailureException(Code code) { super(code.name()); this.code = code; }
    public Code code() { return code; }
}
