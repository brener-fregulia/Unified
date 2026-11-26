package io.github.brener_fregulia.qolunified.feature.soundcontrol;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Search and filter rules for the sound list (sound-control-v1.md §5.4). Free of Minecraft types. */
public final class SoundSearch {
    /** Queries shorter than this list nothing in the All sounds tab unless a filter is set (§6.2). */
    public static final int MIN_QUERY = 2;
    /** The All sounds tab shows at most this many rows; the rest are counted. */
    public static final int MAX_ROWS = 200;

    /**
     * One sound the list can show.
     *
     * @param id full event id
     * @param name friendly name in the current language
     * @param namespace id namespace
     * @param modName mod display name, or the namespace
     * @param category raw category name of the last play, or null when never heard
     * @param categoryLabel localized category name, or null when never heard
     */
    public record Candidate(String id, String name, String namespace, String modName, String category,
            String categoryLabel) {
        String haystack() {
            return String.join("\n", name, id, namespace, modName,
                    category == null ? "" : category, categoryLabel == null ? "" : categoryLabel)
                    .toLowerCase(Locale.ROOT);
        }
    }

    public static final Comparator<Candidate> BY_NAME = Comparator.comparing(Candidate::name,
            String.CASE_INSENSITIVE_ORDER).thenComparing(Candidate::id);

    private SoundSearch() {
    }

    /**
     * True when every word of {@code query} occurs in the name, id, namespace, mod name or category, and the
     * candidate passes the filters. A null filter accepts everything; a category filter rejects never-heard sounds.
     */
    public static boolean matches(Candidate candidate, String query, String categoryFilter, String namespaceFilter) {
        if (categoryFilter != null && !categoryFilter.equals(candidate.category())) {
            return false;
        }
        if (namespaceFilter != null && !namespaceFilter.equals(candidate.namespace())) {
            return false;
        }
        List<String> words = words(query);
        if (words.isEmpty()) {
            return true;
        }
        String haystack = candidate.haystack();
        for (String word : words) {
            if (!haystack.contains(word)) {
                return false;
            }
        }
        return true;
    }

    /** Whether the All sounds tab should list anything for this query and these filters. */
    public static boolean listsAll(String query, String categoryFilter, String namespaceFilter) {
        return categoryFilter != null || namespaceFilter != null || query.strip().length() >= MIN_QUERY;
    }

    static List<String> words(String query) {
        String trimmed = query.strip().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? List.of() : List.of(trimmed.split("\\s+"));
    }
}
