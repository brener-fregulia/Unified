package io.github.brener_fregulia.qolunified.core;

import java.util.Objects;

/** A mod known to conflict with a feature (M0 §3.3). Detection is not implemented yet. */
public record ConflictDescriptor(String modId, String reasonKey) {
    public ConflictDescriptor {
        if (modId == null || modId.isBlank()) {
            throw new IllegalArgumentException("Conflict mod id must not be blank");
        }
        Objects.requireNonNull(reasonKey, "reasonKey");
    }
}
