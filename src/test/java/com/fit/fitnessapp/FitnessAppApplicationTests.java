package com.fit.fitnessapp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class FitnessAppApplicationTests {

    @Test
    void contextLoads(CapturedOutput output) {
        org.assertj.core.api.Assertions.assertThat(output)
                .doesNotContain("Spring Data JDBC - Could not safely identify store assignment")
                .doesNotContain("FatSecret Configuration Check");
    }
}
