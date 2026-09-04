package com.fit.fitnessapp.api.delivery;

import java.util.List;

/** Queues an owned response transactionally, without provider I/O. Empty means the link was revoked. */
public interface OwnedMessageOutbox {
    List<Long> enqueue(Long userId, Long chatId, String text);
}
