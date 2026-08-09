package com.fit.fitnessapp.nutrition.adapter.out.api;

import com.github.scribejava.core.model.OAuth1RequestToken;

public class FatSecretAuthState {
    private OAuth1RequestToken requestToken;
    private Long userId;

    public FatSecretAuthState() {}

    public FatSecretAuthState(OAuth1RequestToken requestToken, Long userId) {
        this.requestToken = requestToken;
        this.userId = userId;
    }

    public OAuth1RequestToken getRequestToken() { return requestToken; }
    public void setRequestToken(OAuth1RequestToken requestToken) { this.requestToken = requestToken; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
}