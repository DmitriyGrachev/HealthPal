package com.fit.fitnessapp.telegram.infrastructure.config;

import com.fit.fitnessapp.telegram.adapter.in.TelegramUpdateHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Configuration
public class TelegramBotConfig {

    @Bean
    public TelegramClient telegramClient(TelegramProperties properties) {
        return new OkHttpTelegramClient(properties.getToken());
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "telegram.bot", name = "enabled", havingValue = "true", matchIfMissing = true)
    public TelegramBotsLongPollingApplication telegramBotsLongPollingApplication(
            TelegramProperties properties,
            TelegramUpdateHandler updateHandler) throws TelegramApiException {
        TelegramBotsLongPollingApplication application = new TelegramBotsLongPollingApplication();
        application.registerBot(properties.getToken(), updateHandler);
        return application;
    }
}
