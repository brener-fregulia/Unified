package unified.qol.core;

import unified.qol.core.compat.CompatibilityReport;
import unified.qol.core.compat.FeatureCompatibility;
import unified.qol.core.compat.ModPresence;
import unified.qol.core.config.ConfigurationService;
import unified.qol.core.config.FeatureChoice;
import unified.qol.core.config.LoadStatus;
import unified.qol.core.config.SaveResult;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Connects the registry, configuration, compatibility detection and lifecycle, and offers the Save → Apply operation for the
 * future settings screen (TX-5, TX-6). Every operation must run on the client thread (LC-7) and fails
 * fast otherwise.
 */
public final class CoreRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger("qol_unified");

    private final FeatureRegistry registry;
    private final ConfigurationService configuration;
    private final CompatibilityReport compatibility;
    private final FeatureLifecycle lifecycle;
    private final Thread clientThread;

    private CoreRuntime(FeatureRegistry registry, ConfigurationService configuration,
            CompatibilityReport compatibility, FeatureLifecycle lifecycle, Thread clientThread) {
        this.registry = registry;
        this.configuration = configuration;
        this.compatibility = compatibility;
        this.lifecycle = lifecycle;
        this.clientThread = clientThread;
    }

    /**
     * Loads configuration, detects installed conflicts, resolves startup choices, initializes the
     * lifecycle and starts enabled features. Conflict detection never writes configuration.
     */
    public static CoreRuntime start(FeatureRegistry registry, ConfigurationService configuration,
            ModPresence installedMods, Thread clientThread) {
        Objects.requireNonNull(clientThread, "clientThread");
        requireThread(clientThread);
        LoadStatus status = configuration.load();
        CompatibilityReport compatibility =
                CompatibilityReport.detectAndLog(registry, installedMods, configuration.choices());
        var desired = new LinkedHashMap<String, DesiredFeature>();
        configuration.choices().forEach((id, choice) -> desired.put(id, compatibility.resolve(id, choice).toDesired()));
        var lifecycle = new FeatureLifecycle(registry, desired);
        ApplyReport report = lifecycle.startup();
        LOGGER.info("Configuration {}{}; {} of {} features active", status,
                status.readOnly() ? " (read-only)" : "",
                report.outcomes().stream().filter(o -> o.state() == FeatureState.ACTIVE).count(),
                registry.features().size());
        return new CoreRuntime(registry, configuration, compatibility, lifecycle, clientThread);
    }

    /**
     * Validates and saves a draft, then applies runtime changes only if the save persisted. A failed
     * runtime transition does not undo the save (TX-7).
     *
     * @throws IllegalArgumentException if the draft is invalid; nothing is saved or applied
     */
    public CommitResult saveAndApply(Map<String, FeatureChoice> draft) {
        requireThread(clientThread);
        SaveResult saved = configuration.save(draft);
        if (!saved.persisted()) {
            return new CommitResult(saved, new ApplyReport(java.util.List.of()));
        }
        var changes = new LinkedHashMap<String, DesiredFeature>();
        draft.forEach((id, choice) -> changes.put(id,
                compatibility.resolve(id, configuration.choices().get(id)).toDesired()));
        return new CommitResult(saved, lifecycle.apply(changes));
    }

    /** Explicit player retry of a failed disable (LC-5). */
    public TransitionOutcome retryDisable(String featureId) {
        requireThread(clientThread);
        return lifecycle.retryDisable(featureId);
    }

    public FeatureRegistry registry() {
        return registry;
    }

    /** Read access to lifecycle state; mutations go through this class. */
    public FeatureLifecycle lifecycle() {
        return lifecycle;
    }

    /**
     * Current choices with conflict defaults applied (CMP-2). A settings draft should start from
     * these; unchanged entries are not written, because conflict defaults are not explicit choices.
     */
    public Map<String, FeatureChoice> choices() {
        var resolved = new LinkedHashMap<String, FeatureChoice>();
        configuration.choices().forEach((id, choice) -> resolved.put(id, compatibility.resolve(id, choice)));
        return java.util.Collections.unmodifiableMap(resolved);
    }

    /** Conflict status of one feature for the settings UI. */
    public FeatureCompatibility compatibility(String featureId) {
        registry.require(featureId);
        return compatibility.status(featureId, configuration.choices().get(featureId));
    }

    /** All conflicts detected at startup. */
    public CompatibilityReport compatibilityReport() {
        return compatibility;
    }

    public LoadStatus configurationStatus() {
        return configuration.status();
    }

    public boolean configurationReadOnly() {
        return configuration.readOnly();
    }

    private static void requireThread(Thread owner) {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("Core operations must run on the client thread (current: "
                    + Thread.currentThread().getName() + ")");
        }
    }

    /** Save outcome and runtime outcomes of one commit. {@code report} is empty when nothing was persisted. */
    public record CommitResult(SaveResult save, ApplyReport report) {
    }
}
