package io.github.brener_fregulia.qolunified.ui.screen;

import io.github.brener_fregulia.qolunified.core.CoreRuntime;
import io.github.brener_fregulia.qolunified.core.FeatureCategory;
import io.github.brener_fregulia.qolunified.core.FeatureDescriptor;
import io.github.brener_fregulia.qolunified.core.TransitionOutcome;
import io.github.brener_fregulia.qolunified.ui.FeatureSearch;
import io.github.brener_fregulia.qolunified.ui.FeatureStatus;
import io.github.brener_fregulia.qolunified.ui.DraftScreen;
import io.github.brener_fregulia.qolunified.ui.SettingsDraft;
import io.github.brener_fregulia.qolunified.ui.UiKeys;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/** Central QoL: Unified settings screen (UI-1–UI-10, TX-1–TX-9). Holds a draft until Done. */
public final class SettingsScreen extends Screen implements DraftScreen {
    private static final int HEADER = 52;
    private static final int FOOTER = 44;
    private static final int GAP = 8;

    private final Screen parent;
    private final SettingsDraft draft;
    private FeatureCategory selected;
    private String query = "";
    private Component message = Component.empty();
    private boolean messageIsError;

    private RowList list;
    private Button doneButton;
    private StringWidget messageWidget;
    private final java.util.Map<FeatureCategory, Button> categoryButtons = new java.util.EnumMap<>(FeatureCategory.class);

    public SettingsScreen(Screen parent, CoreRuntime core) {
        super(Component.translatable(UiKeys.TITLE));
        this.parent = parent;
        this.draft = new SettingsDraft(core);
        List<FeatureCategory> categories = FeatureSearch.categories(core.registry());
        this.selected = categories.isEmpty() ? null : categories.getFirst();
        if (core.configurationReadOnly()) {
            setMessage(Component.translatable(UiKeys.MESSAGE_READ_ONLY), true);
        }
    }

    @Override
    public SettingsDraft draft() {
        return draft;
    }

