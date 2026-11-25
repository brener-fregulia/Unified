package io.github.brener_fregulia.qolunified.ui.screen;

import io.github.brener_fregulia.qolunified.core.CoreRuntime;
import io.github.brener_fregulia.qolunified.core.Feature;
import io.github.brener_fregulia.qolunified.core.FeatureDescriptor;
import io.github.brener_fregulia.qolunified.core.SettingDescriptor;
import io.github.brener_fregulia.qolunified.core.TransitionOutcome;
import io.github.brener_fregulia.qolunified.ui.DraftScreen;
import io.github.brener_fregulia.qolunified.ui.FeatureStatus;
import io.github.brener_fregulia.qolunified.ui.SettingsDraft;
import io.github.brener_fregulia.qolunified.ui.SettingsNavigation;
import io.github.brener_fregulia.qolunified.ui.UiKeys;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/**
 * The unified settings workspace (settings-ux-v1.md, M0 UI-1–UI-10, TX-1–TX-9): an expandable sidebar of categories
 * and features, and the selected feature's panel with its ON/OFF switch, description, comparison, tools and inline
 * settings. One {@link SettingsDraft} for the whole session; navigation and search never touch it; Done commits,
 * Cancel and Escape discard. The screen keeps its navigation state in fields, so it survives opening a tool screen
 * and coming back.
 */
public final class SettingsScreen extends Screen implements DraftScreen {
    private static final int GAP = 6;
    private static final int TOP = 46;
    private static final int FOOTER = 44;
    private static final int HEADER = 34;
    private static final int PANEL_MAX = 520;

    private final Screen parent;
    private final SettingsDraft draft;
    private final String preselect;
    private SettingsNavigation navigation;
    private Component message = Component.empty();
    private boolean messageIsError;
    private boolean drawerOpen;
    private String pendingReveal;
    private final Map<String, Double> panelScroll = new HashMap<>();
    private double navScroll;
    private final Map<String, Component> fieldErrors = new HashMap<>();

    private StringWidget messageWidget;
    private PanelList panel;
    private NavList nav;
    private String panelFeature;

    public SettingsScreen(Screen parent, CoreRuntime core) {
        this(parent, core, null);
    }

    /** Opens the workspace with {@code featureId} selected (a contextual entry point, Q-SU8). */
    public SettingsScreen(Screen parent, CoreRuntime core, String featureId) {
        super(Component.translatable(UiKeys.TITLE));
        this.parent = parent;
        this.draft = new SettingsDraft(core);
        this.preselect = featureId;
        if (core.configurationReadOnly()) {
            setMessage(Component.translatable(UiKeys.MESSAGE_READ_ONLY), true);
        }
    }

    @Override
    public SettingsDraft draft() {
        return draft;
    }

    /** The sidebar state; for development game tests. */
    public SettingsNavigation navigation() {
        return navigation;
    }

    /** Whether the panel currently shows a text whose translation key is {@code key}; for development game tests. */
    public boolean panelShows(String key) {
        return panel != null && panel.children().stream().anyMatch(row -> row.text != null
                && row.text.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                && t.getKey().equals(key));
    }

    // ---- Layout ----

    private int sidebarWidth() {
        if (width >= 640) {
            return 160;
        }
        return width >= 400 ? 120 : 0;
    }

    /**
     * Left edge of the sidebar: on wide windows the sidebar and the bounded panel are centered together, so the
     * workspace does not spread across an ultrawide screen.
     */
    private int workspaceLeft() {
        int used = sidebarWidth() + GAP + PANEL_MAX;
        return width - 2 * GAP > used ? (width - used) / 2 : GAP;
    }

    /** Below 400 GUI px the sidebar becomes a rail with a drawer (settings-ux-v1.md §10). */
    public boolean compact() {
        return sidebarWidth() == 0;
    }

