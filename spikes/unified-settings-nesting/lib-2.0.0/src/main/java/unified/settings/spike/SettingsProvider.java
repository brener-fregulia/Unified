package unified.settings.spike;

/** Contract 2.0: breaking. describe() is gone and title() is required. */
public interface SettingsProvider {
    String title();

    default int order() {
        return 100;
    }
}
