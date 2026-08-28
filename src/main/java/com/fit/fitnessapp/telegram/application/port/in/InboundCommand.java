package com.fit.fitnessapp.telegram.application.port.in;

/** SDK-free, owner-resolved representation of one private channel input. */
public record InboundCommand(
        int updateId,
        Long chatId,
        Long senderId,
        Long userId,
        Type type,
        String payload) {

    private static final int MAX_PAYLOAD_LENGTH = 4_096;

    public InboundCommand {
        if (updateId < 0 || chatId == null || chatId < 1 || senderId == null || senderId < 1) {
            throw new IllegalArgumentException("inbound command identifiers are invalid");
        }
        if (userId != null && userId < 1) {
            throw new IllegalArgumentException("resolved userId must be positive");
        }
        if (type == null) {
            throw new IllegalArgumentException("inbound command type is required");
        }
        payload = payload == null ? "" : payload.trim();
        if (payload.length() > MAX_PAYLOAD_LENGTH) {
            throw new IllegalArgumentException("inbound command payload is too long");
        }
    }

    public String idempotencyKey() {
        return "telegram:" + updateId;
    }

    public enum Type {
        START(false),
        LINK(false),
        TEST_GENERATE(false),
        ASK(true),
        TODAY(true),
        WEIGHT(true),
        NOTE(true),
        GOAL(true),
        EXPERIMENT(true),
        CHECKIN(true),
        OUTCOME(true),
        EVALUATE(true),
        TEXT(true),
        UNKNOWN(false);

        private final boolean currentLinkRequired;

        Type(boolean currentLinkRequired) {
            this.currentLinkRequired = currentLinkRequired;
        }

        public boolean currentLinkRequired() {
            return currentLinkRequired;
        }
    }
}
