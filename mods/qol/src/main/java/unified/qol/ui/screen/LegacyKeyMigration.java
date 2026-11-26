package unified.qol.ui.screen;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unified.settings.UnifiedSettingsClient;

/**
 * Moves a player's custom binding of the former "Open Unified: QoL Settings" key ({@value #LEGACY_KEY}, unbound by
 * default and no longer registered) to the shared "Open Unified Settings" key, once
 * (docs/architecture/unified-settings-integration.md §20.6).
 *
 * <p>Minecraft keeps options.txt; this class only reads it, during client initialization. When the legacy line names a key and the shared key is
 * still at its default, the shared key is rebound through {@link KeyMapping#setKey} and Minecraft saves its own
 * options. That save no longer writes the unregistered legacy line, so the migration cannot run twice. A customized
 * shared key is never overwritten. Any failure leaves every binding as it was.
 */
public final class LegacyKeyMigration {
    public static final String LEGACY_KEY = "key.qol_unified.open_settings";
    private static final String PREFIX = "key_" + LEGACY_KEY + ":";
    private static final Logger LOGGER = LoggerFactory.getLogger("qol_unified");

    private LegacyKeyMigration() {
    }

    /**
     * Reads the legacy line now, during client initialization, before Minecraft or another mod first saves its options
     * (that save drops the unregistered line); the binding is applied once the client has started.
     */
    public static void register() {
        Optional<String> legacy = read(FabricLoader.getInstance().getGameDir().resolve("options.txt"));
        if (legacy.isPresent()) {
            ClientLifecycleEvents.CLIENT_STARTED.register(minecraft -> run(minecraft, legacy));
        }
    }

    private static Optional<String> read(Path options) {
        try {
            return Files.isRegularFile(options)
                    ? legacyBinding(Files.readAllLines(options, StandardCharsets.UTF_8)) : Optional.empty();
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not read the legacy settings key binding; bindings unchanged", e);
            return Optional.empty();
        }
    }

    /** The legacy key's saved value in options.txt lines, when it names a key (not unbound). */
    public static Optional<String> legacyBinding(List<String> optionLines) {
        for (String line : optionLines) {
            if (line.startsWith(PREFIX)) {
                String value = line.substring(PREFIX.length()).strip();
                return value.isEmpty() || value.equals(InputConstants.UNKNOWN.getName())
                        ? Optional.empty() : Optional.of(value);
            }
        }
        return Optional.empty();
    }

    /** Whether the shared key should take {@code legacy}: only while it is still at its default. */
    public static boolean shouldMigrate(Optional<String> legacy, boolean sharedIsDefault) {
        return legacy.isPresent() && sharedIsDefault;
    }

    /** Runs once at client start, after Minecraft loaded its options. */
    private static void run(Minecraft minecraft, Optional<String> legacy) {
        try {
            KeyMapping shared = UnifiedSettingsClient.getOpenKey();
            if (shared == null) {
                return;
            }
            if (!shouldMigrate(legacy, shared.isDefault())) {
                if (legacy.isPresent()) {
                    LOGGER.info("Legacy settings key {} not migrated: {} is customized", legacy.get(),
                            UnifiedSettingsClient.KEY_OPEN);
                }
                return;
            }
            InputConstants.Key key = InputConstants.getKey(legacy.get());
            shared.setKey(key);
            KeyMapping.resetMapping();
            minecraft.options.save();
            LOGGER.info("Moved the legacy settings key binding ({}) to {}", legacy.get(),
                    UnifiedSettingsClient.KEY_OPEN);
        } catch (RuntimeException e) {
            LOGGER.warn("Could not migrate the legacy settings key binding; bindings unchanged", e);
        }
    }
}
