package unified.qol.core;

/** Core services available to a feature while it runs. */
public interface FeatureContext {
    /** The current effective settings; read on use, since the Core may replace the snapshot. */
    SettingsSnapshot settings();
}
