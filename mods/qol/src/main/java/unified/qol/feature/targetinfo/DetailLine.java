package unified.qol.feature.targetinfo;

import java.util.List;

/** One contextual line: a translation key and its arguments (numbers, or nested {@link DetailLine}s). Pure. */
public record DetailLine(String key, List<Object> args) {
    public DetailLine {
        args = List.copyOf(args);
    }

    public static DetailLine of(String key, Object... args) {
        return new DetailLine(key, List.of(args));
    }
}
