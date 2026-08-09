package com.fit.fitnessapp.nutrition.adapter.in;

import com.fit.fitnessapp.nutrition.application.port.in.WeightHistoryUseCase;
import com.fit.fitnessapp.nutrition.domain.WeightHistoryDto;
import com.fit.fitnessapp.api.TelegramWeightRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TelegramWeightListener {

    private static final Logger log = LoggerFactory.getLogger(TelegramWeightListener.class);

    private final WeightHistoryUseCase weightHistoryUseCase;

    @ApplicationModuleListener
    public void onWeightRequested(TelegramWeightRequestedEvent event) {
        log.info("Nutrition module received WeightRequestedEvent userId={} chatId={} status=received",
                event.userId(), event.chatId());

        WeightHistoryDto dto = new WeightHistoryDto(
                null,
                event.userId(),
                event.weightKg(),
                event.date(),
                WeightHistoryDto.WeightSource.MANUAL
        );

        weightHistoryUseCase.saveWeight(dto);
        log.info("Telegram weight saved userId={} date={} status=saved", event.userId(), event.date());
    }
}
