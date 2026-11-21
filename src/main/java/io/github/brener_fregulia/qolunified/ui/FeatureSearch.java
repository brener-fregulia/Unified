package io.github.brener_fregulia.qolunified.ui;

import io.github.brener_fregulia.qolunified.core.Feature;
import io.github.brener_fregulia.qolunified.core.FeatureCategory;
import io.github.brener_fregulia.qolunified.core.FeatureDescriptor;
import io.github.brener_fregulia.qolunified.core.FeatureRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/** Category filtering and search for the feature list (UI-1, UI-3). */
public final class FeatureSearch {
    private FeatureSearch() {
    }

    /** One visible row: a category header (feature null) or a feature. */
    public record Row(FeatureCategory category, FeatureDescriptor feature) {
        public boolean header() {
            return feature == null;
        }
    }

    /** Case-insensitive match against localized name, localized description and id. */
    public static boolean matches(String query, String name, String description, String id) {
        String q = normalize(query);
        return q.isEmpty() || normalize(name).contains(q) || normalize(description).contains(q)
                || normalize(id).contains(q);
    }

    /** Categories that have at least one feature, in catalog order. */
    public static List<FeatureCategory> categories(FeatureRegistry registry) {
        return java.util.Arrays.stream(FeatureCategory.values())
                .filter(c -> registry.features().stream().anyMatch(f -> f.descriptor().category() == c))
                .toList();
    }

    /**
     * Rows to show. A blank query shows the selected category without headers. A search spans all
     * categories and keeps category context with a header before each group. Order is catalog order,
     * then registry order.
     */
    public static List<Row> rows(FeatureRegistry registry, FeatureCategory selected, String query,
            Function<String, String> translate) {
        boolean searching = !normalize(query).isEmpty();
        var rows = new ArrayList<Row>();
        for (FeatureCategory category : FeatureCategory.values()) {
            if (!searching && category != selected) {
                continue;
            }
            boolean headerAdded = false;
            for (Feature feature : registry.features()) {
                FeatureDescriptor d = feature.descriptor();
                if (d.category() != category) {
                    continue;
                }
                if (searching && !matches(query, translate.apply(d.nameKey()), translate.apply(d.descriptionKey()),
                        d.id())) {
                    continue;
                }
                if (searching && !headerAdded) {
                    rows.add(new Row(category, null));
                    headerAdded = true;
                }
                rows.add(new Row(category, d));
            }
        }
        return rows;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
    }
}