    @Override
    protected void init() {
        CoreRuntime core = draft.core();
        if (navigation == null) {
            navigation = new SettingsNavigation(entries(core), preselect);
        }
        int titleWidth = Math.min(font.width(title), width - 2 * GAP);
        addRenderableWidget(new StringWidget((width - titleWidth) / 2, 6, titleWidth, font.lineHeight, title, font));
        int searchWidth = Math.min(300, width - 2 * GAP - (compact() ? 26 : 0));
        int searchX = compact() ? GAP + 26 : (width - searchWidth) / 2;
        EditBox search = new EditBox(font, searchX, 20, searchWidth, 20, Component.translatable(UiKeys.SEARCH_HINT));
        search.setHint(Component.translatable(UiKeys.SEARCH_HINT));
        search.setValue(navigation.query());
        search.setResponder(value -> {
            navigation.setQuery(value);
            if (compact() && navigation.searching()) {
                drawerOpen = true;
            }
            rebuildKeepingSearchFocus();
        });
        addRenderableWidget(search);

        if (navigation.categories().isEmpty()) {
            var empty = new MultiLineTextWidget(Component.translatable(UiKeys.EMPTY_REGISTRY), font)
                    .setMaxWidth(Math.min(320, width - 2 * GAP)).setCentered(true);
            empty.setX((width - empty.getWidth()) / 2);
            empty.setY(TOP + 20);
            addRenderableWidget(empty);
        } else {
            int bodyHeight = Math.max(40, height - TOP - FOOTER);
            if (compact()) {
                Button rail = Button.builder(Component.literal("☰"), b -> {
                    drawerOpen = !drawerOpen;
                    rebuildWidgets();
                }).bounds(GAP, 20, 20, 20).tooltip(Tooltip.create(Component.translatable(UiKeys.NAV_DRAWER))).build();
                addRenderableWidget(rail);
            }
            if (!compact() || drawerOpen) {
                int navWidth = compact() ? Math.min(200, width - 2 * GAP) : sidebarWidth();
                nav = new NavList(minecraft, navWidth, bodyHeight, TOP);
                nav.setX(compact() ? GAP : workspaceLeft());
                nav.setRows(font, navigation.rows(this::flagged), this::navLabel, this::onNav);
                nav.setScrollAmount(navScroll);
                nav.revealSelected();
                addRenderableWidget(nav);
                if (!navigation.hasResults()) {
                    addRenderableWidget(new StringWidget(GAP + 4, TOP + 4, navWidth - 8, font.lineHeight,
                            Component.translatable(UiKeys.NO_MATCHES), font));
                }
            } else {
                nav = null;
            }
            if (!compact() || !drawerOpen) {
                panel(bodyHeight);
            } else {
                panel = null;
            }
        }

        messageWidget = new StringWidget(GAP, height - FOOTER + 4, width - 2 * GAP, font.lineHeight, message, font);
        messageWidget.setMaxWidth(width - 2 * GAP);
        addRenderableWidget(messageWidget);
        setMessage(message, messageIsError);
        int buttonWidth = Math.min(150, (width - 3 * GAP) / 2);
        int footerY = height - 26;
        Button done = Button.builder(Component.translatable(UiKeys.DONE), b -> done())
                .bounds(width / 2 - buttonWidth - GAP / 2, footerY, buttonWidth, 20).build();
        done.active = !core.configurationReadOnly();
        addRenderableWidget(done);
        addRenderableWidget(Button.builder(Component.translatable(UiKeys.CANCEL), b -> onClose())
                .bounds(width / 2 + GAP / 2, footerY, buttonWidth, 20).build());
    }

    /** The selected feature's header (sticky) and scrolling panel. */
    private void panel(int bodyHeight) {
        FeatureDescriptor descriptor = selectedDescriptor();
        if (descriptor == null) {
            panel = null;
            return;
        }
        CoreRuntime core = draft.core();
        String id = descriptor.id();
        int left = compact() ? GAP : workspaceLeft() + sidebarWidth() + GAP;
        int area = width - left - GAP;
        int panelWidth = Math.min(PANEL_MAX, area);
        int px = left + (area - panelWidth) / 2;
        int contentWidth = panelWidth - 16;

        // Header: name and switch (or Retry disable for an UNKNOWN feature), then the status line.
        int controlWidth;
        if (FeatureStatus.offersRetryDisable(core, id)) {
            Button retry = Button.builder(Component.translatable(UiKeys.RETRY_DISABLE), b -> retryDisable(id))
                    .bounds(px + panelWidth - 92, TOP, 90, 20)
                    .tooltip(Tooltip.create(Component.translatable(UiKeys.TOOLTIP_UNKNOWN))).build();
            addRenderableWidget(retry);
            controlWidth = 92;
        } else {
            FeatureSwitch toggle = new FeatureSwitch(Component.translatable(descriptor.nameKey()),
                    draft.choice(id).enabled(), on -> {
                        draft.setEnabled(id, on);
                        rebuildWidgets(); // the hint and dimming follow the draft
                    });
            toggle.setX(px + panelWidth - FeatureSwitch.WIDTH - 2);
            toggle.setY(TOP);
            toggle.active = draft.canToggle(id);
            if (!draft.editable()) {
                toggle.setTooltip(Tooltip.create(Component.translatable(UiKeys.MESSAGE_READ_ONLY)));
            } else if (core.compatibility(id).hasConflicts()) {
                toggle.setTooltip(Tooltip.create(Texts.conflictReasons(core, id)));
            }
            addRenderableWidget(toggle);
            controlWidth = FeatureSwitch.WIDTH + 4;
        }
        Component name = Component.translatable(descriptor.nameKey()).copy().withStyle(ChatFormatting.BOLD);
        StringWidget nameWidget = new StringWidget(px + 4, TOP + 1, panelWidth - controlWidth - 8, font.lineHeight,
                name, font);
        nameWidget.setMaxWidth(panelWidth - controlWidth - 8);
        addRenderableWidget(nameWidget);
        Component status = Texts.status(core, id).copy()
                .withColor(Texts.needsAttention(core, id) ? PanelList.WARNING : 0xFFB8D8B8);
        StringWidget statusWidget = new StringWidget(px + 4, TOP + 12, panelWidth - controlWidth - 8,
                font.lineHeight, status, font);
        statusWidget.setMaxWidth(panelWidth - controlWidth - 8);
        addRenderableWidget(statusWidget);

        panel = new PanelList(minecraft, panelWidth, bodyHeight - HEADER, TOP + HEADER, contentWidth);
        panel.setX(px);
        List<PanelList.Row> rows = FeaturePanel.rows(font, this, draft, descriptor, contentWidth,
                error -> showFieldError(id, error));
        panel.setRows(rows);
        if (id.equals(panelFeature)) {
            panel.setScrollAmount(panelScroll.getOrDefault(id, 0.0));
        }
        panelFeature = id;
        addRenderableWidget(panel);
        if (pendingReveal != null) {
            for (PanelList.Row row : rows) {
                if (row instanceof PanelList.SettingRow s && pendingReveal.equals(s.settingKey)) {
                    panel.reveal(s);
                    s.highlight();
                }
            }
            pendingReveal = null;
        }
    }

