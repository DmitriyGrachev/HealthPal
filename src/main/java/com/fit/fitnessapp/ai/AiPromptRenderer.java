package com.fit.fitnessapp.ai;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Component
public class AiPromptRenderer {

    private static final Pattern UNRESOLVED_PLACEHOLDER = Pattern.compile("\\{\\{[^}]+}}");

    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    public String render(String templateName, Map<String, ?> variables) {
        String rendered = template(templateName);
        for (Map.Entry<String, ?> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            rendered = rendered.replace(placeholder, String.valueOf(entry.getValue()));
        }
        if (UNRESOLVED_PLACEHOLDER.matcher(rendered).find()) {
            throw new IllegalArgumentException("Prompt template has unresolved placeholders: " + templateName);
        }
        return rendered;
    }

    private String template(String templateName) {
        return templateCache.computeIfAbsent(templateName, this::loadTemplate);
    }

    private String loadTemplate(String templateName) {
        ClassPathResource resource = new ClassPathResource("ai/prompts/" + templateName);
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load AI prompt template: " + templateName, e);
        }
    }
}
