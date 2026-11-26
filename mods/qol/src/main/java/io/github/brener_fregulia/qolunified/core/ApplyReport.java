package io.github.brener_fregulia.qolunified.core;

import java.util.List;

/** Outcomes of one apply, in execution order. Runtime failures here never imply a save failure (TX-7). */
public record ApplyReport(List<TransitionOutcome> outcomes) {
    public ApplyReport {
        outcomes = List.copyOf(outcomes);
    }

    public boolean hasFailures() {
        return outcomes.stream().anyMatch(TransitionOutcome::failed);
    }
}
