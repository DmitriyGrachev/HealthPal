package com.fit.fitnessapp.auth;

import com.fit.fitnessapp.api.InsightGeneratedEvent;
import com.fit.fitnessapp.api.InsightType;
import com.fit.fitnessapp.api.ChangeType;
import com.fit.fitnessapp.api.DomainSourceState;
import com.fit.fitnessapp.api.lifecycle.DataRetentionDisclosure;
import com.fit.fitnessapp.api.lifecycle.UserDataExportFragment;
import com.fit.fitnessapp.api.lifecycle.UserDataLifecycleParticipant;
import com.fit.fitnessapp.auth.application.port.in.UserDataExportManifestUseCase;
import com.fit.fitnessapp.auth.application.port.in.UserDataLifecycleUseCase;
import com.fit.fitnessapp.auth.domain.UserDataExportManifest;
import com.fit.fitnessapp.memory.application.service.MemoryEventListener;
import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fit.fitnessapp.telegram.adapter.in.TelegramNotificationListener;
import com.fit.fitnessapp.telegram.adapter.in.TelegramAiResponseListener;
import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.application.service.TelegramLinkRevocationService;
import com.fit.fitnessapp.telegram.application.port.in.ConversationStateUseCase;
import com.fit.fitnessapp.telegram.domain.ConversationState;
import com.fit.fitnessapp.telegram.infrastructure.persistence.repository.TelegramUserRepository;
import com.fit.fitnessapp.nutrition.application.port.out.NutritionSourceStatePort;
import com.fit.fitnessapp.workout.application.port.out.WorkoutSourceStatePort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;

