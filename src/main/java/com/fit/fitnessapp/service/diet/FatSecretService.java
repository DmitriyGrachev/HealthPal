package com.fit.fitnessapp.service.diet;

import com.fit.fitnessapp.api.FatSecretLegacyApi;
import com.fit.fitnessapp.model.fatsecret.UserFatsecretConnection;
import com.fit.fitnessapp.model.fatsecret.FatSecretAuthState;
import com.fit.fitnessapp.model.user.User;
import com.fit.fitnessapp.repository.FatsecretConnectionRepository;
import com.fit.fitnessapp.repository.UserRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.*;
import com.github.scribejava.core.oauth.OAuth10aService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
public class FatSecretService {

    @Value("${fatsecret.consumer-key}")
    private String consumerKey;

    @Value("${fatsecret.consumer-secret}")
    private String consumerSecret;

    @Value("${fatsecret.callback-url}")
    private String callbackUrl;

    private final FatsecretConnectionRepository fatsecretConnectionRepository;
    private final UserRepository userRepository;
    private final Cache<String, FatSecretAuthState> requestTokenCache;

    private OAuth10aService createService() {
        return new ServiceBuilder(consumerKey)
                .apiSecret(consumerSecret)
                .callback(callbackUrl)
                // .debug() // Можно раскомментировать для отладки
                .build(FatSecretLegacyApi.instance());
    }

    // Шаг 1: Получить ссылку на авторизацию
    public String getAuthorizationUrl(Long userId) throws IOException, ExecutionException, InterruptedException {
        OAuth10aService service = createService();
        OAuth1RequestToken requestToken = service.getRequestToken();

        // Сохраняем в кеш: Токен + ID пользователя
        requestTokenCache.put(requestToken.getToken(), new FatSecretAuthState(requestToken, userId));

        return service.getAuthorizationUrl(requestToken);
    }

    // Шаг 2: Обработка колбэка и сохранение в БД
    @Transactional
    public Long processCallback(String oauthToken, String oauthVerifier) throws Exception {
        OAuth10aService service = createService();

        // Достаем из кеша, кто это был
        FatSecretAuthState state = requestTokenCache.getIfPresent(oauthToken);
        if (state == null) {
            throw new IllegalArgumentException("Token not found or expired");
        }
        requestTokenCache.invalidate(oauthToken);

        // Обмениваем на Access Token
        OAuth1AccessToken accessToken = service.getAccessToken(state.getRequestToken(), oauthVerifier);

        // Сохраняем в БД
        User user = userRepository.findById(state.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserFatsecretConnection connection = fatsecretConnectionRepository.findByUserId(user.getId())
                .orElse(new UserFatsecretConnection());

        connection.setUser(user);
        connection.setAccessToken(accessToken.getToken());
        connection.setAccessTokenSecret(accessToken.getTokenSecret());

        fatsecretConnectionRepository.save(connection);

        return user.getId();
    }

    // Шаг 3: Вызов API (читает токен из БД)
    public Response makeApiCall(Long userId, String apiMethod, String dateParam) throws Exception {
        UserFatsecretConnection connection = fatsecretConnectionRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not connected to FatSecret"));

        OAuth10aService service = createService();
        OAuth1AccessToken accessToken = new OAuth1AccessToken(connection.getAccessToken(), connection.getAccessTokenSecret());

        OAuthRequest request = new OAuthRequest(Verb.POST, "https://platform.fatsecret.com/rest/server.api");
        request.addParameter("method", apiMethod);
        request.addParameter("format", "json");

        if (dateParam != null) {
            request.addParameter("date", dateParam);
        }

        service.signRequest(accessToken, request);
        return service.execute(request);
    }
}