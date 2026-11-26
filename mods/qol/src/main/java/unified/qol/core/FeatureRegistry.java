package unified.qol.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Explicit, read-only list of features in registration order (M0 §3.5). */
public final class FeatureRegistry {
    private final List<Feature> features;
    private final Map<String, Feature> byId;

    private FeatureRegistry(List<Feature> features, Map<String, Feature> byId) {
        this.features = List.copyOf(features);
        this.byId = Map.copyOf(byId);
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<Feature> features() {
        return features;
    }

    public Optional<Feature> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Feature require(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown feature: " + id));
    }

    /** Collects registrations; {@link #build()} validates and freezes them. */
    public static final class Builder {
        private final List<Feature> features = new ArrayList<>();
        private final Map<String, Feature> byId = new LinkedHashMap<>();
        private boolean built;

        private Builder() {
        }

        public Builder register(Feature feature) {
            if (built) {
                throw new IllegalStateException("Feature registry is frozen");
            }
            Objects.requireNonNull(feature, "feature");
            FeatureDescriptor descriptor = Objects.requireNonNull(feature.descriptor(), "descriptor");
            if (byId.putIfAbsent(descriptor.id(), feature) != null) {
                throw new IllegalArgumentException("Duplicate feature id: " + descriptor.id());
            }
            features.add(feature);
            return this;
        }

        public FeatureRegistry build() {
            if (built) {
                throw new IllegalStateException("Feature registry is frozen");
            }
            built = true;
            return new FeatureRegistry(features, byId);
        }
    }
}
