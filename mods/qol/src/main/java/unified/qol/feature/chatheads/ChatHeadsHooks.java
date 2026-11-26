package unified.qol.feature.chatheads;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Installs the C-2 refresh once, on the first enable. Fabric offers no way to remove a tick listener; it compares
 * one value per tick and does nothing until the feature or its position changes.
 */
final class ChatHeadsHooks {
    private ChatHeadsHooks() {
    }

    static void install() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.gui != null) {
                ChatHeadMessages.refreshIfChanged(mc.gui.hud.getChat());
            }
        });
    }
}
