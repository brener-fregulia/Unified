package io.github.brener_fregulia.qolunified.feature.foodhud;

import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.resources.Identifier;

/**
 * Installs the Food HUD's registrations once, on the first enable. Fabric and vanilla offer no way to remove them,
 * so each one checks {@link FoodHudFeature#activeSettings()} and does nothing while the feature is inactive.
 */
final class FoodHudHooks {
    static final Identifier DEBUG_ENTRY = Identifier.fromNamespaceAndPath("qol_unified", FoodHudSettings.ID);

    private FoodHudHooks() {
    }

    static void install() {
        ClientTooltipComponentCallback.EVENT.register(data ->
                data instanceof FoodTooltip food ? new FoodTooltipComponent(food) : null);
        if (DebugScreenEntries.getEntry(DEBUG_ENTRY) == null) {
            DebugScreenEntries.register(DEBUG_ENTRY, new FoodDebugEntry());
        }
    }
}
