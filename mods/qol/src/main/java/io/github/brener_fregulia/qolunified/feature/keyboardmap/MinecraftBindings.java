package io.github.brener_fregulia.qolunified.feature.keyboardmap;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;

/**
 * Reads vanilla and modded key mappings from {@link Options#keyMappings} and writes them back only through vanilla's
 * own API: {@link KeyMapping#setKey}, {@link KeyMapping#resetMapping()} and {@link Options#save()} (keymap-v1.md F-9,
 * F-10). No other storage.
 */
final class MinecraftBindings implements RemapSession.BindingStore {
    private final Options options;

    MinecraftBindings(Options options) {
        this.options = options;
    }

    /** One pass over all mappings, in vanilla's Key Binds order. */
    BindingCatalog snapshot() {
        KeyMapping[] mappings = options.keyMappings.clone();
        Arrays.sort(mappings);
        var bindings = new ArrayList<Binding>(mappings.length);
        for (KeyMapping mapping : mappings) {
            InputId input = mapping.isUnbound() ? null : toInput(InputConstants.getKey(mapping.saveString()));
            bindings.add(new Binding(mapping.getName(), Component.translatable(mapping.getName()).getString(),
                    mapping.getCategory().label().getString(),
                    input == null ? "" : mapping.getTranslatedKeyMessage().getString(), input,
                    toInput(mapping.getDefaultKey())));
        }
        return new BindingCatalog(bindings);
    }

    @Override
    public void assign(String id, InputId input) {
        KeyMapping mapping = find(id);
        if (mapping != null) {
            mapping.setKey(input == null ? InputConstants.UNKNOWN : toKey(input));
            apply();
        }
    }

    @Override
    public void reset(String id) {
        KeyMapping mapping = find(id);
        if (mapping != null) {
            mapping.setKey(mapping.getDefaultKey());
            apply();
        }
    }

    private void apply() {
        KeyMapping.resetMapping();
        options.save();
    }

    private KeyMapping find(String id) {
        for (KeyMapping mapping : options.keyMappings) {
            if (mapping.getName().equals(id)) {
                return mapping;
            }
        }
        return null;
    }

    static InputId toInput(InputConstants.Key key) {
        if (key == null || key.equals(InputConstants.UNKNOWN)) {
            return null;
        }
        return key.getType() == InputConstants.Type.MOUSE ? InputId.mouse(key.getValue()) : InputId.key(key.getValue());
    }

    static InputConstants.Key toKey(InputId input) {
        return (input.kind() == InputId.Kind.MOUSE ? InputConstants.Type.MOUSE : InputConstants.Type.KEYBOARD)
                .getOrCreate(input.code());
    }

    /** The OS-provided name of a physical key or mouse button (F-3). */
    static String label(InputId input) {
        return toKey(input).getDisplayName().getString();
    }

    static List<InputId> standardMouseButtons() {
        return List.of(InputId.mouse(1), InputId.mouse(3), InputId.mouse(2), InputId.mouse(4), InputId.mouse(5));
    }
}
