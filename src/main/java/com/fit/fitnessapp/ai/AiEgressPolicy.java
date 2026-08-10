package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.ai.exception.AiEgressDeniedException;
import com.fit.fitnessapp.api.SensitiveAiEgressGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AiEgressPolicy implements SensitiveAiEgressGuard {

    private final boolean allowSensitiveExternalEgress;

    @Autowired
    public AiEgressPolicy(AiProperties properties) {
        this(properties.allowSensitiveExternalEgress());
    }

    AiEgressPolicy(boolean allowSensitiveExternalEgress) {
        this.allowSensitiveExternalEgress = allowSensitiveExternalEgress;
    }

    public AiDataClass validate(ClassifiedAiPrompt prompt) {
        if (prompt == null || prompt.classification() == null) {
            throw denied("AI_UNCLASSIFIED_EGRESS_DENIED", "AI prompt must have a data classification");
        }
        return switch (prompt.classification()) {
            case PUBLIC -> AiDataClass.PUBLIC;
            case SENSITIVE -> {
                if (!allowSensitiveExternalEgress) {
                    throw denied("AI_SENSITIVE_EGRESS_DISABLED", "Sensitive external AI egress is disabled");
                }
                yield AiDataClass.SENSITIVE;
            }
            case SECRET -> throw denied("AI_SECRET_EGRESS_DENIED", "Secret data must never leave the application");
        };
    }

    @Override
    public void validateSensitiveEgress() {
        validate(new ClassifiedAiPrompt("", AiDataClass.SENSITIVE));
    }

    private AiEgressDeniedException denied(String code, String message) {
        return new AiEgressDeniedException(code, message);
    }
}
