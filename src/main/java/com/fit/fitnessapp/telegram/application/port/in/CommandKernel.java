package com.fit.fitnessapp.telegram.application.port.in;

/** Channel-neutral entry point for command dispatch. */
@FunctionalInterface
public interface CommandKernel {
    CommandResult dispatch(InboundCommand command);
}
