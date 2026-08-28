package com.fit.fitnessapp.telegram.application.service;

import com.fit.fitnessapp.telegram.application.port.in.CommandKernel;
import com.fit.fitnessapp.telegram.application.port.in.CommandResult;
import com.fit.fitnessapp.telegram.application.port.in.InboundCommand;
import com.fit.fitnessapp.telegram.application.service.handlers.CommandHandler;
import org.springframework.stereotype.Service;

import java.util.List;

/** Deterministic first-match dispatch over SDK-free application handlers. */
@Service
public class DefaultCommandKernel implements CommandKernel {

    private final List<CommandHandler> handlers;

    public DefaultCommandKernel(List<CommandHandler> handlers) {
        this.handlers = List.copyOf(handlers);
    }

    @Override
    public CommandResult dispatch(InboundCommand command) {
        for (CommandHandler handler : handlers) {
            if (handler.canHandle(command)) {
                handler.handle(command);
                return CommandResult.handled(command.type(), handler.getClass().getSimpleName());
            }
        }
        return CommandResult.unhandled(command.type());
    }
}
