package com.fit.fitnessapp.nutrition.adapter.out.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fit.fitnessapp.api.FatSecretLegacyApi;
import com.fit.fitnessapp.nutrition.application.port.out.FatSecretApiPort;
import com.fit.fitnessapp.nutrition.application.util.TimeEntryUtil;
import com.fit.fitnessapp.nutrition.domain.*;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth1AccessToken;
import com.github.scribejava.core.model.OAuth1RequestToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth10aService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class FatSecretApiAdapter implements FatSecretApiPort {

    private static final Logger log = LoggerFactory.getLogger(FatSecretApiAdapter.class);

    @Value("${fatsecret.consumer-key}")
    private String consumerKey;

    @Value("${fatsecret.consumer-secret}")
    private String consumerSecret;

    @Value("${fatsecret.callback-url}")
    private String callbackUrl;

    private final Cache<String, FatSecretAuthState> requestTokenCache;
    private final ObjectMapper objectMapper;

    private final TimeEntryUtil timeEntryUtil = new TimeEntryUtil();

    public FatSecretApiAdapter(Cache<String, FatSecretAuthState> requestTokenCache, ObjectMapper objectMapper) {
        this.requestTokenCache = requestTokenCache;
        this.objectMapper = objectMapper;
    }

    private OAuth10aService createService() {
        return new ServiceBuilder(consumerKey)
                .apiSecret(consumerSecret)
                .callback(callbackUrl)
                .build(FatSecretLegacyApi.instance());
    }

    @Override
    public String getAuthUrl(Long userId) {
        try {
            OAuth10aService service = createService();
            OAuth1RequestToken requestToken = service.getRequestToken();

            // Прячем технический кэш внутри адаптера
            requestTokenCache.put(requestToken.getToken(), new FatSecretAuthState(requestToken, userId));

            return service.getAuthorizationUrl(requestToken);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate FatSecret Auth URL", e);
        }
    }

    @Override
    public FatSecretAuthResult exchangeToken(String oauthToken, String oauthVerifier) {
        try {
            FatSecretAuthState state = requestTokenCache.getIfPresent(oauthToken);
            if (state == null) {
                throw new RuntimeException("Auth session expired or invalid");
            }
            requestTokenCache.invalidate(oauthToken);

            OAuth10aService service = createService();
            OAuth1AccessToken accessToken = service.getAccessToken(state.getRequestToken(), oauthVerifier);

            // Возвращаем чистый DTO с ID юзера и токенами
            return new FatSecretAuthResult(
                    state.getUserId(),
                    new FatSecretToken(accessToken.getToken(), accessToken.getTokenSecret())
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to exchange FatSecret token", e);
        }
    }

    @Override
    public NutritionDay fetchAndParseFoodEntries(FatSecretToken token, Long userId, long daysSinceEpoch) {
        try {
            OAuth10aService service = createService();
            OAuth1AccessToken scribeToken = new OAuth1AccessToken(token.accessToken(), token.accessTokenSecret());

            OAuthRequest request = new OAuthRequest(Verb.POST, "https://platform.fatsecret.com/rest/server.api");
            request.addParameter("method", "food_entries.get.v2");
            request.addParameter("format", "json");
            request.addParameter("date", String.valueOf(daysSinceEpoch));

            service.signRequest(scribeToken, request);
            Response response = service.execute(request);

            System.out.println(response.getBody().toString());
            if (!response.isSuccessful()) {
                throw new RuntimeException("FatSecret API error: " + response.getBody());
            }

            return parseJsonToNutritionDay(response.getBody(), userId, LocalDate.ofEpochDay(daysSinceEpoch));
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch data from FatSecret", e);
        }
    }
    @Override
    public NutritionMonth fetchAndParseFoodEntriesForCurrentMonth(
            FatSecretToken token, Long userId, long currentDaysInMonth) {
        try {
            OAuth10aService service = createService();
            OAuth1AccessToken scribeToken = new OAuth1AccessToken(
                    token.accessToken(), token.accessTokenSecret());

            OAuthRequest request = new OAuthRequest(Verb.POST, "https://platform.fatsecret.com/rest/server.api");
            request.addParameter("method", "food_entries.get_month.v2");
            request.addParameter("format", "json");
            request.addParameter("date", String.valueOf(currentDaysInMonth));

            service.signRequest(scribeToken, request);
            Response response = service.execute(request);

            System.out.println(response.getBody().toString());

            if (!response.isSuccessful()) {
                throw new RuntimeException("FatSecret API error: " + response.getBody());
            }

            return parseMonthJson(response.getBody(), userId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch monthly data from FatSecret", e);
        }
    }

    private NutritionMonth parseMonthJson(String jsonBody, Long userId) {
        List<NutritionDaySummary> days = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(jsonBody);
            JsonNode dayNode = root.path("month").path("day");

            if (dayNode.isArray()) {
                for (JsonNode node : dayNode) {
                    days.add(mapDayNode(node, userId));
                }
            } else if (dayNode.isObject()) {
                days.add(mapDayNode(dayNode, userId));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse FatSecret month JSON", e);
        }
        return new NutritionMonth(userId, days);
    }

    private NutritionDaySummary mapDayNode(JsonNode node, Long userId) {
        int dateInt = node.path("date_int").asInt();
        return new NutritionDaySummary(
                userId,
                LocalDate.ofEpochDay(dateInt),
                dateInt,
                node.path("calories").asDouble(),
                node.path("protein").asDouble(),
                node.path("fat").asDouble(),
                node.path("carbohydrate").asDouble()
        );
    }
    @Override
    public WeightEntryDto getLatestWeight(FatSecretToken token) {
        LocalDate today = LocalDate.now();
        List<WeightEntryDto> history = getWeightHistory(token, today.toEpochDay());

        if (history.isEmpty()) {
            // Если в этом месяце еще нет записей, проверим прошлый месяц
            history = getWeightHistory(token, today.minusMonths(1).withDayOfMonth(today.minusMonths(1).lengthOfMonth()).toEpochDay());
        }

        return history.isEmpty() ? null : history.get(history.size() - 1);
    }

    @Override
    public List<WeightEntryDto> getWeightHistory(FatSecretToken token, long daysSinceEpoch) {
        try {
            OAuth10aService service = createService();
            OAuth1AccessToken scribeToken = new OAuth1AccessToken(token.accessToken(), token.accessTokenSecret());

            OAuthRequest request = new OAuthRequest(Verb.GET, "https://platform.fatsecret.com/rest/weight/month/v2");
            request.addQuerystringParameter("date", String.valueOf(daysSinceEpoch));
            request.addQuerystringParameter("format", "json");

            service.signRequest(scribeToken, request);
            Response response = service.execute(request);

            log.debug("FatSecret getWeightHistory response [{}]: {}", response.getCode(), response.getBody());

            if (!response.isSuccessful()) {
                throw new RuntimeException("FatSecret API call failed: " + response.getCode() + " " + response.getBody());
            }

            return parseWeightHistoryResponse(response.getBody());

        } catch (Exception e) {
            log.error("Failed to get weight history from FatSecret", e);
            throw new RuntimeException("Failed to get weight history from FatSecret", e);
        }
    }

    @Override
    public boolean updateWeight(FatSecretToken token, WeightEntryDto weightEntry) {
        try {
            OAuth10aService service = createService();
            OAuth1AccessToken scribeToken = new OAuth1AccessToken(token.accessToken(), token.accessTokenSecret());

            OAuthRequest request = new OAuthRequest(Verb.POST, "https://platform.fatsecret.com/rest/weight/v1");
            request.addParameter("current_weight_kg", weightEntry.weight().toString());
            if (weightEntry.date() != null) {
                request.addParameter("date", String.valueOf(weightEntry.date().toEpochDay()));
            }
            if (weightEntry.comment() != null) {
                request.addParameter("comment", weightEntry.comment());
            }
            request.addParameter("format", "json");

            service.signRequest(scribeToken, request);
            Response response = service.execute(request);

            log.debug("FatSecret updateWeight response [{}]: {}", response.getCode(), response.getBody());

            if (!response.isSuccessful()) {
                throw new RuntimeException("FatSecret API call failed: " + response.getCode() + " " + response.getBody());
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.has("error")) {
                log.warn("FatSecret updateWeight returned error: {}", root.path("error").path("message").asText());
                return false;
            }
            return root.path("success").asInt() == 1;

        } catch (Exception e) {
            log.error("Failed to update weight on FatSecret", e);
            throw new RuntimeException("Failed to update weight on FatSecret", e);
        }
    }

    private List<WeightEntryDto> parseWeightHistoryResponse(String jsonResponse) {
        List<WeightEntryDto> entries = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            if (root.has("error")) {
                log.warn("FatSecret getWeightHistory returned error: {}", root.path("error").path("message").asText());
                return entries;
            }
            JsonNode dayNode = root.path("month").path("day");

            if (dayNode.isArray()) {
                for (JsonNode node : dayNode) {
                    entries.add(mapWeightDayNode(node));
                }
            } else if (dayNode.isObject()) {
                entries.add(mapWeightDayNode(dayNode));
            }
        } catch (Exception e) {
            log.error("Failed to parse FatSecret weight history JSON", e);
            throw new RuntimeException("Failed to parse FatSecret weight history JSON", e);
        }
        return entries;
    }

    private WeightEntryDto mapWeightDayNode(JsonNode node) {
        int dateInt = node.path("date_int").asInt();
        return new WeightEntryDto(
                new java.math.BigDecimal(node.path("weight_kg").asText()),
                LocalDate.ofEpochDay(dateInt),
                dateInt,
                node.path("weight_comment").asText(null)
        );
    }
    @Override
    public List<FatSecretExerciseDto> getExercises(FatSecretToken token) {
        try {
            OAuth10aService service = createService();
            OAuth1AccessToken scribeToken = new OAuth1AccessToken(token.accessToken(), token.accessTokenSecret());

            OAuthRequest request = new OAuthRequest(Verb.GET, "https://platform.fatsecret.com/rest/exercises/v2");
            request.addQuerystringParameter("format", "json");

            service.signRequest(scribeToken, request);
            Response response = service.execute(request);

            if (!response.isSuccessful()) {
                throw new RuntimeException("FatSecret API error: " + response.getBody());
            }

            List<FatSecretExerciseDto> exercises = new ArrayList<>();
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode exerciseNode = root.path("exercise_types").path("exercise");

            if (exerciseNode.isArray()) {
                for (JsonNode node : exerciseNode) {
                    exercises.add(new FatSecretExerciseDto(
                            node.path("exercise_id").asLong(),
                            node.path("exercise_name").asText()
                    ));
                }
            } else if (exerciseNode.isObject()) {
                exercises.add(new FatSecretExerciseDto(
                        exerciseNode.path("exercise_id").asLong(),
                        exerciseNode.path("exercise_name").asText()
                ));
            }
            return exercises;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch exercises from FatSecret", e);
        }
    }

    @Override
    public List<FatSecretExerciseEntryDto> getExerciseEntries(FatSecretToken token, long daysSinceEpoch) {
        try {
            OAuth10aService service = createService();
            OAuth1AccessToken scribeToken = new OAuth1AccessToken(token.accessToken(), token.accessTokenSecret());

            OAuthRequest request = new OAuthRequest(Verb.GET, "https://platform.fatsecret.com/rest/exercise-entries/v2");
            request.addQuerystringParameter("date", String.valueOf(daysSinceEpoch));
            request.addQuerystringParameter("format", "json");

            service.signRequest(scribeToken, request);
            Response response = service.execute(request);

            if (!response.isSuccessful()) {
                throw new RuntimeException("FatSecret API error: " + response.getBody());
            }

            List<FatSecretExerciseEntryDto> entries = new ArrayList<>();
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode entryNode = root.path("exercise_entries").path("exercise_entry");

            if (entryNode.isArray()) {
                for (JsonNode node : entryNode) {
                    entries.add(mapExerciseEntryNode(node));
                }
            } else if (entryNode.isObject()) {
                entries.add(mapExerciseEntryNode(entryNode));
            }
            return entries;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch exercise entries from FatSecret", e);
        }
    }

    private FatSecretExerciseEntryDto mapExerciseEntryNode(JsonNode node) {
        return new FatSecretExerciseEntryDto(
                node.path("exercise_id").asLong(),
                node.path("exercise_name").asText(),
                node.path("minutes").asInt(),
                new java.math.BigDecimal(node.path("calories").asText()),
                node.path("is_template_value").asInt() == 1
        );
    }

    private NutritionDay parseJsonToNutritionDay(String jsonBody, Long userId, LocalDate date) {
        List<FoodEntry> entries = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(jsonBody);
            JsonNode foodEntriesNode = root.path("food_entries").path("food_entry");

            if (foodEntriesNode.isArray()) {
                for (JsonNode node : foodEntriesNode) {
                    FoodEntry entry = new FoodEntry(
                            node.path("food_id").asLong(),
                            node.path("food_entry_id").asLong(),
                            node.path("food_entry_name").asText(),
                            node.path("meal").asText(),
                            node.path("calories").asInt(),
                            node.path("protein").asDouble(),
                            node.path("fat").asDouble(),
                            node.path("carbohydrate").asDouble()
                    );
                    entries.add(entry);
                    System.out.println(entry.toString());
                }
            } else if (foodEntriesNode.isObject()) { // FatSecret возвращает объект, если запись всего одна
                FoodEntry entry = new FoodEntry(
                        foodEntriesNode.path("food_id").asLong(),
                        foodEntriesNode.path("food_entry_id").asLong(),
                        foodEntriesNode.path("food_entry_name").asText(),
                        foodEntriesNode.path("meal").asText(),
                        foodEntriesNode.path("calories").asInt(),
                        foodEntriesNode.path("protein").asDouble(),
                        foodEntriesNode.path("fat").asDouble(),
                        foodEntriesNode.path("carbohydrate").asDouble()
                );
                entries.add(entry);
                System.out.println(entry.toString());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse FatSecret JSON", e);
        }
        return new NutritionDay(userId, date, entries);
    }
}