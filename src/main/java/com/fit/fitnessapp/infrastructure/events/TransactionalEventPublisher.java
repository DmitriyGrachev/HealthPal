package com.fit.fitnessapp.infrastructure.events;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Publishes a business event in a short transaction after external work has completed. */
@Service
@RequiredArgsConstructor
public class TransactionalEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(Object event) {
        eventPublisher.publishEvent(event);
    }
}
