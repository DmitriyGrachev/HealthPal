package com.fit.fitnessapp.ai.adapter.out.experiment;

import com.fit.fitnessapp.ai.AiDataClass;
import com.fit.fitnessapp.ai.AiEgressPolicy;
import com.fit.fitnessapp.ai.AiExecutionGuard;
import com.fit.fitnessapp.ai.AiPromptRenderer;
import com.fit.fitnessapp.ai.AiProperties;
import com.fit.fitnessapp.ai.ClassifiedAiPrompt;
import com.fit.fitnessapp.ai.application.service.AiSafetyService;
import com.fit.fitnessapp.experiment.spi.ExperimentDraft;
import com.fit.fitnessapp.experiment.spi.ExperimentDraftGenerator;
import com.fit.fitnessapp.experiment.spi.ExperimentDraftRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Optional, grounded AI proposal adapter. The manual experiment flow does not depend on this bean. */
@Component
public final class AiExperimentDraftGenerator implements ExperimentDraftGenerator {

    private static final String PROMPT_TEMPLATE = "experiment-proposal-v1.md";
    private static final int RESERVED_ATTEMPTS = 1;
    private static final BeanOutputConverter<AiExperimentDraftValidator.Candidate> OUTPUT_CONVERTER =
            new BeanOutputConverter<>(AiExperimentDraftValidator.Candidate.class);

    private final boolean enabled;
    private final AiPromptRenderer promptRenderer;
    private final AiSafetyService safetyService;
    private final AiEgressPolicy egressPolicy;
    private final AiExecutionGuard executionGuard;
    private final AiExperimentDraftValidator validator;
    private final DraftModelOperation modelOperation;

    @Autowired
    public AiExperimentDraftGenerator(
            AiProperties properties,
            AiPromptRenderer promptRenderer,
            AiSafetyService safetyService,
            AiEgressPolicy egressPolicy,
            AiExecutionGuard executionGuard,
            @Qualifier("openRouterChatClient") ChatClient chatClient,
            AiExperimentDraftValidator validator) {
        this(properties.experimentDraftEnabled(), promptRenderer, safetyService, egressPolicy,
                executionGuard, validator, modelOperation(chatClient, properties.QUICK_ANALYSIS_MODEL()));
    }

    /** Package-private seam for offline tests; it never creates a provider client. */
    AiExperimentDraftGenerator(
            boolean enabled,
            AiPromptRenderer promptRenderer,
            AiSafetyService safetyService,
            AiEgressPolicy egressPolicy,
            AiExecutionGuard executionGuard,
            AiExperimentDraftValidator validator,
            DraftModelOperation modelOperation) {
        this.enabled = enabled;
        this.promptRenderer = Objects.requireNonNull(promptRenderer, "promptRenderer is required");
        this.safetyService = Objects.requireNonNull(safetyService, "safetyService is required");
        this.egressPolicy = Objects.requireNonNull(egressPolicy, "egressPolicy is required");
        this.executionGuard = Objects.requireNonNull(executionGuard, "executionGuard is required");
        this.validator = Objects.requireNonNull(validator, "validator is required");
        this.modelOperation = Objects.requireNonNull(modelOperation, "modelOperation is required");
    }

    @Override
    public Optional<ExperimentDraft> generate(ExperimentDraftRequest request) {
        Objects.requireNonNull(request, "request is required");
        if (!enabled || !request.sufficientForDraft()) {
            return Optional.empty();
        }
        if (safetyService.hasMedicalRedFlags(request.untrustedProblemText())) {
            return Optional.empty();
        }

        String wrappedQuestion = safetyService.wrapUntrusted(
                AiSafetyService.UntrustedDataType.USER_QUESTION,
                request.untrustedProblemText());
        String prompt = promptRenderer.render(PROMPT_TEMPLATE, promptVariables(request, wrappedQuestion))
                + "\n\n" + OUTPUT_CONVERTER.getFormat();

        // This check intentionally precedes the execution guard and provider call.
        egressPolicy.validate(new ClassifiedAiPrompt(prompt, AiDataClass.SENSITIVE));

        AiExperimentDraftValidator.Candidate candidate;
        try {
            candidate = executionGuard.execute(
                    request.userId(), prompt, RESERVED_ATTEMPTS, () -> modelOperation.generate(prompt));
        } catch (RuntimeException providerOrInfrastructureFailure) {
            // Provider outages, malformed/empty provider responses, budget, bulkhead and timeout failures
            // are availability outcomes. Semantic validation happens below and deliberately propagates.
            return Optional.empty();
        }
        if (candidate == null) {
            return Optional.empty();
        }
        return Optional.of(validator.validate(request, candidate));
    }

    private Map<String, Object> promptVariables(ExperimentDraftRequest request, String wrappedQuestion) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("baselineStartDate", request.baselineStartDate());
        variables.put("baselineEndDate", request.baselineEndDate());
        variables.put("durationDays", request.durationDays());
        variables.put("primaryMetric", request.primaryMetric());
        variables.put("outcomeDirection", request.outcomeDirection());
        variables.put("meaningfulChange", request.meaningfulChange());
        variables.put("activeGoal", request.activeGoal());
        variables.put("userAuthoredContext", safetyService.wrapUntrusted(
                AiSafetyService.UntrustedDataType.USER_NOTE,
                "goalName=" + request.goalName()
                        + "\ncurrentHypothesis=" + request.currentHypothesis()
                        + "\ncurrentIntervention=" + request.currentIntervention()
                        + "\ncurrentStopConditions=" + request.currentStopConditions()));
        variables.put("coverage", request.coverage());
        variables.put("evidenceRefs", safeEvidenceRefs(request));
        variables.put("missingFields", request.missingFields());
        variables.put("userQuestion", wrappedQuestion);
        return variables;
    }

    private List<Map<String, Object>> safeEvidenceRefs(ExperimentDraftRequest request) {
        return request.evidenceRefs().stream()
                .map(reference -> {
                    Map<String, Object> safe = new LinkedHashMap<>();
                    safe.put("referenceId", reference.referenceId());
                    safe.put("sourceType", reference.sourceType());
                    safe.put("sourceVersion", reference.sourceVersion());
                    safe.put("observedAt", reference.observedAt());
                    return safe;
                })
                .toList();
    }

    private static DraftModelOperation modelOperation(ChatClient chatClient, String modelName) {
        Objects.requireNonNull(chatClient, "chatClient is required");
        return prompt -> {
            var requestSpec = chatClient.prompt().user(prompt);
            if (modelName != null && !modelName.isBlank()) {
                requestSpec = requestSpec.options(OpenAiChatOptions.builder().model(modelName));
            }
            String content = requestSpec.call().content();
            if (content == null || content.isBlank()) {
                return null;
            }
            return OUTPUT_CONVERTER.convert(content);
        };
    }

    @FunctionalInterface
    interface DraftModelOperation {
        AiExperimentDraftValidator.Candidate generate(String prompt);
    }
}
