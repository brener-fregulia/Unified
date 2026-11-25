package io.github.brener_fregulia.qolunified.ui;

import io.github.brener_fregulia.qolunified.core.FeatureCategory;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The settings workspace's sidebar (settings-ux-v1.md §4.1, §9): categories that can expand and collapse, their
 * features, the selected feature and the search. Pure: it works on translated strings handed in by the screen, never
 * touches the draft or the configuration, and keeps its state for the whole settings session.
 */
public final class SettingsNavigation {
    /** One setting as search sees it: its key, its translated name and the translated labels of its values. */
    public record SettingEntry(String key, String label, List<String> optionLabels) {
        public SettingEntry {
            optionLabels = List.copyOf(optionLabels);
        }
    }

    /** One feature as the sidebar sees it, with translated texts. */
    public record FeatureEntry(FeatureCategory category, String id, String name, String description,
            List<SettingEntry> settings) {
        public FeatureEntry {
            settings = List.copyOf(settings);
        }
    }

    public enum Kind {
        CATEGORY,
        FEATURE,
        SETTING
    }

    /**
     * One visible sidebar row.
     *
     * @param featureId the feature of a FEATURE or SETTING row, else null
     * @param settingKey the setting of a SETTING row (a search result), else null
     * @param count the number of features of a CATEGORY row
     * @param flagged a FEATURE row whose draft has invalid input
     */
    public record Row(Kind kind, FeatureCategory category, String featureId, String settingKey, int count,
            boolean expanded, boolean selected, boolean flagged) {
    }

    private final List<FeatureEntry> features;
    private final Set<FeatureCategory> expanded = EnumSet.noneOf(FeatureCategory.class);
    private Set<FeatureCategory> beforeSearch;
    private String selected;
    private String query = "";

    /**
     * @param features every registered feature, in registry order
     * @param preselect a feature to select first (a contextual entry point), or null for the first feature
     */
    public SettingsNavigation(List<FeatureEntry> features, String preselect) {
        this.features = List.copyOf(features);
        FeatureEntry first = find(preselect);
        if (first == null && !categories().isEmpty()) {
            first = firstIn(categories().getFirst());
        }
        if (first != null) {
            selected = first.id();
            expanded.add(first.category());
        }
    }

    /** Categories that have at least one feature, in catalog order (empty ones are hidden, Q-SU5). */
    public List<FeatureCategory> categories() {
        var result = new ArrayList<FeatureCategory>();
        for (FeatureCategory category : FeatureCategory.values()) {
            if (features.stream().anyMatch(f -> f.category() == category)) {
                result.add(category);
            }
        }
        return result;
    }

    public String selected() {
        return selected;
    }

    public FeatureEntry selectedFeature() {
        return find(selected);
    }

    public String query() {
        return query;
    }

    public boolean searching() {
        return !normalize(query).isEmpty();
    }

    public boolean isExpanded(FeatureCategory category) {
        return expanded.contains(category);
    }

    /**
     * A click on a category: a collapsed one expands and selects its first feature unless the selection is already in
     * it; an expanded one collapses and keeps the selection. Ignored while searching (all results are shown).
     */
    public void clickCategory(FeatureCategory category) {
        if (searching()) {
            return;
        }
        if (expanded.remove(category)) {
            return;
        }
        expanded.add(category);
        FeatureEntry current = find(selected);
        if (current == null || current.category() != category) {
            FeatureEntry first = firstIn(category);
            if (first != null) {
                selected = first.id();
            }
        }
    }

    /** Collapses or expands without changing the selection (keyboard Left/Right). */
    public void setExpanded(FeatureCategory category, boolean open) {
        if (open) {
            expanded.add(category);
        } else {
            expanded.remove(category);
        }
    }

