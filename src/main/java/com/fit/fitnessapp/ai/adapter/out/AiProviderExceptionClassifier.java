package com.fit.fitnessapp.ai.adapter.out;

import com.fit.fitnessapp.ai.exception.AiAuthException;
import com.fit.fitnessapp.ai.exception.AiInvalidRequestException;
import com.fit.fitnessapp.ai.exception.AiRateLimitException;
import com.fit.fitnessapp.ai.exception.AiTimeoutException;
import com.fit.fitnessapp.ai.exception.AiUnavailableException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

final class AiProviderExceptionClassifier {

    private AiProviderExceptionClassifier() {
    }

    static RuntimeException translate(String provider, Exception failure) {
        if (failure instanceof AiAuthException
                || failure instanceof AiInvalidRequestException
                || failure instanceof AiRateLimitException
                || failure instanceof AiTimeoutException
                || failure instanceof AiUnavailableException) {
            return (RuntimeException) failure;
        }

        RestClientResponseException responseFailure = findCause(failure, RestClientResponseException.class);
        if (responseFailure != null) {
            int status = responseFailure.getStatusCode().value();
            if (status == 400) {
                return new AiInvalidRequestException(provider + " rejected the request", failure);
            }
            if (status == 401 || status == 403) {
                return new AiAuthException(provider + " authentication failed", failure);
            }
            if (status == 429) {
                return new AiRateLimitException(provider + " rate limit exceeded", failure);
            }
            if (status >= 500) {
                return new AiUnavailableException(provider + " is unavailable", failure);
            }
        }

        if (hasCause(failure, SocketTimeoutException.class)
                || hasCause(failure, HttpTimeoutException.class)
                || hasCause(failure, TimeoutException.class)) {
            return new AiTimeoutException(provider + " request timed out", failure);
        }

        return new AiUnavailableException(provider + " is unavailable", failure);
    }

    private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        return findCause(failure, type) != null;
    }

    private static <T extends Throwable> T findCause(Throwable failure, Class<T> type) {
        Throwable current = failure;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }
}
