package com.fit.fitnessapp.ai;
import com.fit.fitnessapp.auth.CurrentUserApi;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiterService rateLimiterService;
    private final CurrentUserApi currentUserApi;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        try {
            Long userId = currentUserApi.getCurrentUserId();
            Bucket tokenBucket = rateLimiterService.resolveBucket(userId);

            if (tokenBucket.tryConsume(1)) {
                return true; // Пропускаем запрос
            } else {
                response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "Слишком много запросов. Подождите.");
                return false; // Блокируем
            }
        } catch (Exception e) {
            return true; // Если юзер не авторизован (например, логин), пропускаем
        }
    }
}