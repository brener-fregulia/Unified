package io.github.brener_fregulia.qolunified.feature.keyboardmap;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.brener_fregulia.qolunified.core.SettingsSnapshot;
import io.github.brener_fregulia.qolunified.feature.keyboardmap.BindingFilter.Visibility;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The keyboard map screen (keymap-v1.md §6, §7). The catalog is built when the screen opens and after each change;
 * nothing is rebuilt per frame. Mappings change only through {@link RemapSession}, after an explicit Change, Clear or
 * Reset. The world stays visible: vanilla draws its in-world background, and the panels are translucent.
 */
public final class KeyboardMapScreen extends Screen {
    private static final int PANEL = 0xB0101010;
    private static final int KEY_UNBOUND = 0x90505050;
    private static final int KEY_UNBOUND_HIDDEN = 0x40303030;
    private static final int KEY_SINGLE = 0xD02E7D32;
    private static final int KEY_OVERLAP = 0xE0C62828;
    private static final int OUTLINE = 0xFF4FC3F7;
    private static final int HOVER = 0xA0FFFFFF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFF9A9A9A;
    private static final int TEXT_OVERLAP = 0xFFFF8A80;
    private static final int TEXT_SINGLE = 0xFFB9F6CA;
    private static final int TEXT_HEADER = 0xFFFFD866;
    private static final int ROW_HEIGHT = 11;
    private static final int MAX_LABEL_LINES = 6;

    private final Screen parent;
    private final Consumer<String> layoutWriter;
    private final MinecraftBindings source;
    private final RemapSession session;
    private final Selection selection = new Selection();

    private BindingCatalog catalog;
    private String layoutId;
    private String query = "";
    private String category;
    private Visibility visibility;
    private boolean showOtherSections;
    private boolean showListTab;
    private int scroll;

    private KeyboardMapGeometry geometry;
    private KeyboardLayout layout;
    private List<KeyCap> drawnKeys = List.of();
    private int originX;
    private int originY;
    private final List<Row> rows = new ArrayList<>();
    private final List<Chip> chips = new ArrayList<>();
    private InputId hovered;

    private EditBox search;
    private Button change;
    private Button clear;
    private Button reset;
    private Button confirm;
    private Button cancel;

    private record Row(Binding binding, String header) {
    }

    private record Chip(InputId input, int x, int y, int width, int height, String label) {
    }

    /**
     * @param layoutWriter persists a layout chosen in the toolbar: through the Core configuration, or into the
     *     settings screen's draft when opened from there
     */
    public KeyboardMapScreen(Screen parent, String initialLayout, Consumer<String> layoutWriter) {
        super(Component.translatable(KeyboardMapTexts.TITLE));
        this.parent = parent;
        this.layoutWriter = Objects.requireNonNull(layoutWriter, "layoutWriter");
        this.source = new MinecraftBindings(Minecraft.getInstance().options);
        this.session = new RemapSession(() -> catalog, source);
        SettingsSnapshot settings = settings();
        this.layoutId = initialLayout != null ? initialLayout : settings.getEnum(KeyboardMapSettings.LAYOUT);
        this.visibility = Visibility.byId(settings.getEnum(KeyboardMapSettings.DEFAULT_FILTER));
        this.catalog = source.snapshot();
    }

    private static SettingsSnapshot settings() {
        SettingsSnapshot active = KeyboardMapFeature.activeSettings();
        return active != null ? active : SettingsSnapshot.defaults(KeyboardMapSettings.DESCRIPTOR);
    }

