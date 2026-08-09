package com.fit.fitnessapp.telegram.application.service.handlers;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramCommandParserTest {

    @Test
    void acceptsCommandWithOptionalArguments() {
        assertThat(TelegramCommandParser.isCommand("/weight", "/weight")).isTrue();
        assertThat(TelegramCommandParser.isCommand("/weight 75.5", "/weight")).isTrue();
    }

    @Test
    void rejectsCommandPrefixesAndDifferentCommands() {
        assertThat(TelegramCommandParser.isCommand("/weighty", "/weight")).isFalse();
        assertThat(TelegramCommandParser.isCommand("/weighty 75", "/weight")).isFalse();
        assertThat(TelegramCommandParser.isCommand("/today", "/weight")).isFalse();
    }
}
