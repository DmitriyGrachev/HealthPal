package com.fit.fitnessapp.module;

import com.fit.fitnessapp.FitnessAppApplication;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModuleArchitectureTest {

    private final ApplicationModules modules = ApplicationModules.of(FitnessAppApplication.class);

    @Test
    void verifyArchitecture() {
        // Verifies that there are no circular dependencies and that internal packages are not leaked.
        modules.verify();
    }

    @Test
    @Disabled("Documentation generation is not part of the architecture test gate.")
    void writeDocumentation() {
        // Generates PlantUML diagrams for the modules.
        new Documenter(modules)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml();
    }
}
