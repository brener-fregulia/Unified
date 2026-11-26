package unified.settings.spike;

/** Contract 1.1: adds order() with a default, so 1.0 providers keep working. */
public interface SettingsProvider {
    String describe();

    default int order() {
        return 100;
    }
}
