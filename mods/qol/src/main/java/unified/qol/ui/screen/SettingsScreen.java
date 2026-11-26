package unified.qol.ui.screen;

import unified.qol.core.CoreRuntime;
import unified.qol.core.Feature;
import unified.qol.core.FeatureDescriptor;
import unified.qol.core.SettingDescriptor;
import unified.qol.core.TransitionOutcome;
import unified.qol.ui.DraftScreen;
import unified.qol.ui.FeatureStatus;
import unified.qol.ui.SettingsDraft;
import unified.qol.ui.SettingsNavigation;
import unified.qol.ui.UiKeys;
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
 * settings. Every valid change is committed at once through the session's {@link SettingsDraft}: validated, saved,
 * then applied (settings-ux-v1.md §18). Back and Escape only close. Navigation and search never touch the draft. The
 * screen keeps its navigation state in fields, so it survives opening a tool screen and coming back.
 */
public final class SettingsScreen extends Screen implements DraftScreen, SettingRows.Host {
    private static final int GAP = 6;
    private static final int TOP = 44;
    /** Footer: the Back row, plus a message line above it while there is a message. */
    private static final int FOOTER = 28;
    private static final int MESSAGE_LINE = 12;
    private static final int HEADER = 28;
    private static final int PANEL_MAX = 400;
    /** Setting rows stop growing here, so labels stay near their controls on wide screens. */
    private static final int ROW_MAX = 384;

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
    /** Integer text that is not a valid value yet, per feature.key, kept out of the draft (§18). */
    private final Map<String, String> pendingTexts = new HashMap<>();
    private final Map<String, Component> pendingErrors = new HashMap<>();
    /** The last commit could not be saved; the draft still holds the change until Retry or Undo. */
    private boolean saveFailed;
    private boolean rebuildRequested;

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

