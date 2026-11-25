package io.github.brener_fregulia.qolunified.ui.screen;

import io.github.brener_fregulia.qolunified.core.ApplyMode;
import io.github.brener_fregulia.qolunified.core.FeatureDescriptor;
import io.github.brener_fregulia.qolunified.core.SettingDescriptor;
import io.github.brener_fregulia.qolunified.ui.SettingsDraft;
import io.github.brener_fregulia.qolunified.ui.UiKeys;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/**
 * Descriptor-driven setting rows (settings-ux-v1.md §7.1), moved from the retired feature settings screen with the
 * same controls and validation: boolean on/off, enum cycle, integer edit box with range errors, map summary.
 */
final class SettingRows {
    private SettingRows() {
    }

    /**
     * @param dimmed the feature is OFF in the draft: still editable, drawn subdued (Q-SU3)
     * @param errorLine receives the current integer validation message (or empty) of this row
     */
    static PanelList.SettingRow row(Font font, SettingsDraft draft, FeatureDescriptor descriptor,
            SettingDescriptor setting, boolean dimmed, int width, Consumer<Component> errorLine) {
        String id = descriptor.id();
        String key = setting.key();
        Object value = draft.choice(id).settings().get(key);
        Component label = Component.translatable(setting.nameKey(id));
        Component restart = setting.applyMode() == ApplyMode.RESTART_REQUIRED
                ? Component.translatable(UiKeys.RESTART_REQUIRED_SUFFIX) : null;
        if (setting instanceof SettingDescriptor.MapSetting) {
            // Map settings are edited by the feature's own screen; the panel summarizes them.
            Component count = Component.translatable(UiKeys.MAP_ENTRIES, ((Map<?, ?>) value).size());
            return new PanelList.SettingRow(font, label, count, PanelList.MUTED, List.of(), dimmed, key, width);
        }
        AbstractWidget control = switch (setting) {
            case SettingDescriptor.BooleanSetting ignored -> CycleButton.onOffBuilder((Boolean) value)
                    .displayOnlyValue()
                    .create(0, 0, 90, 20, label, (b, v) -> draft.setValue(id, key, v));
            case SettingDescriptor.EnumSetting e -> CycleButton.<String>builder(
                            v -> Component.translatable(UiKeys.enumValue(id, key, v)), (String) value)
                    .withValues(e.values())
                    .displayOnlyValue()
                    .create(0, 0, 120, 20, label, (b, v) -> draft.setValue(id, key, v));
            case SettingDescriptor.IntegerSetting integer ->
                    integerField(font, draft, id, key, integer, value, label, errorLine);
            case SettingDescriptor.MapSetting ignored -> throw new IllegalStateException("handled above");
        };
        control.active = draft.editable();
        return new PanelList.SettingRow(font, label, restart, PanelList.WARNING, List.of(control), dimmed, key,
                width);
    }

    private static EditBox integerField(Font font, SettingsDraft draft, String id, String key,
            SettingDescriptor.IntegerSetting setting, Object value, Component label, Consumer<Component> errorLine) {
        EditBox box = new EditBox(font, 0, 0, 90, 20, label);
        box.setMaxLength(11);
        box.setValue(draft.invalidInput(id, key).orElse(String.valueOf(value)));
        Runnable showError = () -> {
            Optional<String> error = draft.hasInvalidInput(id, key)
                    ? Optional.of(validationKey(setting, box.getValue())) : Optional.empty();
            box.setTextColor(error.isPresent() ? PanelList.ERROR : 0xFFE0E0E0);
            Optional<Component> text = error.map(k -> Component.literal(
                    I18n.get(setting.nameKey(id)) + ": " + I18n.get(k, setting.min(), setting.max())));
            box.setTooltip(text.map(Tooltip::create).orElse(null));
            errorLine.accept(text.map(t -> (Component) t.copy().withStyle(ChatFormatting.RED))
                    .orElse(Component.empty()));
        };
        box.setResponder(text -> {
            if (draft.editable()) {
                draft.setIntegerText(id, key, text);
            }
            showError.run();
        });
        box.active = draft.editable();
        box.setEditable(draft.editable());
        showError.run();
        return box;
    }

    private static String validationKey(SettingDescriptor.IntegerSetting setting, String text) {
        try {
            int v = Integer.parseInt(text.strip());
            return v < setting.min() || v > setting.max() ? UiKeys.ERROR_OUT_OF_RANGE : UiKeys.ERROR_INVALID_VALUE;
        } catch (NumberFormatException e) {
            return UiKeys.ERROR_NOT_A_NUMBER;
        }
    }
}
