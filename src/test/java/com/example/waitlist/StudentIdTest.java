package com.example.waitlist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StudentIdTest {

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void rejectsBlank(String value) {
        assertThrows(IllegalArgumentException.class, () -> StudentId.of(value));
    }

    @Test
    void rejectsNull() {
        assertThrows(NullPointerException.class, () -> StudentId.of(null));
    }

    @Test
    void trimsWhitespaceAndUsesValueEquality() {
        assertEquals(StudentId.of("s-1"), StudentId.of("  s-1  "));
        assertEquals("s-1", StudentId.of(" s-1 ").toString());
    }
}