    /** Breakpoints (settings-ux-v1.md §19): below 400 a drawer, then 120, 150 and 170 GUI px. */
    private int sidebarWidth() {
        if (width >= 960) {
            return 170;
        }
        if (width >= 640) {
            return 150;
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

    /** Width of the sidebar and panel together, or of the whole window when that is narrower. */
    private int workspaceWidth() {
        return Math.min(sidebarWidth() + GAP + PANEL_MAX, width - 2 * GAP);
    }

    private int footerHeight() {
        return FOOTER + (message.getString().isEmpty() ? 0 : MESSAGE_LINE);
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
        if (!saveFailed && draft.editable() && draft.changed()) {
            commitNow(); // a tool screen (e.g. the Keyboard Map layout) changed the draft
        }
        int titleWidth = Math.min(font.width(title), width - 2 * GAP);
        addRenderableWidget(new StringWidget((width - titleWidth) / 2, 5, titleWidth, font.lineHeight, title, font));
        // The search box spans the workspace (sidebar + panel) so it lines up with what it filters.
        int searchX = compact() ? GAP + 24 : workspaceLeft();
        int searchWidth = compact() ? width - searchX - GAP : workspaceWidth();
        EditBox search = new EditBox(font, searchX, 18, searchWidth, 20, Component.translatable(UiKeys.SEARCH_HINT));
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
            int bodyHeight = Math.max(40, height - TOP - footerHeight());
            if (compact()) {
                Button rail = Button.builder(Component.literal("☰"), b -> {
                    drawerOpen = !drawerOpen;
                    rebuildWidgets();
                }).bounds(GAP, 18, 20, 20).tooltip(Tooltip.create(Component.translatable(UiKeys.NAV_DRAWER))).build();
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

        int left = compact() ? GAP : workspaceLeft();
        int span = compact() ? width - 2 * GAP : workspaceWidth();
        messageWidget = new StringWidget(left, height - footerHeight() + 3, span, font.lineHeight, message, font);
        messageWidget.setMaxWidth(span);
        addRenderableWidget(messageWidget);
        setMessage(message, messageIsError);
        int footerY = height - 24;
        if (saveFailed) {
            // A failed save: try the same change again, or drop it and show what is stored.
            int w = Math.min(110, (span - 2 * GAP) / 3);
            int x = left + (span - 3 * w - 2 * GAP) / 2;
            addRenderableWidget(Button.builder(Component.translatable(UiKeys.RETRY_SAVE), b -> changed())
                    .bounds(x, footerY, w, 20).build());
            addRenderableWidget(Button.builder(Component.translatable(UiKeys.UNDO_CHANGE), b -> {
                draft.revert();
                saveFailed = false;
                setMessage(Component.empty(), false);
                rebuildWidgets();
            }).bounds(x + w + GAP, footerY, w, 20).build());
            addRenderableWidget(Button.builder(Component.translatable(UiKeys.BACK), b -> onClose())
                    .bounds(x + 2 * (w + GAP), footerY, w, 20).build());
        } else {
            int w = Math.min(150, span);
            addRenderableWidget(Button.builder(Component.translatable(UiKeys.BACK), b -> onClose())
                    .bounds(left + (span - w) / 2, footerY, w, 20).build());
        }
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
        int area = compact() ? width - 2 * GAP : workspaceLeft() + workspaceWidth() - left;
        int panelWidth = Math.min(PANEL_MAX, area);
        int px = left;
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
                        changed(); // saved and applied now; the hint and dimming follow
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
        StringWidget statusWidget = new StringWidget(px + 4, TOP + 11, panelWidth - controlWidth - 8,
                font.lineHeight, status, font);
        statusWidget.setMaxWidth(panelWidth - controlWidth - 8);
        addRenderableWidget(statusWidget);

        int rowWidth = Math.min(contentWidth, ROW_MAX);
        List<PanelList.Row> rows = FeaturePanel.rows(font, this, draft, descriptor, rowWidth, this);
        int listHeight = Math.min(bodyHeight - HEADER, PanelList.contentHeight(rows));
        panel = new PanelList(minecraft, panelWidth, listHeight, TOP + HEADER, rowWidth);
        panel.setX(px);
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

    // ---- Auto-apply (§18) ----

    /** A control changed the draft: validate, save, then apply, at once. */
    @Override
    public void changed() {
        commitNow();
        rebuildRequested = true; // rebuilt on the next tick, outside the widget event being handled
    }

    private void commitNow() {
        if (!draft.changed()) {
            return;
        }
        CoreRuntime core = draft.core();
        Set<String> pendingBefore = pendingRestart(core);
        SettingsDraft.CommitOutcome outcome = draft.commit();
        saveFailed = false;
        switch (outcome.kind()) {
            case NO_CHANGE, APPLIED -> setMessage(!pendingBefore.containsAll(pendingRestart(core))
                    ? Component.translatable(UiKeys.MESSAGE_RESTART) : Component.empty(), false);
            case INVALID -> setMessage(Component.translatable(UiKeys.MESSAGE_INVALID), true);
            case READ_ONLY -> {
                draft.revert();
                setMessage(Component.translatable(UiKeys.MESSAGE_READ_ONLY), true);
            }
            case SAVE_FAILED -> {
                // Nothing was applied; the change waits in the draft for Retry, or Undo restores the saved state.
                saveFailed = true;
                setMessage(Component.translatable(UiKeys.MESSAGE_SAVE_FAILED_RETRY), true);
            }
            case APPLIED_WITH_FAILURES -> setMessage(Component.translatable(UiKeys.MESSAGE_APPLY_FAILED,
                    Texts.featureNames(core, outcome.failures())), true);
        }
    }

    /** True while the last change could not be saved; for development game tests. */
    public boolean saveFailed() {
        return saveFailed;
    }

    @Override
    public java.util.Optional<String> pendingText(String featureId, String key) {
        return java.util.Optional.ofNullable(pendingTexts.get(featureId + "." + key));
    }

    @Override
    public void pendingText(String featureId, String key, String text, Component error) {
        String field = featureId + "." + key;
        if (text == null) {
            pendingTexts.remove(field);
            pendingErrors.remove(field);
            if (!saveFailed && messageIsError && pendingErrors.isEmpty()) {
                setMessage(Component.empty(), false);
            }
        } else {
            pendingTexts.put(field, text);
            pendingErrors.put(field, error);
            setMessage(error, true);
        }
        rebuildRequested = true;
    }

    @Override
    public void tick() {
        super.tick();
        if (rebuildRequested) {
            rebuildRequested = false;
            rebuildWidgets();
        }
    }


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
        return pendingTexts.keySet().stream().anyMatch(field -> field.startsWith(featureId + "."));
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
        if (messageWidget != null && message.getString().isEmpty() != text.getString().isEmpty()) {
            rebuildRequested = true; // the footer grows or shrinks by the message line
        }
        message = text;
        messageIsError = error;
        if (messageWidget != null) {
            messageWidget.setMessage(error ? text.copy().withStyle(ChatFormatting.RED) : text);
        }
    }

    /**
     * Back and Escape close the workspace. Valid changes are already saved and applied; integer text that is not a
     * valid value was never part of the draft. A change whose save failed is dropped, so the draft never outlives
     * the screen with unsaved edits.
     */
    @Override
    public void onClose() {
        if (saveFailed) {
            draft.revert();
        }
        minecraft.gui.setScreen(parent);
    }
}
