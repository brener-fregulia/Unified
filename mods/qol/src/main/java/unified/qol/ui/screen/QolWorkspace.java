package unified.qol.ui.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import unified.qol.QolUnifiedClient;
import unified.qol.core.CoreRuntime;
import unified.qol.core.Feature;
import unified.qol.core.FeatureDescriptor;
import unified.qol.core.SettingDescriptor;
import unified.qol.core.TransitionOutcome;
import unified.qol.ui.DraftScreen;
import unified.qol.ui.FeatureStatus;
import unified.qol.ui.SettingsDraft;
import unified.qol.ui.UiKeys;
import unified.settings.api.PanelArea;
import unified.settings.api.SettingsWorkspace;
import unified.settings.api.WorkspaceFeature;
import unified.settings.api.WorkspaceHost;
import unified.settings.api.WorkspaceSetting;
import unified.settings.ui.UnifiedSettingsScreen;

/**
 * Unified: QoL's part of the shared settings workspace (docs/architecture/unified-settings-integration.md §20): its
 * features for the shared sidebar and search, and each feature's panel exactly as the QoL workspace drew it (ON/OFF
 * switch, status, description, comparison, tools and inline settings, settings-ux-v1.md). Every valid change is
 * committed at once through this session's {@link SettingsDraft}: validated, saved, then applied (§18). QoL's
 * Configuration Service stays the only writer of its configuration.
 */
public final class QolWorkspace implements SettingsWorkspace, SettingRows.Host {
    private static final int HEADER = 28;
    /** Setting rows stop growing here, so labels stay near their controls on wide screens. */
    private static final int ROW_MAX = 384;

    private final SettingsDraft draft;
    private WorkspaceHost host;
    /** Integer text that is not a valid value yet, per feature.key, kept out of the draft (§18). */
    private final Map<String, String> pendingTexts = new HashMap<>();
    private final Map<String, Component> pendingErrors = new HashMap<>();
    private final Map<String, Double> panelScroll = new HashMap<>();
    /** The last commit could not be saved; the draft still holds the change until Retry or Undo. */
    private boolean saveFailed;
    private PanelList panel;
    private String panelFeature;

    public QolWorkspace(CoreRuntime core) {
        this.draft = new SettingsDraft(core);
    }

    /** Opens the shared workspace with {@code featureId} selected (a contextual entry point, Q-SU8). */
    public static Screen open(Screen parent, String featureId) {
        return new UnifiedSettingsScreen(parent, QolUnifiedClient.MOD_ID, featureId);
    }

    /** QoL's workspace session in {@code screen}, if it is the shared workspace. */
    public static Optional<QolWorkspace> of(Screen screen) {
        return screen instanceof UnifiedSettingsScreen workspace
                && workspace.workspace(QolUnifiedClient.MOD_ID) instanceof QolWorkspace qol
                ? Optional.of(qol) : Optional.empty();
    }

    /** The draft {@code screen} edits: a QoL tool screen's, or QoL's session in the shared workspace (§16). */
    public static SettingsDraft draftShown(Screen screen) {
        if (screen instanceof DraftScreen draftScreen) {
            return draftScreen.draft();
        }
        return of(screen).map(QolWorkspace::draft).orElse(null);
    }

    public SettingsDraft draft() {
        return draft;
    }

    /** True while the last change could not be saved; for development game tests. */
    public boolean saveFailedNow() {
        return saveFailed;
    }

    /** Whether the panel currently shows a text whose translation key is {@code key}; for development game tests. */
    public boolean panelShows(String key) {
        return panel != null && panel.children().stream().anyMatch(row -> row.text != null
                && row.text.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                && t.getKey().equals(key));
    }

    // ---- Shared workspace contract ----

    @Override
    public void attach(WorkspaceHost host) {
        this.host = host;
        if (draft.core().configurationReadOnly()) {
            host.setMessage(Component.translatable(UiKeys.MESSAGE_READ_ONLY), true);
        }
    }