    @Override
    protected void init() {
        layout = KeyboardLayouts.byId(layoutId);
        geometry = KeyboardMapGeometry.plan(width, height, layout);
        int m = geometry.margin();
        int y = geometry.toolbarY();

        // Toolbar: search, category, visibility, layout, and the section toggle when the keyboard is split.
        int x = m;
        int searchWidth = Math.clamp(width / 4, 80, 180);
        search = new EditBox(font, x, y, searchWidth, 20, Component.translatable(KeyboardMapTexts.SEARCH_HINT));
        search.setHint(Component.translatable(KeyboardMapTexts.SEARCH_HINT).withStyle(ChatFormatting.DARK_GRAY));
        search.setMaxLength(64);
        search.setValue(query);
        search.setResponder(text -> {
            query = text;
            scroll = 0;
            rebuildRows();
        });
        addRenderableWidget(search);
        x += searchWidth + 4;

        var categories = new ArrayList<String>();
        categories.add("");
        categories.addAll(catalog.categories());
        if (category != null && !categories.contains(category)) {
            category = null;
        }
        int controls = geometry.split() ? 4 : 3;
        int cycleWidth = Math.clamp((width - x - m) / controls - 4, 50, 140);
        CycleButton<String> categoryButton = CycleButton.<String>builder(
                        c -> c.isEmpty() ? Component.translatable(KeyboardMapTexts.ALL_CATEGORIES) : Component.literal(c),
                        category == null ? "" : category)
                .withValues(categories).displayOnlyValue()
                .create(x, y, cycleWidth, 20, Component.translatable(KeyboardMapTexts.CATEGORY), (b, v) -> {
                    category = v.isEmpty() ? null : v;
                    scroll = 0;
                    rebuildRows();
                });
        categoryButton.setTooltip(Tooltip.create(Component.translatable(KeyboardMapTexts.CATEGORY)));
        addRenderableWidget(categoryButton);
        x += cycleWidth + 4;
        CycleButton<Visibility> showButton = CycleButton.<Visibility>builder(
                        v -> Component.translatable(filterKey(v.id())), visibility)
                .withValues(Visibility.values()).displayOnlyValue()
                .create(x, y, cycleWidth, 20, Component.translatable(KeyboardMapTexts.SHOW), (b, v) -> {
                    visibility = v;
                    scroll = 0;
                    rebuildRows();
                });
        showButton.setTooltip(Tooltip.create(Component.translatable(KeyboardMapTexts.SHOW)));
        addRenderableWidget(showButton);
        x += cycleWidth + 4;
        CycleButton<String> layoutButton = CycleButton.<String>builder(
                        v -> Component.translatable(layoutKey(v)), layoutId)
                .withValues(KeyboardLayouts.US_ANSI, KeyboardLayouts.ABNT2).displayOnlyValue()
                .create(x, y, cycleWidth, 20, Component.translatable(KeyboardMapTexts.LAYOUT), (b, v) -> {
                    layoutId = v;
                    layoutWriter.accept(v);
                    rebuildWidgets();
                });
        layoutButton.setTooltip(Tooltip.create(Component.translatable(KeyboardMapTexts.LAYOUT)));
        addRenderableWidget(layoutButton);
        x += cycleWidth + 4;
        if (geometry.split()) {
            addRenderableWidget(Button.builder(Component.translatable(showOtherSections
                            ? KeyboardMapTexts.MAIN_KEYS : KeyboardMapTexts.MORE_KEYS), b -> {
                        showOtherSections = !showOtherSections;
                        rebuildWidgets();
                    }).bounds(x, y, cycleWidth, 20).build());
        }

        // Bottom bar: Change / Confirm, Clear / Cancel, Reset, vanilla Key Binds, [list tab], Done.
        int by = geometry.bottomBarY();
        int slots = geometry.tabbed() ? 6 : 5;
        int buttonWidth = Math.clamp((width - 2 * m - (slots - 1) * 4) / slots, 40, 110);
        int bx = m;
        change = addRenderableWidget(Button.builder(Component.translatable(KeyboardMapTexts.CHANGE), b -> {
            if (selection.bindingId() != null) {
                session.begin(selection.bindingId());
                updateButtons();
            }
        }).bounds(bx, by, buttonWidth, 20).build());
        confirm = addRenderableWidget(Button.builder(Component.translatable(KeyboardMapTexts.CONFIRM), b -> {
            session.confirm();
            refresh();
        }).bounds(bx, by, buttonWidth, 20).build());
        bx += buttonWidth + 4;
        clear = addRenderableWidget(Button.builder(Component.translatable(KeyboardMapTexts.CLEAR), b -> {
            session.clear(selection.bindingId());
            refresh();
        }).bounds(bx, by, buttonWidth, 20).build());
        cancel = addRenderableWidget(Button.builder(Component.translatable(KeyboardMapTexts.CANCEL), b -> {
            session.cancel();
            updateButtons();
        }).bounds(bx, by, buttonWidth, 20).build());
        bx += buttonWidth + 4;
        reset = addRenderableWidget(Button.builder(Component.translatable(KeyboardMapTexts.RESET), b -> {
            session.reset(selection.bindingId());
            refresh();
        }).bounds(bx, by, buttonWidth, 20).build());
        bx += buttonWidth + 4;
        addRenderableWidget(Button.builder(Component.translatable(KeyboardMapTexts.KEY_BINDS), b ->
                minecraft.gui.setScreen(new KeyBindsScreen(this, minecraft.options)))
                .bounds(bx, by, buttonWidth, 20).build());
        bx += buttonWidth + 4;
        if (geometry.tabbed()) {
            addRenderableWidget(Button.builder(Component.translatable(showListTab
                            ? KeyboardMapTexts.SHOW_KEYBOARD : KeyboardMapTexts.SHOW_LIST), b -> {
                        showListTab = !showListTab;
                        rebuildWidgets();
                    }).bounds(bx, by, buttonWidth, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable(KeyboardMapTexts.DONE), b -> onClose())
                .bounds(width - m - buttonWidth, by, buttonWidth, 20).build());

        catalog = source.snapshot();
        layoutKeyboard();
        rebuildRows();
        updateButtons();
    }

    private static String filterKey(String id) {
        return "qol_unified.feature.keyboard_map.setting.default_filter." + id;
    }

    private static String layoutKey(String id) {
        return "qol_unified.feature.keyboard_map.setting.layout." + id;
    }

    private boolean keyboardVisible() {
        return !geometry.tabbed() || !showListTab;
    }

    private boolean listVisible() {
        return !geometry.tabbed() || showListTab;
    }

    private void layoutKeyboard() {
        drawnKeys = layout.keys(geometry.visibleSections(showOtherSections));
        int unit = geometry.unit();
        int keyboardWidth = KeyboardLayout.width(drawnKeys) * unit;
        originX = geometry.keyboardX() + (geometry.keyboardWidth() - keyboardWidth) / 2;
        originY = geometry.contentTop();
        chips.clear();
        var mouse = new LinkedHashSet<>(MinecraftBindings.standardMouseButtons());
        catalog.inputs().stream().filter(i -> i.kind() == InputId.Kind.MOUSE).forEach(mouse::add);
        int start = originX + font.width(Component.translatable(KeyboardMapTexts.MOUSE)) + 4;
        int end = geometry.keyboardX() + geometry.keyboardWidth();
        int cy = originY + KeyboardLayout.height(drawnKeys) * unit + 4;
        // Full button names when they all fit; otherwise button numbers, so every bound button stays reachable.
        int full = mouse.stream().mapToInt(i -> Math.min(font.width(MinecraftBindings.label(i)) + 8, 80) + 3).sum();
        boolean numbers = start + full > end;
        int cx = start;
        for (InputId input : mouse.stream().sorted(java.util.Comparator.comparingInt(InputId::code)).toList()) {
            String label = numbers ? String.valueOf(input.code()) : MinecraftBindings.label(input);
            int w = Math.min(font.width(label) + 8, 80);
            if (cx + w > end) {
                break;
            }
            chips.add(new Chip(input, cx, cy, w, 12, label));
            cx += w + 3;
        }
    }

    private void rebuildRows() {
        rows.clear();
        var filter = new BindingFilter(query, category, visibility);
        String lastCategory = null;
        for (Binding binding : catalog.bindings()) {
            if (!filter.matches(binding, catalog)) {
                continue;
            }
            if (!binding.category().equals(lastCategory)) {
                rows.add(new Row(null, binding.category()));
                lastCategory = binding.category();
            }
            rows.add(new Row(binding, null));
        }
        scroll = Math.clamp(scroll, 0, maxScroll());
    }

    private void refresh() {
        catalog = source.snapshot();
        layoutKeyboard();
        rebuildRows();
        updateButtons();
    }

    private void updateButtons() {
        if (change == null) {
            return;
        }
        boolean confirming = session.state() == RemapSession.State.CONFIRMING;
        boolean idle = session.state() == RemapSession.State.IDLE;
        Binding selected = selectedBinding();
        change.visible = !confirming;
        clear.visible = !confirming;
        confirm.visible = confirming;
        cancel.visible = confirming;
        change.active = idle && selected != null;
        clear.active = idle && selected != null && !selected.unbound();
        reset.active = idle && selected != null && !selected.isDefault();
    }

    private Binding selectedBinding() {
        return selection.bindingId() == null ? null : catalog.binding(selection.bindingId()).orElse(null);
    }

    // ---- Input ----

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (session.state() == RemapSession.State.CAPTURING) {
            if (event.isEscape()) {
                session.cancel();
            } else {
                InputId input = MinecraftBindings.toInput(InputConstants.getKey(event));
                if (input != null) {
                    session.offer(input);
                    refresh();
                }
            }
            updateButtons();
            return true;
        }
        if (session.state() == RemapSession.State.CONFIRMING && event.isEscape()) {
            session.cancel();
            updateButtons();
            return true;
        }
        if (event.hasControlDownWithQuirk() && event.key() == InputConstants.KEY_F) {
            setFocused(search);
            return true;
        }
        if (event.isEscape() && search.isFocused() && !search.getValue().isEmpty()) {
            search.setValue("");
            return true;
        }
        if ((event.isUp() || event.isDown()) && listVisible() && session.state() == RemapSession.State.IDLE) {
            moveSelection(event.isDown() ? 1 : -1);
            return true;
        }
        return super.keyPressed(event);
    }