import java.time.LocalDate;
import java.sql.Connection;
import java.sql.PreparedStatement;
import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Import(UserDataLifecycleServiceIntegrationTest.RollbackTestConfiguration.class)
class UserDataLifecycleServiceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private UserDataLifecycleUseCase lifecycleUseCase;

    @Autowired
    private UserDataExportManifestUseCase manifestUseCase;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private MemoryEventListener memoryEventListener;

    @Autowired
    private TelegramNotificationListener telegramNotificationListener;

    @Autowired
    private RollbackProbeParticipant rollbackProbe;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private NutritionSourceStatePort nutritionSourceState;

    @Autowired
    private WorkoutSourceStatePort workoutSourceState;

    @Autowired
    private TelegramUserRepository telegramUserRepository;

    @Autowired
    private TelegramBotService telegramBotService;

    @Autowired
    private TelegramLinkRevocationService telegramLinkRevocationService;

    @Autowired
    private TelegramAiResponseListener telegramAiResponseListener;

    @Autowired
    private ConversationStateUseCase conversationStateUseCase;

    @MockitoBean
    private AbsSender botSender;

    @Test
    void exportsAndDeletesEveryOwnedCategoryWithoutTouchingAnotherUserOrGlobalBudget() throws Exception {
        long userId = insertUser("lifecycle");
        long otherUserId = insertUser("lifecycle-other");
        long chatId = 4_001L;
        long otherChatId = 5_001L;

        seedOwnedData(userId, chatId);
        jdbc.update("INSERT INTO telegram_delivery_outbox (chat_id, text) VALUES (?, 'anonymous same chat')", chatId);
        seedControlData(otherUserId, otherChatId);
        UUID completedPublication = insertPublication(userId, true);
        UUID incompletePublication = insertPublication(userId, false);
        UUID otherPublication = insertPublication(otherUserId, false);
        UUID malformedPublication = insertMalformedPublication();
        InsightGeneratedEvent staleEvent = new InsightGeneratedEvent(
                userId,
                LocalDate.of(2026, 8, 8),
                InsightType.DAILY,
                "stale private insight",
                "stale Telegram summary",
                "stale-snapshot");

        var export = lifecycleUseCase.exportUserData(userId);

        assertThat(export.userId()).isEqualTo(userId);
        assertThat(export.profile()).containsEntry("goal_weight_kg", 75.0);
        assertThat(export.weightHistory()).hasSize(2)
                .extracting(row -> row.get("weight_source"))
                .containsExactly("MANUAL", "FATSECRET");
        assertThat(export.nutritionDays()).hasSize(1);
        assertThat(export.foodEntries()).hasSize(1);
        assertThat(export.workoutSessions()).hasSize(1);
        assertThat(export.workoutExercises()).hasSize(1);
        assertThat(export.workoutSets()).hasSize(1);
        assertThat(export.workoutCardio()).hasSize(1);
        assertThat(export.userNotes()).hasSize(1);
        assertThat(export.aiInsights()).hasSize(1);
        assertThat(export.memories()).singleElement()
                .satisfies(memory -> assertThat(memory.get("content")).isEqualTo("owned memory"));
        assertThat(export.telegramAccount()).containsEntry("chat_id", chatId);
        assertThat(export.conversationState()).containsEntry("state", "AWAITING_NOTE");
        assertThat(export.conversationHistory()).hasSize(1);
        assertThat(export.telegramDeliveries()).hasSize(1)
                .allSatisfy(row -> assertThat(row).containsEntry("text", "private response"));
        assertThat(export.telegramDeliveries()).allSatisfy(row -> assertThat(row)
                .doesNotContainKeys("lease_owner", "lease_expires_at", "claimed_at", "lease_generation"));
        assertThat(export.durableJobs()).hasSize(1);
        assertThat(export.durableJobs()).allSatisfy(row -> assertThat(row)
                .doesNotContainKeys("lease_generation", "lease_owner", "lease_expires_at", "claimed_at"));
        assertThat(export.fatSecretConnected()).isTrue();
        assertThat(export.aiUsageBudget()).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("scope_type", "USER");
            assertThat(row).containsEntry("scope_id", userId);
            assertThat(row).containsEntry("used_tokens", 250L);
        });

        var v1Json = objectMapper.valueToTree(export);
        assertThat(v1Json.fieldNames()).toIterable()
                .doesNotContain("manifestVersion", "modules");
        assertThat(v1Json.get("profile")).isNotNull();

        UserDataExportManifest v2 = manifestUseCase.exportUserData(userId);
        assertThat(v2.manifestVersion()).isEqualTo(2);
        assertThat(v2.modules()).extracting(module -> module.moduleKey())
                .isSorted().doesNotHaveDuplicates();
        v2.modules().stream()
                .forEach(module -> {
                    assertThat(module.schemaVersion()).isGreaterThanOrEqualTo(1);
                    assertThat(module.retentionDisclosure()).isNotEmpty().allSatisfy(disclosure -> {
                        assertThat(disclosure.storageClass())
                                .isNotEqualTo(DataRetentionDisclosure.StorageClass.UNSPECIFIED);
                        assertThat(disclosure.retentionClass())
                                .isNotEqualTo(DataRetentionDisclosure.RetentionClass.UNSPECIFIED);
                        assertThat(disclosure.deletionScope())
                                .isNotEqualTo(DataRetentionDisclosure.DeletionScope.UNSPECIFIED);
                    });
                });
        assertThat(moduleCategories(v2, "ai"))
                .contains("ai_insights", "ai_usage_budget");
        assertThat(moduleCategories(v2, "nutrition"))
                .contains("nutrition_profile_and_manual_weight", "fatsecret_connection", "fatsecret_weight",
                        "fatsecret_day", "fatsecret_food", "nutrition_source_state");
        assertThat(moduleCategories(v2, "workout")).contains("workout_source_state");
        assertThat(moduleCategories(v2, "event-publications")).contains("event_publications");

        var nutritionModule = v2.modules().stream()
                .filter(module -> module.moduleKey().equals("nutrition"))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> nutritionSourceState = ((List<Map<String, Object>>)
                nutritionModule.data().get("nutritionSourceState")).getFirst();
        assertThat(nutritionSourceState)
                .containsKeys("sourceDate", "sourceVersion", "present", "createdAt", "updatedAt")
                .doesNotContainKeys("content_hash", "lifecycle_epoch", "contentHash", "lifecycleEpoch");

        var workoutModule = v2.modules().stream()
                .filter(module -> module.moduleKey().equals("workout"))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> workoutSourceState = ((List<Map<String, Object>>)
                workoutModule.data().get("workoutSourceState")).getFirst();
        assertThat(workoutSourceState)
                .containsKeys("sourceDate", "sourceVersion", "present", "createdAt", "updatedAt")
                .doesNotContainKeys("content_hash", "lifecycle_epoch", "contentHash", "lifecycleEpoch");

        var jobsModule = v2.modules().stream()
                .filter(module -> module.moduleKey().equals("jobs"))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> v2DurableJob = (Map<String, Object>) ((List<?>) jobsModule.data().get("durableJobs"))
                .getFirst();
        assertThat(v2DurableJob)
                .doesNotContainKeys("lease_generation", "lease_owner", "lease_expires_at", "claimed_at");

        var publicationModule = v2.modules().stream()
                .filter(module -> module.moduleKey().equals("event-publications"))
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> publicationReceipts = (List<Map<String, Object>>)
                publicationModule.data().get("eventPublicationReceipts");
        assertThat(publicationReceipts).hasSize(2).allSatisfy(receipt -> assertThat(receipt)
                .containsKeys("id", "listenerId", "eventType", "publicationDate", "completionDate")
                .doesNotContainKeys("serialized_event", "serializedEvent", "user_id", "userId"));
        assertThat(publicationReceipts).extracting(receipt -> receipt.get("id"))
                .containsExactlyInAnyOrder(completedPublication, incompletePublication);
        assertThat(publicationModule.retentionDisclosure()).singleElement()
                .extracting(DataRetentionDisclosure::retentionClass)
                .isEqualTo(DataRetentionDisclosure.RetentionClass.ACCOUNT_LIFETIME);

        String v2Json = objectMapper.writeValueAsString(v2);
        assertThat(v2Json)
                .doesNotContain("access_token", "access_token_secret")
                .doesNotContain("oauth-token-canary-" + userId, "oauth-secret-canary-" + userId)
                .doesNotContain("publication-payload-canary-" + userId)
                .doesNotContain("other memory", "keep me");
        assertThat(v2Json).contains("\"moduleKey\":\"auth\"");
        assertThat(v2Json).contains("\"userNotes\"");

        lifecycleUseCase.disconnectFatSecret(userId);
        assertThat(count("fatsecret_connection", "user_id", userId)).isZero();
        assertThat(count("fatsecret_day", "user_id", userId)).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM fatsecret_food food
                JOIN fatsecret_day day ON day.id = food.day_id
                WHERE day.user_id = ?
                """, Long.class, userId)).isOne();

        var result = lifecycleUseCase.deleteAccount(userId);
        transactionTemplate.executeWithoutResult(status -> {
            AopTestUtils.<MemoryEventListener>getTargetObject(memoryEventListener).onInsightGenerated(staleEvent);
            AopTestUtils.<TelegramNotificationListener>getTargetObject(telegramNotificationListener)
                    .onInsightGenerated(staleEvent);
        });

        assertThat(result.success()).isTrue();
        assertThat(count("users", "id", userId)).isZero();
        assertThat(count("user_roles", "user_id", userId)).isZero();
        assertThat(count("user_notes", "user_id", userId)).isZero();
        assertThat(count("profile", "user_id", userId)).isZero();
        assertThat(count("weight_history", "user_id", userId)).isZero();
        assertThat(count("fatsecret_connection", "user_id", userId)).isZero();
        assertThat(count("fatsecret_day", "user_id", userId)).isZero();
        assertThat(count("nutrition_source_state", "user_id", userId)).isZero();
        assertThat(count("workout", "user_id", userId)).isZero();
        assertThat(count("workout_cardio", "user_id", userId)).isZero();
        assertThat(count("workout_source_state", "user_id", userId)).isZero();
        assertThat(count("ai_insights", "user_id", userId)).isZero();
        assertThat(count("telegram_users", "user_id", userId)).isZero();
        assertThat(count("conversation_state", "chat_id", chatId)).isZero();
        assertThat(count("conversation_history", "chat_id", chatId)).isZero();
        assertThat(count("telegram_delivery_outbox", "chat_id", chatId)).isZero();
        assertThat(count("durable_jobs", "user_id", userId)).isZero();
        assertThat(count("user_memory", "user_id", userId)).isZero();
        assertThat(userBudgetCount(userId)).isZero();
        assertThat(publicationCount(completedPublication)).isZero();
        assertThat(publicationCount(incompletePublication)).isZero();
        assertThat(serializedPublicationCount(userId)).isZero();

        assertThat(count("users", "id", otherUserId)).isOne();
        assertThat(count("profile", "user_id", otherUserId)).isOne();
        assertThat(count("telegram_users", "user_id", otherUserId)).isOne();
        assertThat(count("telegram_delivery_outbox", "chat_id", otherChatId)).isOne();
        assertThat(count("user_memory", "user_id", otherUserId)).isOne();
        assertThat(userBudgetCount(otherUserId)).isOne();
        assertThat(globalBudgetCount()).isOne();
        assertThat(publicationCount(otherPublication)).isOne();
        assertThat(publicationCount(malformedPublication)).isOne();
        assertThat(serializedPublicationCount(otherUserId)).isOne();

        lifecycleUseCase.deleteAccount(otherUserId);
        jdbc.update("DELETE FROM event_publication WHERE id = ?", malformedPublication);
        jdbc.update("""
                DELETE FROM ai_usage_budget
                 WHERE scope_type = 'GLOBAL' AND scope_id = 0
                   AND window_start = TIMESTAMPTZ '2025-01-01 00:00:00Z'
                """);
    }

    @Test
    void participantFailureRollsBackEarlierModuleCleanupAndPreservesIdentity() {
        long userId = insertUser("rollback");
        jdbc.update("INSERT INTO profile (user_id, age) VALUES (?, 30)", userId);
        seedSourceStateRows(userId);
        rollbackProbe.failFor(userId);

        try {
            assertThatThrownBy(() -> lifecycleUseCase.deleteAccount(userId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("rollback probe");

            assertThat(count("users", "id", userId)).isOne();
            assertThat(count("profile", "user_id", userId)).isOne();
            assertThat(count("nutrition_source_state", "user_id", userId)).isOne();
            assertThat(count("workout_source_state", "user_id", userId)).isOne();
        } finally {
            rollbackProbe.clear();
        }
    }

    @Test
    void accountDeletionWaitsForInFlightTelegramEnqueueAndThenRemovesItsOutboxRow() throws Exception {
        long userId = insertUser("telegram-race");
        long chatId = 9_000_000L + userId;
        jdbc.update("INSERT INTO telegram_users (telegram_id, user_id, chat_id) VALUES (?, ?, ?)",
                chatId, userId, chatId);
        CountDownLatch linkLocked = new CountDownLatch(1);
        CountDownLatch allowEnqueue = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var enqueue = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                telegramUserRepository.findByUserIdForUpdate(userId).orElseThrow();
                linkLocked.countDown();
                try {
                    if (!allowEnqueue.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("enqueue release timed out");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
                telegramBotService.enqueueMessage(chatId, "race-safe message");
            }));

            assertThat(linkLocked.await(5, TimeUnit.SECONDS)).isTrue();
            var deletion = executor.submit(() -> lifecycleUseCase.deleteAccount(userId));
            Thread.sleep(100);
            assertThat(deletion.isDone()).isFalse();

            allowEnqueue.countDown();
            enqueue.get(5, TimeUnit.SECONDS);
            deletion.get(5, TimeUnit.SECONDS);
        } finally {
            allowEnqueue.countDown();
        }

        assertThat(count("users", "id", userId)).isZero();
        assertThat(count("telegram_users", "user_id", userId)).isZero();
        assertThat(count("telegram_delivery_outbox", "chat_id", chatId)).isZero();
    }

    @Test
    void unlinkRevokesQueuedOwnedNotificationBeforeWorkerDelivery() {
        long userId = insertUser("telegram-unlink");
        long chatId = 9_100_000L + userId;
        jdbc.update("INSERT INTO telegram_users (telegram_id, user_id, chat_id) VALUES (?, ?, ?)",
                chatId, userId, chatId);

        assertThat(telegramBotService.enqueueOwnedMessage(userId, chatId, "queued private message")).isTrue();

        telegramLinkRevocationService.unlink(userId);
        telegramBotService.processOutboxRetries();

        verifyNoInteractions(botSender);
        assertThat(count("telegram_users", "user_id", userId)).isZero();
        assertThat(count("telegram_delivery_outbox", "chat_id", chatId)).isZero();
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    @Test
    void unlinkCascadesAlreadySentOwnedPrivateDelivery() {
        long userId = insertUser("telegram-sent-unlink");
        long chatId = 9_150_000L + userId;
        jdbc.update("INSERT INTO telegram_users (telegram_id, user_id, chat_id) VALUES (?, ?, ?)",
                chatId, userId, chatId);
        assertThat(telegramBotService.enqueueOwnedMessage(userId, chatId, "sent private message")).isTrue();

        telegramBotService.processOutboxRetries();

        assertThat(jdbc.queryForObject("""
                SELECT status
                  FROM telegram_delivery_outbox
                 WHERE user_id = ? AND chat_id = ?
                """, String.class, userId, chatId)).isEqualTo("SENT");

        telegramLinkRevocationService.unlink(userId);

        assertThat(count("telegram_delivery_outbox", "chat_id", chatId)).isZero();
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    @Test
    void unlinkDeletesOwnedAndAnonymousRowsForOnlyTheLockedChat() {
        long userId = insertUser("telegram-mixed-unlink");
        long otherUserId = insertUser("telegram-mixed-other");
        long chatId = 9_175_000L + userId;
        long otherChatId = 9_176_000L + otherUserId;
        try {
            jdbc.update("INSERT INTO telegram_users (telegram_id, user_id, chat_id) VALUES (?, ?, ?)",
                    chatId, userId, chatId);
            jdbc.update("INSERT INTO telegram_users (telegram_id, user_id, chat_id) VALUES (?, ?, ?)",
                    otherChatId, otherUserId, otherChatId);
            assertThat(telegramBotService.enqueueOwnedMessage(userId, chatId, "owned pending")).isTrue();
            assertThat(telegramBotService.enqueueOwnedMessage(userId, chatId, "owned sent")).isTrue();
            jdbc.update("""
                    UPDATE telegram_delivery_outbox
                       SET status = 'SENT', sent_at = NOW()
                     WHERE user_id = ? AND chat_id = ? AND text = 'owned sent'
                    """, userId, chatId);
            jdbc.update("INSERT INTO telegram_delivery_outbox (chat_id, text) VALUES (?, 'anonymous same chat')", chatId);
            jdbc.update("INSERT INTO telegram_delivery_outbox (chat_id, text) VALUES (?, 'other chat')", otherChatId);

            telegramLinkRevocationService.unlink(userId);

            assertThat(count("telegram_users", "user_id", userId)).isZero();
            assertThat(count("telegram_delivery_outbox", "chat_id", chatId)).isZero();
            assertThat(count("telegram_users", "user_id", otherUserId)).isOne();
            assertThat(count("telegram_delivery_outbox", "chat_id", otherChatId)).isOne();
        } finally {
            jdbc.update("DELETE FROM telegram_delivery_outbox WHERE chat_id IN (?, ?)", chatId, otherChatId);
            jdbc.update("DELETE FROM telegram_users WHERE user_id IN (?, ?)", userId, otherUserId);
            jdbc.update("DELETE FROM users WHERE id IN (?, ?)", userId, otherUserId);
        }
    }

    @Test
    void unlinkWaitsForOwnedEnqueueThenCascadesItsOutboxRow() throws Exception {
        long userId = insertUser("telegram-unlink-race");
        long chatId = 9_200_000L + userId;
        jdbc.update("INSERT INTO telegram_users (telegram_id, user_id, chat_id) VALUES (?, ?, ?)",
                chatId, userId, chatId);
        CountDownLatch linkLocked = new CountDownLatch(1);
        CountDownLatch allowEnqueue = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var enqueue = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                telegramUserRepository.findByUserIdForUpdate(userId).orElseThrow();
                linkLocked.countDown();
                await(allowEnqueue, "owned enqueue release timed out");
                assertThat(telegramBotService.enqueueOwnedMessage(userId, chatId, "race-safe private message"))
                        .isTrue();
            }));

            assertThat(linkLocked.await(5, TimeUnit.SECONDS)).isTrue();
            var unlink = executor.submit(() -> telegramLinkRevocationService.unlink(userId));
            Thread.sleep(100);
            assertThat(unlink.isDone()).isFalse();

            allowEnqueue.countDown();
            enqueue.get(5, TimeUnit.SECONDS);
            unlink.get(5, TimeUnit.SECONDS);
        } finally {
            allowEnqueue.countDown();
        }

        assertThat(count("telegram_users", "user_id", userId)).isZero();
        assertThat(count("telegram_delivery_outbox", "chat_id", chatId)).isZero();
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    @Test
    void delayedAiResponseAfterAccountDeletionCreatesNoOutboxOrSend() {
        long userId = insertUser("telegram-delayed-ai");
        long chatId = 9_300_000L + userId;
        jdbc.update("INSERT INTO telegram_users (telegram_id, user_id, chat_id) VALUES (?, ?, ?)",
                chatId, userId, chatId);
        lifecycleUseCase.deleteAccount(userId);

        telegramAiResponseListener.onAiResponse(
                new com.fit.fitnessapp.api.TelegramAiResponseEvent(userId, chatId, "stale private response"));

        verifyNoInteractions(botSender);
        assertThat(count("telegram_delivery_outbox", "chat_id", chatId)).isZero();
    }

    @Test
    void delayedStateUpdateAfterAccountDeletionCannotRecreatePrivateState() {
        long userId = insertUser("telegram-delayed-state");
        long chatId = 9_400_000L + userId;
        jdbc.update("INSERT INTO telegram_users (telegram_id, user_id, chat_id) VALUES (?, ?, ?)",
                chatId, userId, chatId);
        lifecycleUseCase.deleteAccount(userId);

        boolean updated = conversationStateUseCase.updateState(
                userId, chatId, ConversationState.WAITING_WEIGHT);

        assertThat(updated).isFalse();
        assertThat(count("conversation_state", "chat_id", chatId)).isZero();
    }

    @Test
    void unlinkDuringLiveClaimDeletesRowAndFencesLateProviderCompletion() throws Exception {
        long userId = insertUser("telegram-live-unlink");
        long chatId = 9_450_000L + userId;
        jdbc.update("INSERT INTO telegram_users (telegram_id, user_id, chat_id) VALUES (?, ?, ?)",
                chatId, userId, chatId);
        assertThat(telegramBotService.enqueueOwnedMessage(userId, chatId, "live unlink message")).isTrue();
        CountDownLatch providerStarted = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        org.mockito.Mockito.when(botSender.execute(org.mockito.ArgumentMatchers.any(
                        org.telegram.telegrambots.meta.api.methods.send.SendMessage.class)))
                .thenAnswer(invocation -> {
                    providerStarted.countDown();
                    releaseProvider.await(5, TimeUnit.SECONDS);
                    return null;
                });

        try (var executor = Executors.newSingleThreadExecutor()) {
            var processing = executor.submit(() -> telegramBotService.processOutboxRetries());
            assertThat(providerStarted.await(5, TimeUnit.SECONDS)).isTrue();
            telegramLinkRevocationService.unlink(userId);
            assertThat(count("telegram_delivery_outbox", "chat_id", chatId)).isZero();
            releaseProvider.countDown();
            processing.get(5, TimeUnit.SECONDS);
        } finally {
            releaseProvider.countDown();
            jdbc.update("DELETE FROM telegram_delivery_outbox WHERE chat_id = ?", chatId);
            jdbc.update("DELETE FROM telegram_users WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }

        verify(botSender).execute(org.mockito.ArgumentMatchers.any(
                org.telegram.telegrambots.meta.api.methods.send.SendMessage.class));
        assertThat(count("telegram_delivery_outbox", "chat_id", chatId)).isZero();
    }

    @Test
    void accountDeletionDuringLiveClaimDeletesRowAndFencesLateProviderCompletion() throws Exception {
        long userId = insertUser("telegram-live-delete");
        long chatId = 9_460_000L + userId;
        jdbc.update("INSERT INTO telegram_users (telegram_id, user_id, chat_id) VALUES (?, ?, ?)",
                chatId, userId, chatId);
        assertThat(telegramBotService.enqueueOwnedMessage(userId, chatId, "live deletion message")).isTrue();
        CountDownLatch providerStarted = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        org.mockito.Mockito.when(botSender.execute(org.mockito.ArgumentMatchers.any(
                        org.telegram.telegrambots.meta.api.methods.send.SendMessage.class)))
                .thenAnswer(invocation -> {
                    providerStarted.countDown();
                    releaseProvider.await(5, TimeUnit.SECONDS);
                    return null;
                });

        try (var executor = Executors.newSingleThreadExecutor()) {
            var processing = executor.submit(() -> telegramBotService.processOutboxRetries());
            assertThat(providerStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(lifecycleUseCase.deleteAccount(userId).success()).isTrue();
            assertThat(count("telegram_delivery_outbox", "chat_id", chatId)).isZero();
            releaseProvider.countDown();
            processing.get(5, TimeUnit.SECONDS);
        } finally {
            releaseProvider.countDown();
            jdbc.update("DELETE FROM telegram_delivery_outbox WHERE chat_id = ?", chatId);
            jdbc.update("DELETE FROM telegram_users WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }

        verify(botSender).execute(org.mockito.ArgumentMatchers.any(
                org.telegram.telegrambots.meta.api.methods.send.SendMessage.class));
        assertThat(count("telegram_delivery_outbox", "chat_id", chatId)).isZero();
    }

    @Test
    void unlinkDuringLiveClaimFencesLateProviderFailure() throws Exception {
        long userId = insertUser("telegram-live-unlink-failure");
        long chatId = 9_470_000L + userId;
        jdbc.update("INSERT INTO telegram_users (telegram_id, user_id, chat_id) VALUES (?, ?, ?)",
                chatId, userId, chatId);
        assertThat(telegramBotService.enqueueOwnedMessage(userId, chatId, "live unlink failure")).isTrue();
        CountDownLatch providerStarted = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        TelegramApiException lateFailure = org.mockito.Mockito.mock(TelegramApiException.class);
        when(botSender.execute(org.mockito.ArgumentMatchers.any(
                org.telegram.telegrambots.meta.api.methods.send.SendMessage.class)))
                .thenAnswer(invocation -> {
                    providerStarted.countDown();
                    releaseProvider.await(5, TimeUnit.SECONDS);
                    throw lateFailure;
                });

        try (var executor = Executors.newSingleThreadExecutor()) {
            var processing = executor.submit(() -> telegramBotService.processOutboxRetries());
            assertThat(providerStarted.await(5, TimeUnit.SECONDS)).isTrue();
            telegramLinkRevocationService.unlink(userId);
            assertThat(count("telegram_delivery_outbox", "chat_id", chatId)).isZero();
            releaseProvider.countDown();
            processing.get(5, TimeUnit.SECONDS);
        } finally {
            releaseProvider.countDown();
            jdbc.update("DELETE FROM telegram_delivery_outbox WHERE chat_id = ?", chatId);
            jdbc.update("DELETE FROM telegram_users WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }

        verify(botSender).execute(org.mockito.ArgumentMatchers.any(
                org.telegram.telegrambots.meta.api.methods.send.SendMessage.class));
        assertThat(count("telegram_delivery_outbox", "chat_id", chatId)).isZero();
    }

    @Test
    void accountDeletionDuringLiveClaimFencesLateProviderFailure() throws Exception {
        long userId = insertUser("telegram-live-delete-failure");
        long chatId = 9_480_000L + userId;
        jdbc.update("INSERT INTO telegram_users (telegram_id, user_id, chat_id) VALUES (?, ?, ?)",
                chatId, userId, chatId);
        assertThat(telegramBotService.enqueueOwnedMessage(userId, chatId, "live deletion failure")).isTrue();
        CountDownLatch providerStarted = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        TelegramApiException lateFailure = org.mockito.Mockito.mock(TelegramApiException.class);
        when(botSender.execute(org.mockito.ArgumentMatchers.any(
                org.telegram.telegrambots.meta.api.methods.send.SendMessage.class)))
                .thenAnswer(invocation -> {
                    providerStarted.countDown();
                    releaseProvider.await(5, TimeUnit.SECONDS);
                    throw lateFailure;
                });

        try (var executor = Executors.newSingleThreadExecutor()) {
            var processing = executor.submit(() -> telegramBotService.processOutboxRetries());
            assertThat(providerStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(lifecycleUseCase.deleteAccount(userId).success()).isTrue();
            assertThat(count("telegram_delivery_outbox", "chat_id", chatId)).isZero();
            releaseProvider.countDown();
            processing.get(5, TimeUnit.SECONDS);
        } finally {
            releaseProvider.countDown();
            jdbc.update("DELETE FROM telegram_delivery_outbox WHERE chat_id = ?", chatId);
            jdbc.update("DELETE FROM telegram_users WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }

        verify(botSender).execute(org.mockito.ArgumentMatchers.any(
                org.telegram.telegrambots.meta.api.methods.send.SendMessage.class));
        assertThat(count("telegram_delivery_outbox", "chat_id", chatId)).isZero();
    }

    @Test
    void accountDeletionWaitsForConcurrentPublicationAndCascadesItAtCommit() throws Exception {
        long userId = insertUser("publication-race");
        UUID publicationId = UUID.randomUUID();
        CountDownLatch publicationInserted = new CountDownLatch(1);
        CountDownLatch allowPublicationCommit = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var publication = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                jdbc.update("""
                        INSERT INTO event_publication
                            (id, listener_id, event_type, serialized_event, publication_date)
                        VALUES (?, 'publication-race-test', 'example.UserEvent',
                                jsonb_build_object('userId', ?::bigint, 'payload', 'private')::text,
                                CURRENT_TIMESTAMP)
                        """, publicationId, userId);
                publicationInserted.countDown();
                await(allowPublicationCommit, "publication commit release timed out");
            }));

            assertThat(publicationInserted.await(5, TimeUnit.SECONDS)).isTrue();
            var deletion = executor.submit(() -> lifecycleUseCase.deleteAccount(userId));
            Thread.sleep(100);
            assertThat(deletion.isDone()).isFalse();

            allowPublicationCommit.countDown();
            publication.get(5, TimeUnit.SECONDS);
            deletion.get(5, TimeUnit.SECONDS);
        } finally {
            allowPublicationCommit.countDown();
        }

        assertThat(count("users", "id", userId)).isZero();
        assertThat(publicationCount(publicationId)).isZero();
        assertThat(serializedPublicationCount(userId)).isZero();
    }

    @Test
    void nutritionAccountDeletionAndAdvanceUseTheSameOwnerLockOrder() throws Exception {
        assertDeletionAndAdvanceRaceIsDeadlockFree(
                "nutrition_source_state",
                userId -> nutritionSourceState.advance(
                        userId,
                        LocalDate.of(2026, 8, 8),
                        ChangeType.UPSERT,
                        "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"));
    }

    @Test
    void workoutAccountDeletionAndAdvanceUseTheSameOwnerLockOrder() throws Exception {
        assertDeletionAndAdvanceRaceIsDeadlockFree(
                "workout_source_state",
                userId -> workoutSourceState.advance(
                        userId,
                        LocalDate.of(2026, 8, 8),
                        ChangeType.UPSERT,
                        "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"));
    }

    private void assertDeletionAndAdvanceRaceIsDeadlockFree(
            String sourceTable,
            java.util.function.Function<Long, java.util.Optional<DomainSourceState>> advance) throws Exception {
        long userId = insertUser("source-delete-race");
        seedSourceStateRows(userId);
        try (Connection external = dataSource.getConnection()) {
            external.setAutoCommit(false);
            lockSourceRow(external, sourceTable, userId);

            try (var executor = Executors.newFixedThreadPool(2)) {
                var deletion = executor.submit(() -> lifecycleUseCase.deleteAccount(userId));
                awaitBlockedSourceDelete(sourceTable);
                var competingAdvance = executor.submit(() -> advance.apply(userId));

                external.commit();

                assertThat(deletion.get(10, TimeUnit.SECONDS).success()).isTrue();
                assertThat(competingAdvance.get(10, TimeUnit.SECONDS)).isEmpty();
            } finally {
                if (!external.getAutoCommit()) {
                    external.rollback();
                }
            }
        } finally {
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }

        assertThat(count("users", "id", userId)).isZero();
        assertThat(count(sourceTable, "user_id", userId)).isZero();
    }

    private void lockSourceRow(Connection connection, String sourceTable, long userId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT user_id FROM " + sourceTable + " WHERE user_id = ? FOR UPDATE")) {
            statement.setLong(1, userId);
            statement.executeQuery().close();
        }
    }

    private void awaitBlockedSourceDelete(String sourceTable) {
        String queryPattern = "%DELETE FROM " + sourceTable + "%";
        for (int attempt = 0; attempt < 2_000; attempt++) {
            boolean blocked = Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS (
                        SELECT 1
                          FROM pg_stat_activity activity
                          JOIN pg_locks waiting ON waiting.pid = activity.pid
                         WHERE NOT waiting.granted
                           AND activity.query ILIKE ?
                    )
                    """, Boolean.class, queryPattern));
            if (blocked) {
                return;
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("account deletion did not reach blocked " + sourceTable + " delete");
    }

    @TestConfiguration
    static class RollbackTestConfiguration {

        @Bean
        RollbackProbeParticipant rollbackProbeParticipant() {
            return new RollbackProbeParticipant();
        }
    }

    static class RollbackProbeParticipant implements UserDataLifecycleParticipant {

        private volatile Long failingUserId;

        void failFor(long userId) {
            failingUserId = userId;
        }

        void clear() {
            failingUserId = null;
        }

        @Override
        public String key() {
            return "zz-test-rollback";
        }

        @Override
        public UserDataExportFragment exportData(Long userId) {
            return new UserDataExportFragment(key(), java.util.Map.of());
        }

        @Override
        public java.util.List<DataRetentionDisclosure> retentionDisclosure() {
            return java.util.List.of(new DataRetentionDisclosure(
                    "rollback_probe",
                    DataRetentionDisclosure.StorageClass.LOCAL_OPERATIONAL,
                    DataRetentionDisclosure.RetentionClass.UNTIL_TERMINAL,
                    null,
                    java.util.List.of(),
                    DataRetentionDisclosure.DeletionScope.LOCAL_OPERATIONAL,
                    DataRetentionDisclosure.BackupLimitation.SUBJECT_TO_BACKUP_RETENTION));
        }

        @Override
        public void deleteData(Long userId) {
            if (userId.equals(failingUserId)) {
                throw new IllegalStateException("rollback probe");
            }
        }
    }

    private void seedOwnedData(long userId, long chatId) {
        jdbc.update("INSERT INTO user_roles (user_id, role_name) VALUES (?, 'USER')", userId);
        seedSourceStateRows(userId);
        jdbc.update("INSERT INTO profile (user_id, goal_weight_kg) VALUES (?, 75)", userId);
        jdbc.update("""
                INSERT INTO weight_history (user_id, weight_kg, weight_date, weight_source)
                VALUES (?, 80.5, ?, 'MANUAL')
                """, userId, LocalDate.of(2026, 8, 8));
        jdbc.update("""
                INSERT INTO weight_history (user_id, weight_kg, weight_date, weight_source)
                VALUES (?, 81.25, ?, 'FATSECRET')
                """, userId, LocalDate.of(2026, 8, 7));
        jdbc.update("""
                INSERT INTO fatsecret_connection (user_id, access_token, access_token_secret)
                VALUES (?, ?, ?)
                """, userId, "oauth-token-canary-" + userId, "oauth-secret-canary-" + userId);
        Long dayId = jdbc.queryForObject("""
                INSERT INTO fatsecret_day (user_id, date, date_int)
                VALUES (?, ?, 20673)
                RETURNING id
                """, Long.class, userId, LocalDate.of(2026, 8, 8));
        jdbc.update("""
                INSERT INTO fatsecret_food (external_food_id, name, meal_type, day_id)
                VALUES (101, 'Apple', 'snack', ?)
                """, dayId);
        Long workoutId = jdbc.queryForObject("""
                INSERT INTO workout (jefit_id, date, user_id)
                VALUES (201, CURRENT_TIMESTAMP, ?)
                RETURNING id
                """, Long.class, userId);
        Long exerciseId = jdbc.queryForObject("""
                INSERT INTO workout_exercises (jefit_log_id, exercise_name, workout_id)
                VALUES (301, 'Squat', ?)
                RETURNING id
                """, Long.class, workoutId);
        jdbc.update("""
                INSERT INTO workout_sets (set_index, weight, reps, exercise_id)
                VALUES (0, 100, 5, ?)
                """, exerciseId);
        jdbc.update("""
                INSERT INTO workout_cardio
                    (jefit_id, user_id, date, exercise_name, duration_seconds, distance, calories)
                VALUES (401, ?, CURRENT_TIMESTAMP, 'Run', 1200, 3.0, 250)
                """, userId);
        jdbc.update("""
                INSERT INTO user_notes (user_id, related_date, content, type)
                VALUES (?, ?, 'Lifecycle note', 'GENERAL')
                """, userId, LocalDate.of(2026, 8, 8));
        jdbc.update("""
                INSERT INTO ai_insights (user_id, insight_type, date, insight_text)
                VALUES (?, 'DAILY', ?, 'Owned insight')
                """, userId, LocalDate.of(2026, 8, 8));
        jdbc.update("""
                INSERT INTO telegram_users (telegram_id, user_id, chat_id)
                VALUES (3001, ?, ?)
                """, userId, chatId);
        jdbc.update("""
                INSERT INTO conversation_state (user_id, chat_id, state, data)
                VALUES (?, ?, 'AWAITING_NOTE', '{}'::jsonb)
                """, userId, chatId);
        jdbc.update("""
                INSERT INTO conversation_history (user_id, chat_id, message_text)
                VALUES (?, ?, 'private message')
                """, userId, chatId);
        jdbc.update("""
                INSERT INTO telegram_delivery_outbox (user_id, chat_id, text)
                VALUES (?, ?, 'private response')
                """, userId, chatId);
        jdbc.update("""
                INSERT INTO durable_jobs (job_type, user_id, idempotency_key)
                VALUES ('DAILY_INSIGHT', ?, ?)
                """, userId, "lifecycle:" + UUID.randomUUID());
        jdbc.update("""
                INSERT INTO user_memory (content, metadata)
                VALUES ('owned memory', jsonb_build_object('user_id', ?::bigint))
                """, userId);
        jdbc.update("""
                INSERT INTO ai_usage_budget(scope_type, scope_id, window_start, used_tokens)
                VALUES ('USER', ?, TIMESTAMPTZ '2025-01-01 00:00:00Z', 250)
                """, userId);
    }

    private void seedControlData(long otherUserId, long otherChatId) {
        seedSourceStateRows(otherUserId);
        jdbc.update("INSERT INTO profile (user_id, goal_weight_kg) VALUES (?, 70)", otherUserId);
        jdbc.update("""
                INSERT INTO telegram_users (telegram_id, user_id, chat_id)
                VALUES (3002, ?, ?)
                """, otherUserId, otherChatId);
        jdbc.update("INSERT INTO telegram_delivery_outbox (chat_id, text) VALUES (?, 'keep me')", otherChatId);
        jdbc.update("""
                INSERT INTO user_memory (content, metadata)
                VALUES ('other memory', jsonb_build_object('user_id', ?::bigint))
                """, otherUserId);
        jdbc.update("""
                INSERT INTO ai_usage_budget(scope_type, scope_id, window_start, used_tokens)
                VALUES ('USER', ?, TIMESTAMPTZ '2025-01-01 00:00:00Z', 100)
                """, otherUserId);
        jdbc.update("""
                INSERT INTO ai_usage_budget(scope_type, scope_id, window_start, used_tokens)
                VALUES ('GLOBAL', 0, TIMESTAMPTZ '2025-01-01 00:00:00Z', 350)
                ON CONFLICT (scope_type, scope_id, window_start)
                DO UPDATE SET used_tokens = EXCLUDED.used_tokens
                """);
    }

    private UUID insertPublication(long userId, boolean completed) {
        UUID publicationId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO event_publication
                    (id, listener_id, event_type, serialized_event, publication_date, completion_date)
                VALUES (?, 'lifecycle-test', 'example.UserEvent',
                        jsonb_build_object('userId', ?::bigint, 'payload', ?)::text,
                        CURRENT_TIMESTAMP,
                        CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END)
                """, publicationId, userId, "publication-payload-canary-" + userId, completed);
        return publicationId;
    }

    private UUID insertMalformedPublication() {
        UUID publicationId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO event_publication
                    (id, listener_id, event_type, serialized_event, publication_date)
                VALUES (?, 'lifecycle-test', 'example.MalformedEvent', '{not-json', CURRENT_TIMESTAMP)
                """, publicationId);
        return publicationId;
    }

    private long insertUser(String prefix) {
        String suffix = UUID.randomUUID().toString();
        return jdbc.queryForObject(
                "INSERT INTO users (username, email, password) VALUES (?, ?, 'pass') RETURNING id",
                Long.class,
                prefix + suffix.substring(0, 8),
                prefix + "+" + suffix + "@example.test");
    }

    private long count(String table, String ownerColumn, long ownerId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + ownerColumn + " = ?",
                Long.class,
                ownerId);
    }

    private long userBudgetCount(long userId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM ai_usage_budget
                 WHERE scope_type = 'USER' AND scope_id = ?
                """, Long.class, userId);
    }

    private long globalBudgetCount() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM ai_usage_budget
                 WHERE scope_type = 'GLOBAL' AND scope_id = 0
                   AND window_start = TIMESTAMPTZ '2025-01-01 00:00:00Z'
                """, Long.class);
    }

    private long publicationCount(UUID publicationId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM event_publication WHERE id = ?",
                Long.class,
                publicationId);
    }

    private long serializedPublicationCount(long userId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM event_publication
                 WHERE CASE
                           WHEN serialized_event IS JSON
                           THEN serialized_event::jsonb ->> 'userId'
                       END = ?
                """, Long.class, Long.toString(userId));
    }

    private void seedSourceStateRows(long userId) {
        UUID lifecycleEpoch = jdbc.queryForObject(
                "SELECT lifecycle_epoch FROM users WHERE id = ?", UUID.class, userId);
        jdbc.update("""
                INSERT INTO nutrition_source_state
                    (user_id, source_date, source_version, content_hash, present, lifecycle_epoch)
                VALUES (?, DATE '2026-08-08', 1,
                        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', TRUE, ?)
                """, userId, lifecycleEpoch);
        jdbc.update("""
                INSERT INTO workout_source_state
                    (user_id, source_date, source_version, content_hash, present, lifecycle_epoch)
                VALUES (?, DATE '2026-08-08', 1,
                        'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', TRUE, ?)
                """, userId, lifecycleEpoch);
    }

    private java.util.Set<String> moduleCategories(UserDataExportManifest manifest, String moduleKey) {
        return manifest.modules().stream()
                .filter(module -> module.moduleKey().equals(moduleKey))
                .findFirst()
                .orElseThrow()
                .retentionDisclosure().stream()
                .map(DataRetentionDisclosure::category)
                .collect(java.util.stream.Collectors.toSet());
    }

    private void await(CountDownLatch latch, String failureMessage) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(failureMessage);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
