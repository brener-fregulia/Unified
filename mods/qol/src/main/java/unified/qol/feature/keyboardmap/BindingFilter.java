package unified.qol.feature.keyboardmap;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Search and filters, combined with AND (keymap-v1.md I-4 to I-7). The query matches the action name, the category
 * and the input label, ignoring case and accents; {@code key:<text>} matches only the input label.
 */
public record BindingFilter(String query, String category, Visibility visibility) {
    public static final String KEY_PREFIX = "key:";

    /** The {@code default_filter} values. */
    public enum Visibility {
        ALL("all"),
        OVERLAPS("overlaps"),
        ASSIGNED("assigned"),
        UNBOUND("unbound");

        private final String id;

        Visibility(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Visibility byId(String id) {
            for (Visibility v : values()) {
                if (v.id.equals(id)) {
                    return v;
                }
            }
            return ALL;
        }
    }

    public BindingFilter {
        query = query == null ? "" : query;
        visibility = visibility == null ? Visibility.ALL : visibility;
    }

    public static BindingFilter all() {
        return new BindingFilter("", null, Visibility.ALL);
    }

    public boolean isEmpty() {
        return query.isBlank() && category == null && visibility == Visibility.ALL;
    }

    public boolean matches(Binding binding, BindingCatalog catalog) {
        if (category != null && !category.equals(binding.category())) {
            return false;
        }
        boolean visible = switch (visibility) {
            case ALL -> true;
            case OVERLAPS -> catalog.state(binding) == InputState.OVERLAP;
            case ASSIGNED -> !binding.unbound();
            case UNBOUND -> binding.unbound();
        };
        if (!visible) {
            return false;
        }
        String q = fold(query.strip());
        if (q.isEmpty()) {
            return true;
        }
        if (q.startsWith(KEY_PREFIX)) {
            String key = q.substring(KEY_PREFIX.length()).strip();
            return !binding.unbound() && (key.isEmpty() || fold(binding.inputLabel()).contains(key));
        }
        return fold(binding.name()).contains(q) || fold(binding.category()).contains(q)
                || (!binding.unbound() && fold(binding.inputLabel()).contains(q));
    }

    /** Lower case without accents, so {@code ç} finds {@code Ç} and {@code acao} finds {@code Ação}. */
    static String fold(String text) {
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }
}
