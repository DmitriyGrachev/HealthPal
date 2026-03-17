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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class FatSecretApiAdapter implements FatSecretApiPort {

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