    private void moveSelection(int step) {
        List<Binding> visible = rows.stream().map(Row::binding).filter(Objects::nonNull).toList();
        if (visible.isEmpty()) {
            return;
        }
        int index = -1;
        for (int i = 0; i < visible.size(); i++) {
            if (visible.get(i).id().equals(selection.bindingId())) {
                index = i;
            }
        }
        int next = Math.clamp(index + step, 0, visible.size() - 1);
        selection.selectBinding(visible.get(next).id());
        ensureRowVisible(visible.get(next).id());
        updateButtons();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (session.state() == RemapSession.State.CAPTURING) {
            session.offer(InputId.mouse(event.button()));
            refresh();
            return true;
        }
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (session.state() != RemapSession.State.IDLE || event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        double mx = event.x();
        double my = event.y();
        if (keyboardVisible()) {
            InputId input = inputAt(mx, my);
            if (input != null) {
                selectInput(input);
                return true;
            }
        }
        if (listVisible()) {
            Binding binding = rowAt(mx, my);
            if (binding != null) {
                selectBinding(binding.id());
                return true;
            }
        }
        return false;
    }

    /** Same as clicking a key or chip (I-1). */
    public void selectInput(InputId input) {
        selection.selectInput(input);
        catalog.on(selection.highlightedInput(catalog)).stream().findFirst().ifPresent(b -> ensureRowVisible(b.id()));
        updateButtons();
    }

    /** Same as clicking a row (I-2). */
    public void selectBinding(String id) {
        selection.selectBinding(id);
        ensureRowVisible(id);
        updateButtons();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (listVisible() && x >= geometry.panelX() && x < geometry.panelX() + geometry.panelWidth()) {
            scroll = Math.clamp(scroll - (int) Math.signum(scrollY) * ROW_HEIGHT * 3, 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        session.cancel();
        minecraft.gui.setScreen(parent);
    }

    // ---- Hit testing ----

    private int keyX(KeyCap key) {
        return originX + key.x() * geometry.unit();
    }

    private int keyY(KeyCap key) {
        return originY + key.y() * geometry.unit();
    }

    private InputId inputAt(double mx, double my) {
        int unit = geometry.unit();
        for (KeyCap key : drawnKeys) {
            int x = keyX(key);
            int y = keyY(key);
            if (mx >= x && mx < x + key.width() * unit && my >= y && my < y + key.height() * unit) {
                return InputId.key(key.scancode());
            }
        }
        for (Chip chip : chips) {
            if (mx >= chip.x() && mx < chip.x() + chip.width() && my >= chip.y() && my < chip.y() + chip.height()) {
                return chip.input();
            }
        }
        return null;
    }

    private int listTop() {
        return geometry.contentTop() + 2;
    }

    private int listBottom() {
        return geometry.contentBottom();
    }

    private int maxScroll() {
        return geometry == null ? 0 : Math.max(0, rows.size() * ROW_HEIGHT - (listBottom() - listTop()));
    }

    private Binding rowAt(double mx, double my) {
        if (mx < geometry.panelX() || mx >= geometry.panelX() + geometry.panelWidth() || my < listTop()
                || my >= listBottom()) {
            return null;
        }
        int index = (int) ((my - listTop() + scroll) / ROW_HEIGHT);
        return index >= 0 && index < rows.size() ? rows.get(index).binding() : null;
    }

    private void ensureRowVisible(String id) {
        for (int i = 0; i < rows.size(); i++) {
            Binding binding = rows.get(i).binding();
            if (binding != null && binding.id().equals(id)) {
                int top = i * ROW_HEIGHT;
                int height = listBottom() - listTop();
                if (top < scroll) {
                    scroll = Math.max(0, top - ROW_HEIGHT);
                } else if (top + ROW_HEIGHT > scroll + height) {
                    scroll = Math.min(maxScroll(), top + 2 * ROW_HEIGHT - height);
                }
                return;
            }
        }
    }

    // ---- Drawing ----

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        hovered = session.state() == RemapSession.State.IDLE && keyboardVisible() ? inputAt(mouseX, mouseY) : null;
        if (keyboardVisible()) {
            g.fill(geometry.keyboardX() - 2, geometry.contentTop() - 2,
                    geometry.keyboardX() + geometry.keyboardWidth() + 2, geometry.contentBottom() + 2, PANEL);
            drawKeyboard(g);
        }
        if (listVisible()) {
            g.fill(geometry.panelX() - 2, geometry.contentTop() - 2, geometry.panelX() + geometry.panelWidth() + 2,
                    geometry.contentBottom() + 2, PANEL);
            drawList(g, mouseX, mouseY);
        }
        drawStatus(g);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        if (keyboardVisible() && settings().getBoolean(KeyboardMapSettings.CONTEXTUAL_LABELS)) {
            drawContextLabels(g, hovered != null ? hovered : selection.highlightedInput(catalog), hovered != null);
        }
    }

    private BindingFilter filter() {
        return new BindingFilter(query, category, visibility);
    }

    private void drawKeyboard(GuiGraphicsExtractor g) {
        int unit = geometry.unit();
        InputId highlighted = selection.highlightedInput(catalog);
        boolean showUnbound = settings().getBoolean(KeyboardMapSettings.SHOW_UNBOUND_KEYS);
        BindingFilter filter = filter();
        for (KeyCap key : drawnKeys) {
            InputId input = InputId.key(key.scancode());
            int w = key.width() * unit;
            drawInput(g, input, keyX(key), keyY(key), w, key.height() * unit, keyLabel(key, w), highlighted,
                    showUnbound, filter);
        }
        if (!chips.isEmpty()) {
            g.text(font, Component.translatable(KeyboardMapTexts.MOUSE), originX, chips.getFirst().y() + 2, TEXT_DIM);
        }
        for (Chip chip : chips) {
            drawInput(g, chip.input(), chip.x(), chip.y(), chip.width(), chip.height(), chip.label(), highlighted,
                    showUnbound, filter);
        }
    }

    /** Fixed symbol, else the OS name if it fits, else the short name, else the OS name cut to fit. */
    private String keyLabel(KeyCap key, int width) {
        if (key.fixedLabel() != null) {
            return key.fixedLabel();
        }
        String name = MinecraftBindings.label(InputId.key(key.scancode()));
        String shortName = KeyboardLayouts.shortLabel(key.scancode());
        return font.width(name) <= width - 4 || shortName == null ? name : shortName;
    }

    private void drawInput(GuiGraphicsExtractor g, InputId input, int x, int y, int w, int h, String label,
            InputId highlighted, boolean showUnbound, BindingFilter filter) {
        InputState state = catalog.state(input);
        boolean dimmed = !filter.isEmpty() && catalog.on(input).stream().noneMatch(b -> filter.matches(b, catalog));
        int fill = switch (state) {
            case UNBOUND -> showUnbound ? KEY_UNBOUND : KEY_UNBOUND_HIDDEN;
            case SINGLE -> KEY_SINGLE;
            case OVERLAP -> KEY_OVERLAP;
        };
        if (dimmed) {
            fill = (fill & 0x00FFFFFF) | 0x38000000;
        }
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        if (input.equals(highlighted)) {
            g.outline(x, y, w, h, OUTLINE);
            g.outline(x + 1, y + 1, w - 2, h - 2, OUTLINE);
        } else if (input.equals(hovered)) {
            g.outline(x, y, w, h, HOVER);
        }
        int color = dimmed || (state == InputState.UNBOUND && !showUnbound) ? TEXT_DIM : TEXT;
        int textWidth = font.width(label);
        if (textWidth <= w - 2) {
            g.centeredText(font, label, x + w / 2, y + (h - 8) / 2, color);
        } else {
            // Shrink rather than cut, down to half size; cut only beyond that.
            float scale = Math.max(0.5f, (w - 2) / (float) textWidth);
            String fitted = font.plainSubstrByWidth(label, (int) ((w - 2) / scale));
            var pose = g.pose();
            pose.pushMatrix();
            pose.translate(x + w / 2f, y + h / 2f);
            pose.scale(scale, scale);
            g.centeredText(font, fitted, 0, -4, color);
            pose.popMatrix();
        }
        if (state == InputState.OVERLAP) {
            // Warning marker and count, so overlaps never depend on color alone.
            var pose = g.pose();
            pose.pushMatrix();
            pose.translate(x + 2, y + 2);
            pose.scale(0.5f, 0.5f);
            g.text(font, "!" + catalog.on(input).size(), 0, 0, TEXT);
            pose.popMatrix();
        }
    }

    private void drawList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = geometry.panelX();
        int w = geometry.panelWidth();
        int top = listTop();
        int bottom = listBottom();
        if (rows.isEmpty()) {
            g.text(font, Component.translatable(KeyboardMapTexts.NO_MATCHES), x + 4, top + 2, TEXT_DIM);
            return;
        }
        g.enableScissor(x, top, x + w, bottom);
        for (int i = scroll / ROW_HEIGHT; i < rows.size(); i++) {
            int y = top + i * ROW_HEIGHT - scroll;
            if (y >= bottom) {
                break;
            }
            Row row = rows.get(i);
            if (row.header() != null) {
                g.text(font, font.plainSubstrByWidth(row.header(), w - 6), x + 2, y + 2, TEXT_HEADER);
                continue;
            }
            Binding binding = row.binding();
            boolean hoveredRow = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ROW_HEIGHT
                    && mouseY < bottom;
            if (selection.highlights(binding, catalog)) {
                boolean selected = binding.id().equals(selection.bindingId());
                g.fill(x, y, x + w, y + ROW_HEIGHT, selected ? 0x704FC3F7 : 0x404FC3F7);
                g.fill(x, y, x + 2, y + ROW_HEIGHT, OUTLINE);
            } else if (hoveredRow) {
                g.fill(x, y, x + w, y + ROW_HEIGHT, 0x30FFFFFF);
            }
            String key;
            int keyColor;
            if (binding.unbound()) {
                key = Component.translatable(KeyboardMapTexts.UNBOUND).getString();
                keyColor = TEXT_DIM;
            } else {
                boolean overlap = catalog.state(binding) == InputState.OVERLAP;
                boolean drawn = binding.input().kind() == InputId.Kind.MOUSE || layout.contains(binding.input());
                key = (overlap ? "! " : "") + binding.inputLabel() + (drawn ? "" : " *");
                keyColor = overlap ? TEXT_OVERLAP : TEXT_SINGLE;
                if (!drawn && hoveredRow) {
                    g.setTooltipForNextFrame(font, Component.translatable(KeyboardMapTexts.NOT_ON_LAYOUT),
                            mouseX, mouseY);
                }
            }
            String keyText = font.plainSubstrByWidth(key, w / 2 - 4);
            int keyWidth = font.width(keyText);
            g.text(font, keyText, x + w - 3 - keyWidth, y + 2, keyColor);
            g.text(font, font.plainSubstrByWidth(binding.name(), w - keyWidth - 14), x + 6, y + 2, TEXT);
        }
        g.disableScissor();
    }

    private void drawStatus(GuiGraphicsExtractor g) {
        String unbound = Component.translatable(KeyboardMapTexts.UNBOUND).getString();
        Binding selected = selectedBinding();
        Component status = switch (session.state()) {
            case CAPTURING -> Component.translatable(KeyboardMapTexts.CAPTURING,
                    catalog.binding(session.target()).map(Binding::name).orElse("")).withStyle(ChatFormatting.AQUA);
            case CONFIRMING -> Component.translatable(KeyboardMapTexts.CONFIRM_OVERLAP,
                    MinecraftBindings.label(session.pending()),
                    String.join(", ", session.pendingOverlaps().stream().map(Binding::name).toList()))
                    .withStyle(ChatFormatting.GOLD);
            case IDLE -> selected == null ? Component.translatable(KeyboardMapTexts.LEGEND)
                    : Component.translatable(KeyboardMapTexts.SELECTED, selected.name(),
                            selected.unbound() ? unbound : selected.inputLabel(),
                            selected.defaultInput() == null ? unbound : MinecraftBindings.label(selected.defaultInput()));
        };
        String text = font.plainSubstrByWidth(status.getString(), width - 2 * geometry.margin());
        g.text(font, Component.literal(text).withStyle(status.getStyle()), geometry.margin(),
                geometry.contentBottom() + 4, TEXT);
    }

    /** Labels for one key only (keymap-v1.md UI-5): its actions and a short connector line. */
    private void drawContextLabels(GuiGraphicsExtractor g, InputId input, boolean pointed) {
        List<Binding> on = catalog.on(input);
        int[] anchor = input == null ? null : anchor(input);
        if (on.isEmpty() || anchor == null) {
            return;
        }
        var lines = new ArrayList<String>();
        on.stream().limit(MAX_LABEL_LINES).forEach(b -> lines.add(b.name()));
        if (on.size() > MAX_LABEL_LINES) {
            lines.add(Component.translatable(KeyboardMapTexts.MORE_ACTIONS, on.size() - MAX_LABEL_LINES).getString());
        }
        if (on.size() > 1) {
            lines.add(Component.translatable(KeyboardMapTexts.OVERLAP_EXPLANATION, on.size()).getString());
        }
        int maxWidth = Math.min(240, geometry.keyboardWidth() - 8);
        var wrapped = new ArrayList<net.minecraft.util.FormattedCharSequence>();
        int noteStart = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (on.size() > 1 && i == lines.size() - 1) {
                noteStart = wrapped.size();
            }
            wrapped.addAll(font.split(Component.literal(lines.get(i)), maxWidth - 8));
        }
        int boxWidth = Math.min(maxWidth, wrapped.stream().mapToInt(font::width).max().orElse(0) + 8);
        int boxHeight = wrapped.size() * 10 + 4;
        int boxX = Math.clamp(anchor[0] - boxWidth / 2, geometry.keyboardX(),
                geometry.keyboardX() + geometry.keyboardWidth() - boxWidth);
        // A selected key's labels go in the free space under the keyboard when there is room, so they cover no
        // keys; a pointed key's labels sit right next to it.
        int freeTop = originY + KeyboardLayout.height(drawnKeys) * geometry.unit() + KeyboardMapGeometry.MOUSE_ROW + 4;
        boolean below = !pointed && freeTop + boxHeight <= geometry.contentBottom();
        boolean above = !below && anchor[1] - boxHeight - 8 >= geometry.contentTop();
        int boxY = below ? freeTop : above ? anchor[1] - boxHeight - 8 : anchor[3] + 8;
        g.nextStratum();
        g.verticalLine(anchor[0], above ? boxY + boxHeight : anchor[3], above ? anchor[1] : boxY, OUTLINE);
        g.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xE0000000);
        g.outline(boxX, boxY, boxWidth, boxHeight, on.size() > 1 ? KEY_OVERLAP | 0xFF000000 : OUTLINE);
        for (int i = 0; i < wrapped.size(); i++) {
            boolean note = noteStart >= 0 && i >= noteStart;
            g.text(font, wrapped.get(i), boxX + 4, boxY + 3 + i * 10, note ? TEXT_DIM : TEXT);
        }
    }

    /** Center x, top y, right x and bottom y of a drawn key or chip, or {@code null} if it is not drawn. */
    private int[] anchor(InputId input) {
        int unit = geometry.unit();
        if (input.kind() == InputId.Kind.KEYBOARD) {
            for (KeyCap key : drawnKeys) {
                if (key.scancode() == input.code()) {
                    int x = keyX(key);
                    int y = keyY(key);
                    return new int[] {x + key.width() * unit / 2, y, x + key.width() * unit, y + key.height() * unit};
                }
            }
            return null;
        }
        for (Chip chip : chips) {
            if (chip.input().equals(input)) {
                return new int[] {chip.x() + chip.width() / 2, chip.y(), chip.x() + chip.width(),
                        chip.y() + chip.height()};
            }
        }
        return null;
    }

    // ---- Read access for development game tests ----

    public BindingCatalog catalog() {
        return catalog;
    }

    public RemapSession session() {
        return session;
    }

    public Selection selection() {
        return selection;
    }

    public KeyboardMapGeometry geometry() {
        return geometry;
    }

    public List<String> visibleRowIds() {
        return rows.stream().map(Row::binding).filter(Objects::nonNull).map(Binding::id).toList();
    }

    public Set<KeyCap.Section> drawnSections() {
        return geometry.visibleSections(showOtherSections);
    }

    /** Screen position of a drawn key's center, or {@code null}. */
    public int[] keyCenter(int scancode) {
        int[] a = anchor(InputId.key(scancode));
        return a == null ? null : new int[] {a[0], (a[1] + a[3]) / 2};
    }
}
