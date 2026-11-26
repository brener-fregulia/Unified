package unified.qol.core.compat;

/** A declared conflict whose mod is installed (CMP-1). */
public record DetectedConflict(String featureId, String modId, String reasonKey) {
}
