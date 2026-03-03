package com.fit.fitnessapp.api;

import com.github.scribejava.core.builder.api.DefaultApi10a;
import com.github.scribejava.core.builder.api.OAuth1SignatureType;
import com.github.scribejava.core.model.OAuth1RequestToken;
import com.github.scribejava.core.model.Verb;

public class FatSecretLegacyApi extends DefaultApi10a {

    private static class InstanceHolder {
        private static final FatSecretLegacyApi INSTANCE = new FatSecretLegacyApi();
    }

    public static FatSecretLegacyApi instance() {
        return InstanceHolder.INSTANCE;
    }

    @Override
    public String getRequestTokenEndpoint() {
        return "https://authentication.fatsecret.com/oauth/request_token";
    }

    @Override
    public String getAccessTokenEndpoint() {
        return "https://authentication.fatsecret.com/oauth/access_token";
    }

    @Override
    protected String getAuthorizationBaseUrl() {
        return "https://authentication.fatsecret.com/oauth/authorize";
    }

    @Override
    public String getAuthorizationUrl(OAuth1RequestToken requestToken) {
        return getAuthorizationBaseUrl() + "?oauth_token=" + requestToken.getToken();
    }

    // CRITICAL: Override to use POST for request token (FatSecret requires POST)
    @Override
    public Verb getRequestTokenVerb() {
        return Verb.GET;
    }

    // Access token should also use POST
    @Override
    public Verb getAccessTokenVerb() {
        return Verb.GET;
    }
    @Override
    public OAuth1SignatureType getSignatureType() {
        return OAuth1SignatureType.QUERY_STRING;
    }
}