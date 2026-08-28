package com.fit.fitnessapp.telegram.application.service.handlers;

import com.fit.fitnessapp.telegram.application.port.in.InboundCommand;

/** SDK-free strategy used by the channel-neutral command kernel. */
public interface CommandHandler {
    boolean canHandle(InboundCommand command);

    void handle(InboundCommand command);
}
