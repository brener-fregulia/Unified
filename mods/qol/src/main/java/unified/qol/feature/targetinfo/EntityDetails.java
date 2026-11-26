package unified.qol.feature.targetinfo;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** Entity health and owner rules (target-info-v1.md §0, §7). Pure. */
public final class EntityDetails {
    /** Up to this maximum health (10 hearts) health is drawn as hearts; above it, as numbers. */
    public static final float MAX_HEART_HEALTH = 20.0f;

    private EntityDetails() {
    }

    /**
     * @param hearts whether to draw hearts (else numeric text)
     * @param halves health in half hearts, rounded up like the vanilla HUD (hearts mode)
     * @param maxHalves maximum health in half hearts, rounded up (hearts mode)
     */
    public record Health(boolean hearts, int halves, int maxHalves, String current, String max) {
    }

    public static Health health(float health, float maxHealth) {
        float current = Math.max(0.0f, health);
        float max = Math.max(current, maxHealth);
        boolean hearts = max <= MAX_HEART_HEALTH;
        return new Health(hearts, (int) Math.ceil(current), (int) Math.ceil(max), number(current), number(max));
    }

    /** One decimal at most, without a trailing ".0". */
    static String number(float value) {
        float rounded = Math.round(value * 10.0f) / 10.0f;
        return rounded == Math.rint(rounded) ? Integer.toString((int) rounded)
                : String.format(Locale.ROOT, "%.1f", rounded);
    }

    public sealed interface Owner permits Owner.None, Owner.Named, Owner.Unknown {
        record None() implements Owner {
        }

        record Named(String name) implements Owner {
        }

        record Unknown() implements Owner {
        }
    }

    /**
     * Resolves an owner locally only: first an entity already loaded on the client, then the player list. A UUID is
     * never returned as a name, and nothing is looked up over the network.
     *
     * @param owner the synced owner reference; empty for untamed animals
     */
    public static Owner owner(Optional<UUID> owner, Function<UUID, String> loadedEntityName,
            Function<UUID, String> playerListName) {
        if (owner.isEmpty()) {
            return new Owner.None();
        }
        UUID id = owner.get();
        String name = loadedEntityName.apply(id);
        if (name == null || name.isBlank()) {
            name = playerListName.apply(id);
        }
        return name == null || name.isBlank() ? new Owner.Unknown() : new Owner.Named(name);
    }
}
