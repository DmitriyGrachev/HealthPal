package com.fit.fitnessapp.nutrition.adapter.out.persistence.entity;
import com.github.scribejava.core.model.OAuth1RequestToken;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FatSecretAuthState {
    private OAuth1RequestToken requestToken;
    private Long userId;
}