    /** Every registered feature: categories in catalog order, features in registry order within them. */
    @Override
    public List<WorkspaceFeature> features() {
        var entries = new ArrayList<WorkspaceFeature>();
        List<Feature> features = new ArrayList<>(draft.core().registry().features());
        features.sort(Comparator.comparingInt(f -> f.descriptor().category().ordinal()));
        for (Feature feature : features) {
            FeatureDescriptor d = feature.descriptor();
            var settings = new ArrayList<WorkspaceSetting>();
            for (SettingDescriptor s : d.settings()) {
                List<String> options = s instanceof SettingDescriptor.EnumSetting e
                        ? e.values().stream().map(v -> I18n.get(UiKeys.enumValue(d.id(), s.key(), v))).toList()
                        : List.of();
                settings.add(new WorkspaceSetting(s.key(), s.nameKey(d.id()), I18n.get(s.nameKey(d.id())), options));
            }
            entries.add(new WorkspaceFeature(d.category().name().toLowerCase(java.util.Locale.ROOT),
                    UiKeys.category(d.category()), d.id(), d.nameKey(), I18n.get(d.nameKey()),
                    I18n.get(d.descriptionKey()), settings));
        }
        return entries;
    }

    @Override
    public void opened() {
        if (!saveFailed && draft.editable() && draft.changed()) {
            commitNow(); // a tool screen (e.g. the Keyboard Map layout) changed the draft
        }
    }

    /** The selected feature's header (sticky) and scrolling panel. */
    @Override
    public void panel(String featureId, PanelArea area) {
        CoreRuntime core = draft.core();
        FeatureDescriptor descriptor = core.registry().require(featureId).descriptor();
        String id = descriptor.id();
        var font = area.getFont();
        int px = area.getX();
        int top = area.getY();
        int panelWidth = area.getWidth();
        int contentWidth = panelWidth - 16;

        // Header: name and switch (or Retry disable for an UNKNOWN feature), then the status line.
        int controlWidth;
        if (FeatureStatus.offersRetryDisable(core, id)) {
            Button retry = Button.builder(Component.translatable(UiKeys.RETRY_DISABLE), b -> retryDisable(id))
                    .bounds(px + panelWidth - 92, top, 90, 20)
                    .tooltip(Tooltip.create(Component.translatable(UiKeys.TOOLTIP_UNKNOWN))).build();
            area.add(retry);
            controlWidth = 92;
        } else {
            FeatureSwitch toggle = new FeatureSwitch(Component.translatable(descriptor.nameKey()),
                    draft.choice(id).enabled(), on -> {
                        draft.setEnabled(id, on);
                        changed(); // saved and applied now; the hint and dimming follow
                    });
            toggle.setX(px + panelWidth - FeatureSwitch.WIDTH - 2);
            toggle.setY(top);
            toggle.active = draft.canToggle(id);
            if (!draft.editable()) {
                toggle.setTooltip(Tooltip.create(Component.translatable(UiKeys.MESSAGE_READ_ONLY)));
            } else if (core.compatibility(id).hasConflicts()) {
                toggle.setTooltip(Tooltip.create(Texts.conflictReasons(core, id)));
            }
            area.add(toggle);
            controlWidth = FeatureSwitch.WIDTH + 4;
        }
        Component name = Component.translatable(descriptor.nameKey()).copy().withStyle(ChatFormatting.BOLD);
        StringWidget nameWidget = new StringWidget(px + 4, top + 1, panelWidth - controlWidth - 8, font.lineHeight,
                name, font);
        nameWidget.setMaxWidth(panelWidth - controlWidth - 8);
        area.add(nameWidget);
        Component status = Texts.status(core, id).copy()
                .withColor(Texts.needsAttention(core, id) ? PanelList.WARNING : 0xFFB8D8B8);
        StringWidget statusWidget = new StringWidget(px + 4, top + 11, panelWidth - controlWidth - 8,
                font.lineHeight, status, font);
        statusWidget.setMaxWidth(panelWidth - controlWidth - 8);
        area.add(statusWidget);

        int rowWidth = Math.min(contentWidth, ROW_MAX);
        List<PanelList.Row> rows = FeaturePanel.rows(font, area.getScreen(), draft, descriptor, rowWidth, this);
        int listHeight = Math.min(area.getHeight() - HEADER, PanelList.contentHeight(rows));
        PanelList list = new PanelList(Minecraft.getInstance(), panelWidth, listHeight, top + HEADER, rowWidth);
        list.setX(px);
        list.setRows(rows);
        if (id.equals(panelFeature)) {
            list.setScrollAmount(panelScroll.getOrDefault(id, 0.0));
        }
        panelFeature = id;
        panel = list;
        area.add(list);
        String reveal = area.getRevealSetting();
        if (reveal != null) {
            for (PanelList.Row row : rows) {
                if (row instanceof PanelList.SettingRow s && reveal.equals(s.settingKey)) {
                    list.reveal(s);
                    s.highlight();
                }
            }
        }
    }

