package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.ai.exception.AiEgressDeniedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiEgressPolicyTest {

    @Test
    void allowsPublicData() {
        assertThat(new AiEgressPolicy(false).validate(prompt(AiDataClass.PUBLIC)))
                .isEqualTo(AiDataClass.PUBLIC);
    }

    @Test
    void deniesSensitiveDataWithoutExplicitOptIn() {
        assertDenied(new AiEgressPolicy(false), AiDataClass.SENSITIVE, "AI_SENSITIVE_EGRESS_DISABLED");
    }

    @Test
    void allowsSensitiveDataWithExplicitOptIn() {
        assertThat(new AiEgressPolicy(true).validate(prompt(AiDataClass.SENSITIVE)))
                .isEqualTo(AiDataClass.SENSITIVE);
    }

    @Test
    void alwaysDeniesSecretData() {
        assertDenied(new AiEgressPolicy(true), AiDataClass.SECRET, "AI_SECRET_EGRESS_DENIED");
    }

    @Test
    void deniesUnclassifiedData() {
        assertDenied(new AiEgressPolicy(true), null, "AI_UNCLASSIFIED_EGRESS_DENIED");
    }

    @Test
    void deniesMissingRequest() {
        assertThatThrownBy(() -> new AiEgressPolicy(true).validate(null))
                .isInstanceOf(AiEgressDeniedException.class)
                .extracting(error -> ((AiEgressDeniedException) error).code())
                .isEqualTo("AI_UNCLASSIFIED_EGRESS_DENIED");
    }

    private ClassifiedAiPrompt prompt(AiDataClass classification) {
        return new ClassifiedAiPrompt("prompt", classification);
    }

    private void assertDenied(AiEgressPolicy policy, AiDataClass classification, String code) {
        assertThatThrownBy(() -> policy.validate(prompt(classification)))
                .isInstanceOf(AiEgressDeniedException.class)
                .extracting(error -> ((AiEgressDeniedException) error).code())
                .isEqualTo(code);
    }
}
