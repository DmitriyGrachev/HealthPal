package com.fit.fitnessapp.experiment;

import com.fit.fitnessapp.FitnessAppApplication;
import com.fit.fitnessapp.experiment.api.GoalActivated;
import com.fit.fitnessapp.experiment.api.InvestigationCreated;
import com.fit.fitnessapp.experiment.api.ExperimentChangedEvent;
import com.fit.fitnessapp.experiment.application.port.in.GoalCommandUseCase;
import com.fit.fitnessapp.experiment.application.port.in.GoalQueryUseCase;
import com.fit.fitnessapp.experiment.application.port.in.ExperimentCommandUseCase;
import com.fit.fitnessapp.experiment.application.port.in.ExperimentQueryUseCase;
import com.fit.fitnessapp.experiment.application.port.in.InvestigationCommandUseCase;
import com.fit.fitnessapp.experiment.application.port.in.InvestigationQueryUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

class ExperimentModuleArchitectureTest {

    private final ApplicationModules modules = ApplicationModules.of(FitnessAppApplication.class);

    @Test
    void experimentModuleHasOnlyTheInitialAllowedDependencies() {
        ApplicationModule experiment = modules.getModuleByName("experiment").orElseThrow();

        assertThat(experiment.getAllowedDependencies(modules).stream()
                .map(Object::toString)
                .map(value -> value.replace(" ", ""))
                .toList())
                .containsExactlyInAnyOrder("auth", "api::lifecycle");

        assertThat(experiment.getDirectDependencies(modules).stream()
                .map(dependency -> dependency.getTargetModule().getIdentifier().toString())
                .distinct()
                .toList())
                .containsOnly("auth", "api");
    }

    @Test
    void experimentExposesTheStableNamedInterfaces() {
        ApplicationModule experiment = modules.getModuleByName("experiment").orElseThrow();

        assertThat(experiment.getNamedInterfaces().stream()
                .map(interfaceType -> interfaceType.getName())
                .toList())
                .contains("api", "command-api", "query-api", "draft-spi");

        var api = experiment.getNamedInterfaces().getByName("api").orElseThrow();
        assertThat(api.contains(InvestigationCreated.class)).isTrue();
        assertThat(api.contains(GoalActivated.class)).isTrue();
        assertThat(api.contains(ExperimentChangedEvent.class)).isTrue();

        var commandApi = experiment.getNamedInterfaces().getByName("command-api").orElseThrow();
        assertThat(commandApi.contains(InvestigationCommandUseCase.class)).isTrue();
        assertThat(commandApi.contains(GoalCommandUseCase.class)).isTrue();
        assertThat(commandApi.contains(ExperimentCommandUseCase.class)).isTrue();

        var queryApi = experiment.getNamedInterfaces().getByName("query-api").orElseThrow();
        assertThat(queryApi.contains(InvestigationQueryUseCase.class)).isTrue();
        assertThat(queryApi.contains(GoalQueryUseCase.class)).isTrue();
        assertThat(queryApi.contains(ExperimentQueryUseCase.class)).isTrue();
    }

    @Test
    void experimentModuleVerificationRejectsForbiddenDependencies() {
        ApplicationModule experiment = modules.getModuleByName("experiment").orElseThrow();

        assertThat(experiment.detectDependencies(modules).getMessages().stream()
                .noneMatch(line -> line.contains("ai")
                        || line.contains("knowledge")
                        || line.contains("memory")
                        || line.contains("telegram")))
                .isTrue();
    }

    @Test
    void completeModuleVerificationPasses() {
        modules.verify();
    }

}