    private void showFieldError(String featureId, Component error) {
        if (error.getString().isEmpty()) {
            fieldErrors.remove(featureId);
        } else {
            fieldErrors.put(featureId, error);
        }
        Component shown = fieldErrors.values().stream().findFirst().orElse(null);
        if (shown != null) {
            setMessage(shown, true);
        } else if (messageIsError && fieldErrorShown) {
            setMessage(Component.empty(), false);
        }
        fieldErrorShown = shown != null;
    }

    private boolean fieldErrorShown;

    // ---- Navigation ----

    private Component navLabel(SettingsNavigation.Row row) {
        return switch (row.kind()) {
            case CATEGORY -> Component.translatable(UiKeys.category(row.category()));
            case FEATURE -> Component.translatable(draft.core().registry().require(row.featureId()).descriptor()
                    .nameKey());
            case SETTING -> Component.translatable(draft.core().registry().require(row.featureId()).descriptor()
                    .setting(row.settingKey()).orElseThrow().nameKey(row.featureId()));
        };
    }

    private void onNav(SettingsNavigation.Row row, Boolean expand) {
        switch (row.kind()) {
            case CATEGORY -> {
                if (expand == null) {
                    navigation.clickCategory(row.category());
                } else {
                    navigation.setExpanded(row.category(), expand);
                }
            }
            case FEATURE -> {
                navigation.select(row.featureId());
                drawerOpen = false;
            }
            case SETTING -> {
                navigation.select(row.featureId());
                pendingReveal = row.settingKey();
                drawerOpen = false;
            }
        }
        rebuildWidgets();
    }

    private boolean flagged(String featureId) {
        FeatureDescriptor d = draft.core().registry().require(featureId).descriptor();
        return d.settings().stream().anyMatch(s -> draft.hasInvalidInput(featureId, s.key()));
    }

    private FeatureDescriptor selectedDescriptor() {
        String id = navigation.selected();
        return id == null ? null : draft.core().registry().require(id).descriptor();
    }

    private static List<SettingsNavigation.FeatureEntry> entries(CoreRuntime core) {
        var entries = new ArrayList<SettingsNavigation.FeatureEntry>();
        for (Feature feature : core.registry().features()) {
            FeatureDescriptor d = feature.descriptor();
            var settings = new ArrayList<SettingsNavigation.SettingEntry>();
            for (SettingDescriptor s : d.settings()) {
                List<String> options = s instanceof SettingDescriptor.EnumSetting e
                        ? e.values().stream().map(v -> I18n.get(UiKeys.enumValue(d.id(), s.key(), v))).toList()
                        : List.of();
                settings.add(new SettingsNavigation.SettingEntry(s.key(), I18n.get(s.nameKey(d.id())), options));
            }
            entries.add(new SettingsNavigation.FeatureEntry(d.category(), d.id(), I18n.get(d.nameKey()),
                    I18n.get(d.descriptionKey()), settings));
        }
        return entries;
    }

    @Override
    protected void rebuildWidgets() {
        if (panel != null && panelFeature != null) {
            panelScroll.put(panelFeature, panel.scrollAmount());
        }
        if (nav != null) {
            navScroll = nav.scrollAmount();
        }
        super.rebuildWidgets();
    }

    private void rebuildKeepingSearchFocus() {
        rebuildWidgets();
        children().stream().filter(c -> c instanceof EditBox).findFirst().ifPresent(box -> {
            setFocused(box);
            ((EditBox) box).setCursorPosition(((EditBox) box).getValue().length());
        });
    }

    // ---- Actions ----

    private void retryDisable(String id) {
        TransitionOutcome outcome = draft.retryDisable(id);
        setMessage(Component.translatable(outcome.failed() ? UiKeys.MESSAGE_RETRY_FAILED
                : UiKeys.MESSAGE_RETRY_SUCCEEDED), outcome.failed());
        rebuildWidgets();
    }

    private void done() {
        CoreRuntime core = draft.core();
        Set<String> pendingBefore = pendingRestart(core);
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

    private static Set<String> pendingRestart(CoreRuntime core) {
        var pending = new HashSet<String>();
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
