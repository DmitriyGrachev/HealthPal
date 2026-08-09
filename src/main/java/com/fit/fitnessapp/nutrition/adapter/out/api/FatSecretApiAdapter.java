package com.fit.fitnessapp.nutrition.adapter.out.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fit.fitnessapp.api.FatSecretLegacyApi;
import com.fit.fitnessapp.exception.ExternalApiException;
import com.fit.fitnessapp.nutrition.application.port.out.FatSecretApiPort;
import com.fit.fitnessapp.nutrition.domain.FatSecretAuthResult;
import com.fit.fitnessapp.nutrition.domain.FatSecretExerciseDto;
import com.fit.fitnessapp.nutrition.domain.FatSecretExerciseEntryDto;
import com.fit.fitnessapp.nutrition.domain.FatSecretToken;
import com.fit.fitnessapp.nutrition.domain.FoodEntry;
import com.fit.fitnessapp.nutrition.domain.NutritionDay;
import com.fit.fitnessapp.nutrition.domain.NutritionDaySummary;
import com.fit.fitnessapp.nutrition.domain.NutritionMonth;
import com.fit.fitnessapp.nutrition.domain.NutritionMonthFetchResult;
import com.fit.fitnessapp.nutrition.domain.WeightEntryDto;
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

import java.math.BigDecimal;
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

    public FatSecretApiAdapter(Cache<String, FatSecretAuthState> requestTokenCache, ObjectMapper objectMapper) {
        this.requestTokenCache = requestTokenCache;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getAuthUrl(Long userId) {
        try {
            OAuth10aService service = createService();
            OAuth1RequestToken requestToken = service.getRequestToken();
            requestTokenCache.put(requestToken.getToken(), new FatSecretAuthState(requestToken, userId));
            return service.getAuthorizationUrl(requestToken);
        } catch (Exception e) {
            throw new ExternalApiException("Failed to generate FatSecret Auth URL", e);
        }
    }

    @Override
    public FatSecretAuthResult exchangeToken(String oauthToken, String oauthVerifier) {
        try {
            FatSecretAuthState state = requestTokenCache.getIfPresent(oauthToken);
            if (state == null) {
                throw new IllegalArgumentException("FatSecret auth session expired or invalid");
            }
            requestTokenCache.invalidate(oauthToken);

            OAuth10aService service = createService();
            OAuth1AccessToken accessToken = service.getAccessToken(state.getRequestToken(), oauthVerifier);
            return new FatSecretAuthResult(
                    state.getUserId(),
                    new FatSecretToken(accessToken.getToken(), accessToken.getTokenSecret()));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("Failed to exchange FatSecret token", e);
        }
    }

    @Override
    public NutritionDay fetchAndParseFoodEntries(FatSecretToken token, Long userId, long daysSinceEpoch) {
        try {
            OAuthRequest request = request(Verb.POST, "https://platform.fatsecret.com/rest/server.api");
            request.addParameter("method", "food_entries.get.v2");
            request.addParameter("format", "json");
            request.addParameter("date", String.valueOf(daysSinceEpoch));

            Response response = execute(token, request);
            log.debug("FatSecret food entries response status={}", response.getCode());
            ensureSuccessful(response, "food entries");

            return parseJsonToNutritionDay(response.getBody(), userId, LocalDate.ofEpochDay(daysSinceEpoch));
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("Failed to fetch data from FatSecret", e);
        }
    }

    @Override
    public NutritionMonthFetchResult fetchAndParseFoodEntriesForCurrentMonth(
            FatSecretToken token, Long userId, long currentDaysInMonth) {
        try {
            OAuthRequest request = request(Verb.POST, "https://platform.fatsecret.com/rest/server.api");
            request.addParameter("method", "food_entries.get_month.v2");
            request.addParameter("format", "json");
            request.addParameter("date", String.valueOf(currentDaysInMonth));

            Response response = execute(token, request);
            log.debug("FatSecret monthly food entries response status={}", response.getCode());
            ensureSuccessful(response, "monthly food entries");

            return parseMonthResponse(response.getBody(), userId);
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("Failed to fetch monthly data from FatSecret", e);
        }
    }

    @Override
    public WeightEntryDto getLatestWeight(FatSecretToken token) {
        LocalDate today = LocalDate.now();
        List<WeightEntryDto> history = getWeightHistory(token, today.toEpochDay());

        if (history.isEmpty()) {
            LocalDate previousMonthEnd = today.minusMonths(1).withDayOfMonth(today.minusMonths(1).lengthOfMonth());
            history = getWeightHistory(token, previousMonthEnd.toEpochDay());
        }

        return history.isEmpty() ? null : history.get(history.size() - 1);
    }

    @Override
    public List<WeightEntryDto> getWeightHistory(FatSecretToken token, long daysSinceEpoch) {
        try {
            OAuthRequest request = request(Verb.GET, "https://platform.fatsecret.com/rest/weight/month/v2");
            request.addQuerystringParameter("date", String.valueOf(daysSinceEpoch));
            request.addQuerystringParameter("format", "json");

            Response response = execute(token, request);
            log.debug("FatSecret getWeightHistory response status={}", response.getCode());
            ensureSuccessful(response, "weight history");

            return parseWeightHistoryResponse(response.getBody());
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get weight history from FatSecret", e);
            throw new ExternalApiException("Failed to get weight history from FatSecret", e);
        }
    }

    @Override
    public boolean updateWeight(FatSecretToken token, WeightEntryDto weightEntry) {
        try {
            OAuthRequest request = request(Verb.POST, "https://platform.fatsecret.com/rest/weight/v1");
            request.addParameter("current_weight_kg", weightEntry.weight().toString());
            if (weightEntry.date() != null) {
                request.addParameter("date", String.valueOf(weightEntry.date().toEpochDay()));
            }
            if (weightEntry.comment() != null) {
                request.addParameter("comment", weightEntry.comment());
            }
            request.addParameter("format", "json");

            Response response = execute(token, request);
            log.debug("FatSecret updateWeight response status={}", response.getCode());
            ensureSuccessful(response, "weight update");

            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.has("error")) {
                log.warn("FatSecret updateWeight returned errorCode={}", root.path("error").path("code").asText("UNKNOWN"));
                return false;
            }
            return root.path("success").asInt() == 1;
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to update weight on FatSecret", e);
            throw new ExternalApiException("Failed to update weight on FatSecret", e);
        }
    }

    @Override
    public List<FatSecretExerciseDto> getExercises(FatSecretToken token) {
        try {
            OAuthRequest request = request(Verb.GET, "https://platform.fatsecret.com/rest/exercises/v2");
            request.addQuerystringParameter("format", "json");

            Response response = execute(token, request);
            ensureSuccessful(response, "exercises");

            List<FatSecretExerciseDto> exercises = new ArrayList<>();
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode exerciseNode = root.path("exercise_types").path("exercise");

            if (exerciseNode.isArray()) {
                for (JsonNode node : exerciseNode) {
                    exercises.add(new FatSecretExerciseDto(
                            node.path("exercise_id").asLong(),
                            node.path("exercise_name").asText()));
                }
            } else if (exerciseNode.isObject()) {
                exercises.add(new FatSecretExerciseDto(
                        exerciseNode.path("exercise_id").asLong(),
                        exerciseNode.path("exercise_name").asText()));
            }
            return exercises;
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("Failed to fetch exercises from FatSecret", e);
        }
    }

    @Override
    public List<FatSecretExerciseEntryDto> getExerciseEntries(FatSecretToken token, long daysSinceEpoch) {
        try {
            OAuthRequest request = request(Verb.GET, "https://platform.fatsecret.com/rest/exercise-entries/v2");
            request.addQuerystringParameter("date", String.valueOf(daysSinceEpoch));
            request.addQuerystringParameter("format", "json");

            Response response = execute(token, request);
            ensureSuccessful(response, "exercise entries");

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
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException("Failed to fetch exercise entries from FatSecret", e);
        }
    }

    private OAuth10aService createService() {
        return new ServiceBuilder(consumerKey)
                .apiSecret(consumerSecret)
                .callback(callbackUrl)
                .build(FatSecretLegacyApi.instance());
    }

    private OAuthRequest request(Verb verb, String url) {
        return new OAuthRequest(verb, url);
    }

    private Response execute(FatSecretToken token, OAuthRequest request) throws Exception {
        OAuth1AccessToken scribeToken = new OAuth1AccessToken(token.accessToken(), token.accessTokenSecret());
        OAuth10aService service = createService();
        service.signRequest(scribeToken, request);
        return service.execute(request);
    }

    private void ensureSuccessful(Response response, String operation) {
        if (!response.isSuccessful()) {
            throw new ExternalApiException(
                    "FatSecret " + operation + " request failed with status " + response.getCode(),
                    null);
        }
    }

    NutritionMonthFetchResult parseMonthResponse(String jsonBody, Long userId) {
        try {
            JsonNode root = objectMapper.readTree(jsonBody);
            if (root == null || !root.isObject()) {
                return NutritionMonthFetchResult.malformed();
            }
            if (root.has("error")) {
                return root.get("error").isObject()
                        ? NutritionMonthFetchResult.providerError()
                        : NutritionMonthFetchResult.malformed();
            }
            JsonNode monthNode = root.get("month");
            if (monthNode == null || !monthNode.isObject()) {
                return NutritionMonthFetchResult.malformed();
            }
            JsonNode dayNode = monthNode.get("day");
            if (dayNode == null) {
                return NutritionMonthFetchResult.malformed();
            }
            List<NutritionDaySummary> days = new ArrayList<>();

            if (dayNode.isArray()) {
                if (dayNode.isEmpty()) {
                    return NutritionMonthFetchResult.authoritativeEmpty(new NutritionMonth(userId, List.of()));
                }
                for (JsonNode node : dayNode) {
                    days.add(mapDayNode(node, userId));
                }
            } else if (dayNode.isObject()) {
                days.add(mapDayNode(dayNode, userId));
            } else {
                return NutritionMonthFetchResult.malformed();
            }
            return NutritionMonthFetchResult.valid(new NutritionMonth(userId, days));
        } catch (Exception e) {
            return NutritionMonthFetchResult.malformed();
        }
    }

    private NutritionDaySummary mapDayNode(JsonNode node, Long userId) {
        if (!node.isObject()) {
            throw new IllegalArgumentException("Month day must be an object");
        }
        int dateInt = requiredInt(node, "date_int");
        return new NutritionDaySummary(
                userId,
                LocalDate.ofEpochDay(dateInt),
                dateInt,
                requiredDouble(node, "calories"),
                requiredDouble(node, "protein"),
                requiredDouble(node, "fat"),
                requiredDouble(node, "carbohydrate"));
    }

    private int requiredInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException(field + " is required");
        }
        try {
            return Integer.parseInt(value.asText());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be an integer", e);
        }
    }

    private double requiredDouble(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException(field + " is required");
        }
        try {
            double result = Double.parseDouble(value.asText());
            if (!Double.isFinite(result)) {
                throw new IllegalArgumentException(field + " must be finite");
            }
            return result;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be numeric", e);
        }
    }

    private List<WeightEntryDto> parseWeightHistoryResponse(String jsonResponse) {
        List<WeightEntryDto> entries = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            if (root.has("error")) {
                log.warn("FatSecret getWeightHistory returned errorCode={}", root.path("error").path("code").asText("UNKNOWN"));
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
            throw new ExternalApiException("Failed to parse FatSecret weight history JSON", e);
        }
        return entries;
    }

    private WeightEntryDto mapWeightDayNode(JsonNode node) {
        int dateInt = node.path("date_int").asInt();
        return new WeightEntryDto(
                new BigDecimal(node.path("weight_kg").asText()),
                LocalDate.ofEpochDay(dateInt),
                dateInt,
                node.path("weight_comment").asText(null));
    }

    private FatSecretExerciseEntryDto mapExerciseEntryNode(JsonNode node) {
        return new FatSecretExerciseEntryDto(
                node.path("exercise_id").asLong(),
                node.path("exercise_name").asText(),
                node.path("minutes").asInt(),
                new BigDecimal(node.path("calories").asText()),
                node.path("is_template_value").asInt() == 1);
    }

    private NutritionDay parseJsonToNutritionDay(String jsonBody, Long userId, LocalDate date) {
        List<FoodEntry> entries = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(jsonBody);
            JsonNode foodEntriesNode = root.path("food_entries").path("food_entry");

            if (foodEntriesNode.isArray()) {
                for (JsonNode node : foodEntriesNode) {
                    entries.add(mapFoodEntryNode(node));
                }
            } else if (foodEntriesNode.isObject()) {
                entries.add(mapFoodEntryNode(foodEntriesNode));
            }
            log.debug("Parsed FatSecret food entries count={} userId={} date={}", entries.size(), userId, date);
        } catch (Exception e) {
            throw new ExternalApiException("Failed to parse FatSecret JSON", e);
        }
        return new NutritionDay(userId, date, entries);
    }

    private FoodEntry mapFoodEntryNode(JsonNode node) {
        return new FoodEntry(
                node.path("food_id").asLong(),
                node.path("food_entry_id").asLong(),
                node.path("food_entry_name").asText(),
                node.path("meal").asText(),
                node.path("calories").asInt(),
                node.path("protein").asDouble(),
                node.path("fat").asDouble(),
                node.path("carbohydrate").asDouble());
    }
}
