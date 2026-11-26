package io.github.brener_fregulia.qolunified.mixin;

import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Target Info Mixin TI-2 (target-info-v1.md §0, §8): read-only access to the boss bars vanilla draws, so the
 * overlay can sit below them. Only the map's size is read.
 */
@Mixin(BossHealthOverlay.class)
public interface BossHealthOverlayAccessor {
    @Accessor("events")
    Map<UUID, LerpingBossEvent> qolUnified$events();
}
