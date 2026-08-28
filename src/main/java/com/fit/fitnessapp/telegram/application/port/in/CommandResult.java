package com.fit.fitnessapp.telegram.application.port.in;

/** Privacy-safe dispatch result; it never carries user command content. */
public record CommandResult(
        Status status,
        InboundCommand.Type commandType,
        String handler,
        String errorCode) {

    public CommandResult {
        if (status == null || commandType == null) {
            throw new IllegalArgumentException("command result status and type are required");
        }
        handler = handler == null || handler.isBlank() ? "none" : handler;
        errorCode = errorCode == null || errorCode.isBlank() ? null : errorCode;
    }

    public static CommandResult handled(InboundCommand.Type type, String handler) {
        return new CommandResult(Status.HANDLED, type, handler, null);
    }

    public static CommandResult unhandled(InboundCommand.Type type) {
        return new CommandResult(Status.UNHANDLED, type, "none", "COMMAND_UNHANDLED");
    }

    public enum Status {
        HANDLED,
        UNHANDLED
    }
}
