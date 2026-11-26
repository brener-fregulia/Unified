package unified.qol.feature.shulkerpreview;

import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;

/**
 * Registers the tooltip component once, on the first enable. Fabric offers no way to remove it; it only turns
 * {@link ShulkerTooltip} data into a component, and that data exists only while the feature is active.
 */
final class ShulkerPreviewHooks {
    private ShulkerPreviewHooks() {
    }

    static void install() {
        ClientTooltipComponentCallback.EVENT.register(data ->
                data instanceof ShulkerTooltip shulker ? new ShulkerTooltipComponent(shulker) : null);
    }
}
