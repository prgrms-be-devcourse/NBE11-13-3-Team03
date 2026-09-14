package com.team3.gudit.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/** Intentionally fails Spring context initialization for PR 5 only. DO NOT MERGE. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = CiClassifierValidationTest.BrokenValidationConfiguration.class)
class CiClassifierValidationTest {

    @Configuration
    static class BrokenValidationConfiguration {
        @Bean
        Object ciAnalyzerValidationBean() {
            throw new IllegalStateException(
                    "CI_AI_ANALYZER_VALIDATION: ambiguous application context failure");
        }
    }

    @Test
    void intentionalContextFailureForCiAnalyzer() {
        // The test method is never reached because the validation bean fails at context startup.
    }
}
