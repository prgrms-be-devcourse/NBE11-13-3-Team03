package com.team3.gudit.validation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Intentionally failing fixture for PR 5 only. DO NOT MERGE. */
class CiClassifierValidationTest {
    @Test
    void intentionalFailureForCiClassifier() {
        assertEquals(0, -1, "CI_CLASSIFIER_VALIDATION: intentional boundary failure");
    }
}
