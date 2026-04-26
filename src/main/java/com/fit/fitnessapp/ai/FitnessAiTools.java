package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.memory.MemoryUpdateUseCase;
import com.fit.fitnessapp.memory.MemoryType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.Function;

@Configuration
public class FitnessAiTools {

    public record UpdateMemoryRequest(Long userId, String content, String type) {}

    @Bean
    @Description("Сохранить важный факт или паттерн поведения пользователя в долговременную память. " +
            "Используй это, когда обнаруживаешь непереносимость продуктов, предпочтения по времени тренировок " +
            "или стабильные еженедельные паттерны (например, 'каждую пятницу калории выше нормы'). " +
            "Type должен быть 'FACT' для постоянных фактов или 'SEMANTIC' для выявленных паттернов.")
    public Function<UpdateMemoryRequest, String> updateMemory(MemoryUpdateUseCase memoryUpdateUseCase) {
        return request -> {
            if (request.userId() == null) {
                return "Error: User ID is required.";
            }
            
            MemoryType memoryType;
            try {
                memoryType = MemoryType.valueOf(request.type().toUpperCase());
            } catch (Exception e) {
                memoryType = MemoryType.FACT;
            }

            memoryUpdateUseCase.updateMemory(request.userId(), request.content(), memoryType);
            return "Memory updated successfully for user " + request.userId();
        };
    }
}
