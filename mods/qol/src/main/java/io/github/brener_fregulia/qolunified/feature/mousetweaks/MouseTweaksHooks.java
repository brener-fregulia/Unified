package io.github.brener_fregulia.qolunified.feature.mousetweaks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;

/**
 * Registers the gesture handler on container screens (mouse-tweaks-v1.md §9). Fabric recreates a screen's events on
 * every init, so the handler is attached again after a resize and never twice; the gesture in progress is cancelled
 * then, so re-initializing a screen cannot replay clicks. The creative inventory is excluded (§5).
 */
public final class MouseTweaksHooks {
    private static long tick;
    private static boolean registered;

    private MouseTweaksHooks() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> tick++);
        ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?> container)
                    || screen instanceof CreativeModeInventoryScreen) {
                return;
            }
            // One budget per screen instance survives re-inits, so a resize cannot reset it mid-tick.
            InventoryGestures gestures = new InventoryGestures(container, BUDGETS.computeIfAbsent(container,
                    s -> new ClickBudget()));
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> gestures.press(event));
            ScreenMouseEvents.allowMouseDrag(screen).register((s, event, dx, dy) -> gestures.drag(event));
            ScreenMouseEvents.allowMouseRelease(screen).register((s, event) -> gestures.release(event));
            ScreenMouseEvents.allowMouseScroll(screen).register((s, x, y, horizontal, vertical) ->
                    gestures.scroll(x, y, vertical));
            ScreenEvents.afterTick(screen).register(s -> {
                if (!minecraft.isWindowActive()) {
                    gestures.cancel(); // focus loss ends any drag; a release may never arrive
                }
            });
            ScreenEvents.remove(screen).register(s -> {
                gestures.cancel();
                BUDGETS.remove(container);
            });
        });
    }

    private static final java.util.Map<AbstractContainerScreen<?>, ClickBudget> BUDGETS =
            new java.util.WeakHashMap<>();

    /** The client tick count, for the per-tick click budget. */
    static long tick() {
        return tick;
    }
}
