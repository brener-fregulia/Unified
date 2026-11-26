package io.github.brener_fregulia.qolunified.feature.soundcontrol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Session-only history of recently heard sound events (sound-control-v1.md §5.3): one entry per event id, at most
 * {@code capacity} ids, least recently heard evicted first. Stores only the id, the last category, the last-heard time
 * and a count; never positions, entities, players or servers.
 *
 * <p>Thread-safe: recording takes one short lock and allocates only for an id not seen yet.
 *
 * @param <K> event id type
 * @param <C> category type
 */
public final class RecentSounds<K, C> {
    /** One event in a snapshot. {@code count} saturates at {@link Integer#MAX_VALUE}. */
    public record Entry<K, C>(K id, C category, long lastHeardMillis, int count) {
    }

    private static final class Node<C> {
        C category;
        long lastHeard;
        int count;
    }

    private final Map<K, Node<C>> entries;

    public RecentSounds(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        // Access order: get() and put() move an id to the end, so the first entry is the least recently heard.
        this.entries = new LinkedHashMap<>(capacity * 2, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, Node<C>> eldest) {
                return size() > capacity;
            }
        };
    }

    public synchronized void record(K id, C category, long nowMillis) {
        if (id == null) {
            return;
        }
        Node<C> node = entries.get(id);
        if (node == null) {
            node = new Node<>();
            entries.put(id, node);
        }
        node.category = category;
        node.lastHeard = nowMillis;
        if (node.count < Integer.MAX_VALUE) {
            node.count++;
        }
    }

    /** Most recently heard first. */
    public synchronized List<Entry<K, C>> snapshot() {
        var list = new ArrayList<Entry<K, C>>(entries.size());
        entries.forEach((id, node) -> list.add(new Entry<>(id, node.category, node.lastHeard, node.count)));
        return list.reversed();
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized void clear() {
        entries.clear();
    }
}
