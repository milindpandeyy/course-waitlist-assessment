package com.assessment.enrollment.service;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * ID handling shared by both services (DESIGN.md sections 4 and 5 "Input handling"): one place
 * for the blank check and the normalized form that makes IDs case-insensitive.
 */
final class Ids {

    private Ids() {
    }

    /** True if {@code id} is null, empty or only whitespace. */
    static boolean isBlank(String id) {
        return id == null || id.isBlank();
    }

    /**
     * Canonical form of an ID: surrounding whitespace removed, lower-cased (locale-independent),
     * so {@code " Alice "} and {@code "alice"} are the same ID. {@code id} must not be null.
     */
    static String normalize(String id) {
        return id.strip().toLowerCase(Locale.ROOT);
    }

    /** {@link #normalize Normalized} copy of {@code ids}; IDs differing only in case merge. */
    static Set<String> normalizeAll(Set<String> ids) {
        Set<String> normalized = new HashSet<>();
        for (String id : ids) {
            normalized.add(normalize(id));
        }
        return normalized;
    }
}
