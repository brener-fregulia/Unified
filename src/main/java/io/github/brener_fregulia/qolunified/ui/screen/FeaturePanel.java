package io.github.brener_fregulia.qolunified.ui.screen;

import io.github.brener_fregulia.qolunified.core.FeatureDescriptor;
import io.github.brener_fregulia.qolunified.core.FeatureState;
import io.github.brener_fregulia.qolunified.core.SettingDescriptor;
import io.github.brener_fregulia.qolunified.ui.FeatureActions;
import io.github.brener_fregulia.qolunified.ui.FeatureComparisons;
import io.github.brener_fregulia.qolunified.ui.SettingsDraft;
import io.github.brener_fregulia.qolunified.ui.UiKeys;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Builds the scrolling part of one feature's panel (settings-ux-v1.md §5): description, "Applies when ON" hint,
 * comparison, specialized tools and the descriptor-driven settings. It holds no feature logic.
 */
final class FeaturePanel {
    private FeaturePanel() {
    }

    static List<PanelList.Row> rows(Font font, Screen parent, SettingsDraft draft, FeatureDescriptor descriptor,
            int width, SettingRows.Host host) {
        String id = descriptor.id();
        boolean off = !draft.choice(id).enabled();
        var rows = new ArrayList<PanelList.Row>();
        rows.add(PanelList.text(font, Component.translatable(descriptor.descriptionKey()), PanelList.MUTED, width));
        if (off && !descriptor.settings().isEmpty()) {
            rows.add(PanelList.text(font, Component.translatable(UiKeys.APPLIES_WHEN_ON), PanelList.WARNING, width));
        }
        FeatureComparisons.find(id).ifPresent(c -> {
            rows.add(PanelList.divider());
            rows.add(PanelList.comparison(font, c, width));
        });
        FeatureActions.find(id).ifPresent(action -> {
            rows.add(PanelList.divider());
            Component label = Component.translatable(action.labelKey());
            boolean active = draft.core().lifecycle().state(id) == FeatureState.ACTIVE;
            Button button = Button.builder(label, b -> net.minecraft.client.Minecraft.getInstance()
                            .gui.setScreen(action.open().apply(parent, draft)))
                    .size(SettingRows.fit(font, List.of(label.getString()), 100, Math.min(200, width)), 20)
                    .build();
            button.active = active;
            if (!active) {
                // Q-SU6: the tool works on the running feature; activation must be confirmed with Done first.
                button.setTooltip(Tooltip.create(Component.translatable(UiKeys.ACTION_NEEDS_ACTIVE)));
            }
            rows.add(new PanelList.SettingRow(font, active ? Component.empty()
                    : Component.translatable(UiKeys.ACTION_NEEDS_ACTIVE).withStyle(net.minecraft.ChatFormatting.GOLD),
                    null, PanelList.WARNING, List.of(button), false, null, width));
        });
        if (!descriptor.settings().isEmpty()) {
            rows.add(PanelList.divider());
        }
        for (SettingDescriptor setting : descriptor.settings()) {
            rows.add(SettingRows.row(font, draft, descriptor, setting, off, width, host));
        }
        if (descriptor.settings().isEmpty() && FeatureActions.find(id).isEmpty()) {
            rows.add(PanelList.text(font, Component.translatable(UiKeys.NO_SETTINGS), PanelList.MUTED, width));
        }
        return rows;
    }
}
