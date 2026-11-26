package unified.qol.core;

import java.time.Instant;
import java.util.Objects;

/** The last failed lifecycle operation of a feature (M0 §4.2). */
public record FailureRecord(Operation operation, boolean rollbackFailed, String exceptionType, Instant time) {
    public enum Operation {
        ENABLE,
        DISABLE
    }

    public FailureRecord {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(exceptionType, "exceptionType");
        Objects.requireNonNull(time, "time");
    }
}