    @Override
    protected void init() {
        CoreRuntime core = draft.core();
        int titleWidth = Math.min(font.width(title), width - 2 * GAP);
        addRenderableWidget(new StringWidget((width - titleWidth) / 2, 10, titleWidth, font.lineHeight, title, font));

        int searchWidth = Math.min(300, width - 2 * GAP);
        EditBox search = new EditBox(font, (width - searchWidth) / 2, 26, searchWidth, 20,
                Component.translatable(UiKeys.SEARCH_HINT));
        search.setHint(Component.translatable(UiKeys.SEARCH_HINT));
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            refreshList();
            updateCategoryButtons();
        });
        addRenderableWidget(search);

        int listTop = HEADER + 4;
        int listHeight = Math.max(40, height - listTop - FOOTER);
        List<FeatureCategory> categories = FeatureSearch.categories(core.registry());
        if (categories.isEmpty()) {
            var empty = new MultiLineTextWidget(Component.translatable(UiKeys.EMPTY_REGISTRY), font)
                    .setMaxWidth(Math.min(320, width - 2 * GAP))
                    .setCentered(true);
            empty.setX((width - empty.getWidth()) / 2);
            empty.setY(listTop + listHeight / 2 - empty.getHeight() / 2);
            addRenderableWidget(empty);
            list = null;
        } else {
            int navWidth = Math.max(80, Math.min(130, width / 4));
            // Fit all category buttons into the list height; shrink them before overlapping the footer.
            int step = Math.max(14, Math.min(24, listHeight / categories.size()));
            int buttonHeight = Math.min(20, step - 2);
            int y = listTop;
            categoryButtons.clear();
            for (FeatureCategory category : categories) {
                Component label = Component.translatable(UiKeys.category(category));
                Button button = Button.builder(label, b -> {
                    selected = category;
                    rebuildWidgets();
                }).bounds(GAP, y, navWidth, buttonHeight).tooltip(Tooltip.create(label)).build();
                categoryButtons.put(category, button);
                addRenderableWidget(button);
                y += step;
            }
            updateCategoryButtons();
            int listX = GAP * 2 + navWidth;
            list = new RowList(minecraft, width - listX - GAP, listHeight, listTop);
            list.setX(listX);
            addRenderableWidget(list);
            refreshList();
        }

        messageWidget = new StringWidget(GAP, height - FOOTER + 4, width - 2 * GAP, font.lineHeight, message, font);
        messageWidget.setMaxWidth(width - 2 * GAP);
        addRenderableWidget(messageWidget);
        setMessage(message, messageIsError);

        int buttonWidth = Math.min(150, (width - 3 * GAP) / 2);
        int footerY = height - 28;
        doneButton = Button.builder(Component.translatable(UiKeys.DONE), b -> done())
                .bounds(width / 2 - buttonWidth - GAP / 2, footerY, buttonWidth, 20).build();
        doneButton.active = !core.configurationReadOnly();
        addRenderableWidget(doneButton);
        addRenderableWidget(Button.builder(Component.translatable(UiKeys.CANCEL), b -> onClose())
                .bounds(width / 2 + GAP / 2, footerY, buttonWidth, 20).build());
    }

    /** The selected category is shown as an inactive button, except while a search spans all categories. */
    private void updateCategoryButtons() {
        categoryButtons.forEach((category, button) -> button.active = category != selected || !query.isBlank());
    }

    private void refreshList() {
        if (list == null) {
            return;
        }
        CoreRuntime core = draft.core();
        var rows = new ArrayList<RowList.Row>();
        for (FeatureSearch.Row row : FeatureSearch.rows(core.registry(), selected, query, I18n::get)) {
            if (row.header()) {
                rows.add(RowList.header(font, Component.translatable(UiKeys.category(row.category()))));
            } else {
                rows.add(featureRow(row.feature()));
            }
        }
        if (rows.isEmpty()) {
            rows.add(RowList.row(font, Component.translatable(UiKeys.NO_MATCHES), null, List.of()));
        }
        list.setRows(rows);
    }

    private RowList.Row featureRow(FeatureDescriptor descriptor) {
        CoreRuntime core = draft.core();
        String id = descriptor.id();
        var widgets = new ArrayList<AbstractWidget>();
        if (FeatureStatus.offersRetryDisable(core, id)) {
            widgets.add(Button.builder(Component.translatable(UiKeys.RETRY_DISABLE), b -> retryDisable(id))
                    .size(90, 20)
                    .tooltip(Tooltip.create(Component.translatable(UiKeys.TOOLTIP_UNKNOWN)))
                    .build());
        } else {
            CycleButton<Boolean> toggle = CycleButton.booleanBuilder(Component.translatable(UiKeys.ENABLED),
                            Component.translatable(UiKeys.DISABLED), draft.choice(id).enabled())
                    .displayOnlyValue()
                    .create(0, 0, 70, 20, Component.translatable(descriptor.nameKey()),
                            (button, value) -> draft.setEnabled(id, value));
            toggle.active = draft.canToggle(id);
            if (core.compatibility(id).hasConflicts()) {
                toggle.setTooltip(Tooltip.create(Texts.conflictReasons(core, id)));
            }
            widgets.add(toggle);
        }
        Button settings = Button.builder(Component.translatable(UiKeys.FEATURE_SETTINGS),
                b -> minecraft.gui.setScreen(new FeatureSettingsScreen(this, draft, descriptor))).size(60, 20).build();
        settings.active = !descriptor.settings().isEmpty();
        widgets.add(settings);

        return RowList.feature(font, Component.translatable(descriptor.nameKey()), Texts.status(core, id),
                Texts.needsAttention(core, id), Component.translatable(descriptor.descriptionKey()), widgets);
    }

    private void retryDisable(String id) {
        TransitionOutcome outcome = draft.retryDisable(id);
        setMessage(Component.translatable(outcome.failed() ? UiKeys.MESSAGE_RETRY_FAILED
                : UiKeys.MESSAGE_RETRY_SUCCEEDED), outcome.failed());
        rebuildWidgets();
    }

    private void done() {
        CoreRuntime core = draft.core();
        java.util.Set<String> pendingBefore = pendingRestart(core);
        SettingsDraft.CommitOutcome outcome = draft.commit();
        switch (outcome.kind()) {
            case NO_CHANGE, APPLIED -> {
                // Stay open once to announce restart-required changes made by this commit (UI-6).
                if (!pendingBefore.containsAll(pendingRestart(core))) {
                    setMessage(Component.translatable(UiKeys.MESSAGE_RESTART), false);
                    rebuildWidgets();
                    return;
                }
                onClose();
            }
            case INVALID -> setMessage(Component.translatable(UiKeys.MESSAGE_INVALID), true);
            case READ_ONLY -> setMessage(Component.translatable(UiKeys.MESSAGE_READ_ONLY), true);
            case SAVE_FAILED -> setMessage(Component.translatable(UiKeys.MESSAGE_SAVE_FAILED), true);
            case APPLIED_WITH_FAILURES -> {
                setMessage(Component.translatable(UiKeys.MESSAGE_APPLY_FAILED,
                        Texts.featureNames(core, outcome.failures())), true);
                rebuildWidgets();
            }
        }
    }

    private static java.util.Set<String> pendingRestart(CoreRuntime core) {
        var pending = new java.util.HashSet<String>();
        core.registry().features().forEach(f -> {
            if (core.lifecycle().pendingRestart(f.descriptor().id())) {
                pending.add(f.descriptor().id());
            }
        });
        return pending;
    }

    private void setMessage(Component text, boolean error) {
        message = text;
        messageIsError = error;
        if (messageWidget != null) {
            messageWidget.setMessage(error ? text.copy().withStyle(ChatFormatting.RED) : text);
        }
    }

    /** Cancel and Escape discard the draft (TX-2). */
    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
