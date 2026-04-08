package com.fit.fitnessapp.ai.application.port.out;

public interface AiModelPort {
    //Default
    String generate(String prompt);

    String generate(String prompt, String modelName);
}