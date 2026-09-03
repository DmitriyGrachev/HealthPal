package com.fit.fitnessapp.knowledge;

import com.fit.fitnessapp.FitnessAppApplication;
import com.fit.fitnessapp.knowledge.api.KnowledgeClaimChangedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeModuleArchitectureTest {
    private final ApplicationModules modules = ApplicationModules.of(FitnessAppApplication.class);

    @Test
    void knowledgeUsesOnlyLifecycleAndCurrentUserContracts() {
        ApplicationModule knowledge = modules.getModuleByName("knowledge").orElseThrow();

        assertThat(knowledge.getAllowedDependencies(modules).stream()
                .map(Object::toString)
                .map(value -> value.replace(" ", ""))
                .toList()).containsExactlyInAnyOrder("api::lifecycle", "auth::current-user");
        assertThat(knowledge.getDirectDependencies(modules).stream()
                .map(dependency -> dependency.getTargetModule().getIdentifier().toString())
                .distinct()
                .toList()).containsOnly("api", "auth");
    }

    @Test
    void knowledgeExposesOnlyStableApiAndProjectionSpiInterfaces() {
        ApplicationModule knowledge = modules.getModuleByName("knowledge").orElseThrow();

        assertThat(knowledge.getNamedInterfaces().stream()
                .map(namedInterface -> namedInterface.getName())
                .toList()).contains("api", "projection-spi");
        assertThat(knowledge.getNamedInterfaces().getByName("api").orElseThrow()
                .contains(KnowledgeClaimChangedEvent.class)).isTrue();
    }

    @Test
    void completeModuleVerificationPasses() {
        modules.verify();
    }
}
