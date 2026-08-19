package com.fit.fitnessapp.module;

import com.fit.fitnessapp.FitnessAppApplication;
import com.fit.fitnessapp.api.lifecycle.DataRetentionDisclosure;
import com.fit.fitnessapp.api.lifecycle.UserDataExportFragment;
import com.fit.fitnessapp.api.lifecycle.UserDataLifecycleParticipant;
import com.fit.fitnessapp.job.application.service.DurableJobLeaseHeartbeat;
import com.fit.fitnessapp.job.application.service.DurableJobService;
import com.fit.fitnessapp.job.application.service.DurableJobWorker;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleArchitectureTest {

    private final ApplicationModules modules = ApplicationModules.of(FitnessAppApplication.class);

    @Test
    void verifyArchitecture() {
        // Verifies that there are no circular dependencies and that internal packages are not leaked.
        modules.verify();
    }

    @Test
    void lifecycleNamedInterfaceExposesNeutralLifecycleContracts() {
        ApplicationModule api = modules.getModuleByName("api").orElseThrow();
        var lifecycle = api.getNamedInterfaces().getByName("lifecycle").orElseThrow();

        assertThat(lifecycle.contains(UserDataLifecycleParticipant.class)).isTrue();
        assertThat(lifecycle.contains(UserDataExportFragment.class)).isTrue();
        assertThat(lifecycle.contains(DataRetentionDisclosure.class)).isTrue();
    }

    @Test
    void durableJobLeaseNamedInterfaceContainsOnlyHeartbeatType() {
        ApplicationModule job = modules.getModuleByName("job").orElseThrow();
        var leases = job.getNamedInterfaces().getByName("durable-job-leases").orElseThrow();

        assertThat(leases.contains(DurableJobLeaseHeartbeat.class)).isTrue();
        assertThat(leases.contains(DurableJobLeaseHeartbeat.Registration.class)).isTrue();
        assertThat(leases.contains(DurableJobService.class)).isFalse();
        assertThat(leases.contains(DurableJobWorker.class)).isFalse();
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
