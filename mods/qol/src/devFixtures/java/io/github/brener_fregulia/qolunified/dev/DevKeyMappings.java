package io.github.brener_fregulia.qolunified.dev;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * Development-only modded key mappings, registered through Fabric like any mod's, so the keyboard map can be tested
 * with modded bindings: one on Q (the same key as vanilla's Drop) and one unbound.
 */
public final class DevKeyMappings implements ClientModInitializer {
    static final String ON_Q = "key.qol_unified_dev.sample_on_q";
    static final String UNBOUND = "key.qol_unified_dev.sample_unbound";

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("qol_unified_dev", "samples"));
        KeyMappingHelper.registerKeyMapping(new KeyMapping(ON_Q, InputConstants.Type.KEYBOARD,
                InputConstants.KEY_Q, category));
        KeyMappingHelper.registerKeyMapping(new KeyMapping(UNBOUND, InputConstants.Type.KEYBOARD,
                InputConstants.UNKNOWN.getValue(), category));
    }
}
