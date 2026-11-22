package com.example.waitlist;

import java.util.Objects;

/** Opaque student identifier. Blank ids are rejected; surrounding whitespace is trimmed. */
public record StudentId(String value) {

    public StudentId {
        Objects.requireNonNull(value, "value");
        value = value.strip();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Student id must not be blank");
        }
    }

    public static StudentId of(String value) {
        return new StudentId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
