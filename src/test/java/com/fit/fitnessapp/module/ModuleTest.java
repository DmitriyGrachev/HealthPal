package com.fit.fitnessapp.module;


import com.fit.fitnessapp.FitnessAppApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

public class ModuleTest {
    ApplicationModules modules = ApplicationModules.of(FitnessAppApplication.class);

    @Test
    void verifyArchitecture() {
        // Эта строчка проверит, что нет циклических зависимостей,
        // и что никто не лезет в чужие приватные пакеты.
        modules.verify();
    }

    @Test
    void writeDocumentation() {
        // Бонус: генерирует UML-диаграммы взаимодействия твоих модулей!
        new Documenter(modules)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml();
    }
}
