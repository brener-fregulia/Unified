package unified.qol.feature.targetinfo;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;

/**
 * Registers the overlay once, on the first enable, right after the vanilla boss bars. Fabric offers no way to remove
 * a HUD element, so it checks {@link TargetInfoFeature#activeSettings()} and returns at once while inactive.
 */
final class TargetInfoHooks {
    static final Identifier ELEMENT = Identifier.fromNamespaceAndPath("qol_unified", TargetInfoSettings.ID);

    private TargetInfoHooks() {
    }

    static void install() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, ELEMENT, TargetInfoRenderer::extract);
        ShulkerSnapshots.register();
    }
}