    @Override
    public void beforeRebuild() {
        if (panel != null && panelFeature != null) {
            panelScroll.put(panelFeature, panel.scrollAmount());
        }
        panel = null;
    }

    @Override
    public boolean flagged(String featureId) {
        return pendingTexts.keySet().stream().anyMatch(field -> field.startsWith(featureId + "."));
    }

    @Override
    public boolean saveFailed() {
        return saveFailed;
    }

    @Override
    public void retrySave() {
        changed();
    }

    @Override
    public void undoChange() {
        draft.revert();
        saveFailed = false;
        host.setMessage(Component.empty(), false);
    }

    /**
     * Back and Escape close the workspace. Valid changes are already saved and applied; integer text that is not a
     * valid value was never part of the draft. A change whose save failed is dropped, so the draft never outlives
     * the screen with unsaved edits.
     */
    @Override
    public void closed() {
        if (saveFailed) {
            draft.revert();
        }
    }

    // ---- Auto-apply (§18) ----

    /** A control changed the draft: validate, save, then apply, at once. */
    @Override
    public void changed() {
        commitNow();
        host.requestRebuild(); // rebuilt on the next tick, outside the widget event being handled
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
            case NO_CHANGE, APPLIED -> host.setMessage(!pendingBefore.containsAll(pendingRestart(core))
                    ? Component.translatable(UiKeys.MESSAGE_RESTART) : Component.empty(), false);
            case INVALID -> host.setMessage(Component.translatable(UiKeys.MESSAGE_INVALID), true);
            case READ_ONLY -> {
                draft.revert();
                host.setMessage(Component.translatable(UiKeys.MESSAGE_READ_ONLY), true);
            }
            case SAVE_FAILED -> {
                // Nothing was applied; the change waits in the draft for Retry, or Undo restores the saved state.
                saveFailed = true;
                host.setMessage(Component.translatable(UiKeys.MESSAGE_SAVE_FAILED_RETRY), true);
            }
            case APPLIED_WITH_FAILURES -> host.setMessage(Component.translatable(UiKeys.MESSAGE_APPLY_FAILED,
                    Texts.featureNames(core, outcome.failures())), true);
        }
    }

    @Override
    public Optional<String> pendingText(String featureId, String key) {
        return Optional.ofNullable(pendingTexts.get(featureId + "." + key));
    }

    @Override
    public void pendingText(String featureId, String key, String text, Component error) {
        String field = featureId + "." + key;
        if (text == null) {
            pendingTexts.remove(field);
            pendingErrors.remove(field);
            if (!saveFailed && host.messageIsError() && pendingErrors.isEmpty()) {
                host.setMessage(Component.empty(), false);
            }
        } else {
            pendingTexts.put(field, text);
            pendingErrors.put(field, error);
            host.setMessage(error, true);
        }
        host.requestRebuild();
    }

    // ---- Actions ----

    private void retryDisable(String id) {
        TransitionOutcome outcome = draft.retryDisable(id);
        host.setMessage(Component.translatable(outcome.failed() ? UiKeys.MESSAGE_RETRY_FAILED
                : UiKeys.MESSAGE_RETRY_SUCCEEDED), outcome.failed());
        host.rebuildNow();
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
}
