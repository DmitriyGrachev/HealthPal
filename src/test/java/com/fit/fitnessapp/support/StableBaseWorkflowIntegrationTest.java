package com.fit.fitnessapp.support;

import com.fit.fitnessapp.api.InsightGeneratedEvent;
import com.fit.fitnessapp.api.InsightType;
import com.fit.fitnessapp.nutrition.application.port.out.FatSecretApiPort;
import com.fit.fitnessapp.nutrition.application.port.out.NutritionCommandPort;
import com.fit.fitnessapp.nutrition.application.service.NutritionService;
import com.fit.fitnessapp.nutrition.domain.FatSecretToken;
import com.fit.fitnessapp.nutrition.domain.FoodEntry;
import com.fit.fitnessapp.nutrition.domain.NutritionDay;
import com.fit.fitnessapp.nutrition.domain.NutritionDaySaveResult;
import com.fit.fitnessapp.telegram.adapter.in.TelegramUpdateHandler;
import com.fit.fitnessapp.telegram.application.service.handlers.WeightCommandHandler;
import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Contract test for the stable-base path with provider boundaries mocked. */
class StableBaseWorkflowIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private NutritionService nutritionService;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TelegramBotService telegramBotService;
    @Autowired
    private VectorStore vectorStore;
    @Autowired
    private WeightCommandHandler weightCommandHandler;

    @MockitoBean
    private FatSecretApiPort fatSecretApiPort;
    @MockitoBean
    private NutritionCommandPort nutritionCommandPort;
    @MockitoBean(name = "openAiEmbeddingModel")
    private EmbeddingModel embeddingModel;
    @MockitoBean
    private TelegramUpdateHandler telegramUpdateHandler;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM user_memory");
        jdbc.update("DELETE FROM telegram_delivery_outbox");
        jdbc.update("DELETE FROM durable_jobs");
        when(embeddingModel.dimensions()).thenReturn(2048);
        when(embeddingModel.embed(anyString())).thenAnswer(invocation -> embedding(invocation.getArgument(0)));
        when(embeddingModel.embed(any(Document.class)))
                .thenAnswer(invocation -> embedding(invocation.getArgument(0, Document.class).getText()));
        when(embeddingModel.embed(anyList(), any(EmbeddingOptions.class), any(BatchingStrategy.class)))
                .thenAnswer(invocation -> ((List<Document>) invocation.getArgument(0)).stream()
                        .map(document -> embedding(document.getText())).toList());
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenAnswer(invocation -> {
            EmbeddingRequest request = invocation.getArgument(0);
            List<Embedding> embeddings = IntStream.range(0, request.getInstructions().size())
                    .mapToObj(index -> new Embedding(embedding(request.getInstructions().get(index)), index))
                    .toList();
            return new EmbeddingResponse(embeddings);
        });
    }

    @Test
    void syncToInsightMemoryAndTelegramDeliveryUsesDurableBoundaries() throws Exception {
        long userId = insertUser();
        jdbc.update("INSERT INTO telegram_users (telegram_id, user_id, chat_id) VALUES (?, ?, ?)",
                8101L, userId, 8102L);
        LocalDate date = LocalDate.of(2026, 8, 9);
        NutritionDay day = new NutritionDay(userId, date,
                List.of(new FoodEntry(1L, 2L, "Oats", "breakfast", 400, 15, 8, 60)));
        when(nutritionCommandPort.getToken(userId)).thenReturn(Optional.of(new FatSecretToken("token", "secret")));
        when(fatSecretApiPort.fetchAndParseFoodEntries(any(), org.mockito.ArgumentMatchers.eq(userId), any(Long.class)))
                .thenReturn(day);
        when(nutritionCommandPort.saveNutritionDay(day)).thenReturn(
                new NutritionDaySaveResult(userId, date, true, "summary-hash", "entries-hash", 400, 15, 8, 60));

        nutritionService.syncDay(userId, date);
        awaitCount("SELECT COUNT(*) FROM durable_jobs WHERE user_id = ? AND job_type = 'DAILY_INSIGHT'",
                userId, 1L);

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(new InsightGeneratedEvent(
                userId, date, InsightType.DAILY, "Daily insight", "Daily Telegram summary", "summary-hash")));
        awaitCount("SELECT COUNT(*) FROM user_memory WHERE metadata->>'user_id' = ?",
                Long.toString(userId), 1L);
        awaitCount("SELECT COUNT(*) FROM telegram_delivery_outbox WHERE chat_id = ? AND status = 'PENDING'",
                8102L, 1L);

        telegramBotService.processOutboxRetries();

        assertThat(jdbc.queryForObject(
                "SELECT status FROM telegram_delivery_outbox WHERE chat_id = ? ORDER BY id DESC LIMIT 1",
                String.class, 8102L)).isEqualTo("SENT");
        verify(telegramUpdateHandler).execute(any(SendMessage.class));
        assertThat(jdbc.queryForObject(
                "SELECT metadata->>'snapshot_hash' FROM user_memory WHERE metadata->>'user_id' = ?",
                String.class, Long.toString(userId))).isEqualTo("summary-hash");
    }

    @Test
    void telegramWeightCommandPersistsAndConfirmsOnlyAfterDurableListenerPath() throws Exception {
        long userId = insertUser();
        jdbc.update("INSERT INTO telegram_users (telegram_id, user_id, chat_id) VALUES (?, ?, ?)",
                8201L, userId, 8202L);

        weightCommandHandler.handle(update(8201L, 8202L, "/weight"));
        weightCommandHandler.handle(update(8201L, 8202L, "77.7"));

        awaitCount("SELECT COUNT(*) FROM weight_history WHERE user_id = ? AND weight_kg = 77.7",
                userId, 1L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM telegram_delivery_outbox WHERE chat_id = ? AND status = 'SENT'",
                Long.class, 8202L)).isGreaterThanOrEqualTo(1L);
    }

    private long insertUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return jdbc.queryForObject(
                "INSERT INTO users (username, email, password) VALUES (?, ?, 'hash') RETURNING id",
                Long.class, "workflow-" + suffix, "workflow-" + suffix + "@example.test");
    }

    private void awaitCount(String sql, Object argument, long expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        long actual = -1;
        while (System.currentTimeMillis() < deadline) {
            actual = jdbc.queryForObject(sql, Long.class, argument);
            if (actual == expected) {
                return;
            }
            Thread.sleep(50);
        }
        assertThat(actual).isEqualTo(expected);
    }

    private static float[] embedding(String text) {
        float[] values = new float[2048];
        values[0] = 1.0f;
        values[Math.floorMod(text.hashCode(), 2048)] += 0.1f;
        return values;
    }

    private static Update update(long telegramId, long chatId, String text) {
        Chat chat = mock(Chat.class);
        when(chat.isUserChat()).thenReturn(true);
        Message message = mock(Message.class);
        when(message.getChat()).thenReturn(chat);
        when(message.getChatId()).thenReturn(chatId);
        User user = mock(User.class);
        when(user.getId()).thenReturn(telegramId);
        when(message.getFrom()).thenReturn(user);
        when(message.hasText()).thenReturn(true);
        when(message.getText()).thenReturn(text);
        Update update = mock(Update.class);
        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        return update;
    }
}
