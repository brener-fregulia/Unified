package io.github.brener_fregulia.qolunified.ui.screen;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.brener_fregulia.qolunified.core.ApplyMode;
import io.github.brener_fregulia.qolunified.core.FeatureDescriptor;
import io.github.brener_fregulia.qolunified.core.SettingDescriptor;
import io.github.brener_fregulia.qolunified.ui.SettingsDraft;
import io.github.brener_fregulia.qolunified.ui.UiKeys;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/**
 * Descriptor-driven setting rows (settings-ux-v1.md §7.1, §18): boolean on/off, enum cycle, integer edit box with range
 * errors, map summary. Booleans and enums report each chosen value at once; an integer field reports only a complete
 * valid number, on Enter or when it loses focus, and keeps invalid text out of the draft.
 */
final class SettingRows {
    /** What the workspace needs from a row: commit after an edit, and remember text that is not a valid value yet. */
    interface Host {
        void changed();

        Optional<String> pendingText(String featureId, String key);

        void pendingText(String featureId, String key, String text, Component error);
    }

    private SettingRows() {
    }

    /** @param dimmed the feature is OFF: still editable, drawn subdued (Q-SU3) */
    static PanelList.SettingRow row(Font font, SettingsDraft draft, FeatureDescriptor descriptor,
            SettingDescriptor setting, boolean dimmed, int width, Host host) {
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
                    .create(0, 0, 90, 20, label, (b, v) -> {
                        draft.setValue(id, key, v);
                        host.changed();
                    });
            case SettingDescriptor.EnumSetting e -> CycleButton.<String>builder(
                            v -> Component.translatable(UiKeys.enumValue(id, key, v)), (String) value)
                    .withValues(e.values())
                    .displayOnlyValue()
                    .create(0, 0, 120, 20, label, (b, v) -> {
                        draft.setValue(id, key, v);
                        host.changed();
                    });
            case SettingDescriptor.IntegerSetting integer -> integerField(font, draft, id, key, integer, value, label,
                    host);
            case SettingDescriptor.MapSetting ignored -> throw new IllegalStateException("handled above");
        };
        control.active = draft.editable();
        return new PanelList.SettingRow(font, label, restart, PanelList.WARNING, List.of(control), dimmed, key,
                width);
    }

    private static EditBox integerField(Font font, SettingsDraft draft, String id, String key,
            SettingDescriptor.IntegerSetting setting, Object value, Component label, Host host) {
        EditBox box = new EditBox(font, 0, 0, 90, 20, label) {
            @Override
            public boolean keyPressed(KeyEvent event) {
                if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
                    commit(this, draft, id, key, setting, host);
                    return true;
                }
                return super.keyPressed(event);
            }

            @Override
            public void setFocused(boolean focused) {
                boolean had = isFocused();
                super.setFocused(focused);
                if (had && !focused) {
                    commit(this, draft, id, key, setting, host);
                }
            }
        };
        box.setMaxLength(11);
        box.setValue(host.pendingText(id, key).orElse(String.valueOf(value)));
        Runnable show = () -> {
            Optional<Component> error = validation(setting, id, box.getValue());
            box.setTextColor(error.isPresent() ? PanelList.ERROR : 0xFFE0E0E0);
            box.setTooltip(error.map(Tooltip::create).orElse(null));
        };
        box.setResponder(text -> show.run()); // typing only validates; nothing reaches the draft yet
        box.active = draft.editable();
        box.setEditable(draft.editable());
        show.run();
        return box;
    }

    /** Enter or focus loss: a valid, changed number goes to the draft and is committed; invalid text stays local. */
    private static void commit(EditBox box, SettingsDraft draft, String id, String key,
            SettingDescriptor.IntegerSetting setting, Host host) {
        if (!draft.editable()) {
            return;
        }
        String text = box.getValue();
        Optional<Component> error = validation(setting, id, text);
        if (error.isPresent()) {
            host.pendingText(id, key, text, error.get().copy().withStyle(ChatFormatting.RED));
            return;
        }
        host.pendingText(id, key, null, null);
        int number = Integer.parseInt(text.strip());
        if (!Integer.valueOf(number).equals(draft.choice(id).settings().get(key))) {
            draft.setIntegerText(id, key, text);
            host.changed();
        }
    }

    private static Optional<Component> validation(SettingDescriptor.IntegerSetting setting, String id, String text) {
        String errorKey;
        try {
            int v = Integer.parseInt(text.strip());
            if (v >= setting.min() && v <= setting.max()) {
                return Optional.empty();
            }
            errorKey = UiKeys.ERROR_OUT_OF_RANGE;
        } catch (NumberFormatException e) {
            errorKey = UiKeys.ERROR_NOT_A_NUMBER;
        }
        return Optional.of(Component.literal(I18n.get(setting.nameKey(id)) + ": "
                + I18n.get(errorKey, setting.min(), setting.max())));
    }
}
