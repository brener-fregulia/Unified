package io.github.brener_fregulia.qolunified.ui.screen;

import io.github.brener_fregulia.qolunified.core.ApplyMode;
import io.github.brener_fregulia.qolunified.core.FeatureDescriptor;
import io.github.brener_fregulia.qolunified.core.SettingDescriptor;
import io.github.brener_fregulia.qolunified.ui.SettingsDraft;
import io.github.brener_fregulia.qolunified.ui.UiKeys;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;

/** Edits one feature's settings in the shared draft. Back returns to the main screen without saving. */
final class FeatureSettingsScreen extends Screen {
    private static final int GAP = 8;

    private final SettingsScreen parent;
    private final SettingsDraft draft;
    private final FeatureDescriptor descriptor;
    private StringWidget errorLine;

    FeatureSettingsScreen(SettingsScreen parent, SettingsDraft draft, FeatureDescriptor descriptor) {
        super(Component.translatable(descriptor.nameKey()));
        this.parent = parent;
        this.draft = draft;
        this.descriptor = descriptor;
    }

    @Override
    protected void init() {
        int titleWidth = Math.min(font.width(title), width - 2 * GAP);
        addRenderableWidget(new StringWidget((width - titleWidth) / 2, 10, titleWidth, font.lineHeight, title, font));
        int top = 28;
        var list = new RowList(minecraft, width, Math.max(40, height - top - 52), top);
        errorLine = new StringWidget(GAP, height - 44, width - 2 * GAP, font.lineHeight, Component.empty(), font);
        errorLine.setMaxWidth(width - 2 * GAP);
        var rows = new ArrayList<RowList.Row>();
        for (SettingDescriptor setting : descriptor.settings()) {
            rows.add(settingRow(setting));
        }
        if (rows.isEmpty()) {
            rows.add(RowList.row(font, Component.translatable(UiKeys.NO_SETTINGS), null, List.of()));
        }
        list.setRows(rows);
        addRenderableWidget(list);
        addRenderableWidget(errorLine);
        addRenderableWidget(Button.builder(Component.translatable(UiKeys.BACK), b -> onClose())
                .bounds(width / 2 - 75, height - 28, 150, 20).build());
    }

    private RowList.Row settingRow(SettingDescriptor setting) {
        String id = descriptor.id();
        String key = setting.key();
        Object value = draft.choice(id).settings().get(key);
        Component label = Component.translatable(setting.nameKey(id));
        Component detail = setting.applyMode() == ApplyMode.RESTART_REQUIRED
                ? Component.translatable(UiKeys.RESTART_REQUIRED_SUFFIX) : null;
        AbstractWidget control = switch (setting) {
            case SettingDescriptor.BooleanSetting ignored -> CycleButton.onOffBuilder((Boolean) value)
                    .displayOnlyValue()
                    .create(0, 0, 90, 20, label, (b, v) -> draft.setValue(id, key, v));
            case SettingDescriptor.EnumSetting e -> CycleButton.<String>builder(
                            v -> Component.translatable(UiKeys.enumValue(id, key, v)), (String) value)
                    .withValues(e.values())
                    .displayOnlyValue()
                    .create(0, 0, 120, 20, label, (b, v) -> draft.setValue(id, key, v));
            case SettingDescriptor.IntegerSetting integer -> integerField(id, key, integer, value, label);
        };
        control.active = draft.editable();
        return RowList.row(font, label, detail, List.of(control));
    }

    private EditBox integerField(String id, String key, SettingDescriptor.IntegerSetting setting, Object value,
            Component label) {
        EditBox box = new EditBox(font, 0, 0, 90, 20, label);
        box.setMaxLength(11);
        box.setValue(draft.invalidInput(id, key).orElse(String.valueOf(value)));
        Runnable showError = () -> {
            Optional<String> error = draft.hasInvalidInput(id, key)
                    ? Optional.of(validationKey(id, key, setting, box.getValue())) : Optional.empty();
            box.setTextColor(error.isPresent() ? 0xFFFF7070 : 0xFFE0E0E0);
            Optional<Component> text = error.map(k -> Component.literal(
                    I18n.get(setting.nameKey(id)) + ": " + I18n.get(k, setting.min(), setting.max())));
            box.setTooltip(text.map(Tooltip::create).orElse(null));
            if (errorLine != null) {
                errorLine.setMessage(text.map(t -> t.copy().withStyle(net.minecraft.ChatFormatting.RED))
                        .orElse(Component.empty()));
            }
        };
        box.setResponder(text -> {
            if (draft.editable()) {
                draft.setIntegerText(id, key, text);
            }
            showError.run();
        });
        showError.run();
        return box;
    }

    private static String validationKey(String id, String key, SettingDescriptor.IntegerSetting setting,
            String text) {
        try {
            int v = Integer.parseInt(text.strip());
            return v < setting.min() || v > setting.max() ? UiKeys.ERROR_OUT_OF_RANGE : UiKeys.ERROR_INVALID_VALUE;
        } catch (NumberFormatException e) {
            return UiKeys.ERROR_NOT_A_NUMBER;
        }
    }

    /** Back and Escape return to the main screen; the draft is kept for Done or Cancel there. */
    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