    /** Selects a feature; its category is expanded so the selection stays visible after the search is cleared. */
    public void select(String featureId) {
        FeatureEntry entry = find(featureId);
        if (entry == null) {
            return;
        }
        selected = entry.id();
        if (beforeSearch != null) {
            beforeSearch.add(entry.category());
        } else {
            expanded.add(entry.category());
        }
    }

    /**
     * Changes the search text. Starting a search remembers the expansion state and clearing it restores that state.
     * While searching, a selection outside the results moves to the first matching feature, so the panel never
     * shows a feature the results do not list.
     */
    public void setQuery(String text) {
        String next = text == null ? "" : text;
        boolean wasSearching = searching();
        query = next;
        if (searching() && !wasSearching) {
            beforeSearch = EnumSet.noneOf(FeatureCategory.class);
            beforeSearch.addAll(expanded);
        } else if (!searching() && wasSearching) {
            expanded.clear();
            expanded.addAll(beforeSearch);
            beforeSearch = null;
        }
        if (searching()) {
            List<FeatureEntry> matches = features.stream().filter(this::featureMatches).toList();
            if (!matches.isEmpty() && matches.stream().noneMatch(f -> f.id().equals(selected))) {
                select(matches.getFirst().id());
            }
        }
    }

    /** The visible rows; {@code flagged} marks features with invalid draft input (settings-ux-v1.md T-5). */
    public List<Row> rows(Predicate<String> flagged) {
        Objects.requireNonNull(flagged, "flagged");
        var rows = new ArrayList<Row>();
        boolean searching = searching();
        for (FeatureCategory category : categories()) {
            List<FeatureEntry> inCategory = features.stream().filter(f -> f.category() == category).toList();
            if (!searching) {
                boolean open = expanded.contains(category);
                rows.add(new Row(Kind.CATEGORY, category, null, null, inCategory.size(), open, false, false));
                if (open) {
                    for (FeatureEntry f : inCategory) {
                        rows.add(featureRow(f, flagged));
                    }
                }
                continue;
            }
            List<FeatureEntry> matching = inCategory.stream().filter(this::featureMatches).toList();
            if (matching.isEmpty()) {
                continue;
            }
            rows.add(new Row(Kind.CATEGORY, category, null, null, matching.size(), true, false, false));
            for (FeatureEntry f : matching) {
                rows.add(featureRow(f, flagged));
                for (SettingEntry setting : f.settings()) {
                    if (settingMatches(setting)) {
                        rows.add(new Row(Kind.SETTING, category, f.id(), setting.key(), 0, false, false, false));
                    }
                }
            }
        }
        return rows;
    }

    /** Whether the search finds anything (false only while searching without results). */
    public boolean hasResults() {
        return !searching() || features.stream().anyMatch(this::featureMatches);
    }

    private Row featureRow(FeatureEntry f, Predicate<String> flagged) {
        return new Row(Kind.FEATURE, f.category(), f.id(), null, 0, false, f.id().equals(selected),
                flagged.test(f.id()));
    }

    private boolean featureMatches(FeatureEntry f) {
        String q = normalize(query);
        return contains(f.name(), q) || contains(f.description(), q) || contains(f.id(), q)
                || f.settings().stream().anyMatch(this::settingMatches);
    }

    private boolean settingMatches(SettingEntry s) {
        String q = normalize(query);
        return contains(s.label(), q) || contains(s.key(), q)
                || s.optionLabels().stream().anyMatch(label -> contains(label, q));
    }

    private static boolean contains(String text, String normalizedQuery) {
        return text != null && normalize(text).contains(normalizedQuery);
    }

    /** Case- and accent-insensitive form used for matching ("Saturação" matches "saturacao"). */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(text.strip(), Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    private FeatureEntry find(String id) {
        if (id == null) {
            return null;
        }
        return features.stream().filter(f -> f.id().equals(id)).findFirst().orElse(null);
    }

    private FeatureEntry firstIn(FeatureCategory category) {
        return features.stream().filter(f -> f.category() == category).findFirst().orElse(null);
    }
}
