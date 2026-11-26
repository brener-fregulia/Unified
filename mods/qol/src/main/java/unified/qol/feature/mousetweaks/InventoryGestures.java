package unified.qol.feature.mousetweaks;

import com.mojang.blaze3d.platform.InputConstants;
import unified.qol.mixin.ContainerScreenSlotsAccessor;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The gestures of one container screen (mouse-tweaks-v1.md §4, §6). Every item movement is a vanilla click through
 * {@code MultiPlayerGameMode.handleContainerInput}, which predicts the result locally and sends the click to the
 * server. After each click the predicted state is compared with the expected one; at the first difference the
 * gesture stops and sends nothing more. Inputs that drop items (THROW, clicks outside the window) are never used.
 */
final class InventoryGestures {
    /** Container click buttons (vanilla getContainerClickButton): 0 left, 1 right. */
    private static final int LEFT = 0;
    private static final int RIGHT = 1;
    /** Mouse event buttons in 26.3 are SDL buttons: 1 left, 3 right. */
    private static final int MOUSE_LEFT = InputConstants.MOUSE_BUTTON_LEFT;
    private static final int MOUSE_RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;

    private enum Mode {
        NONE,
        /** Right-drag distribution (§4.1); owns press, drag and release. */
        RIGHT_DRAG,
        /** Left press on a matching stack; becomes COLLECT on the first move, or a plain click on release. */
        COLLECT_PENDING,
        /** Left-drag collection into the cursor (§4.2). */
        COLLECT,
        /** Shift + left drag with an item: quick-moves matching stacks (§4.2). */
        SHIFT_COLLECT,
        /** Shift + left drag with an empty cursor (§4.3); vanilla keeps the press. */
        SHIFT_DRAG,
        /** A check failed: the rest of this gesture is swallowed and sends nothing. */
        STOPPED
    }

    private final AbstractContainerScreen<?> screen;
    private final ClickBudget budget;
    private final WheelAccumulator wheel = new WheelAccumulator();
    private final Set<Slot> moved = Collections.newSetFromMap(new IdentityHashMap<>());
    private Mode mode = Mode.NONE;
    /** True when vanilla handled the press (Shift + drag with an empty cursor); it then keeps drag and release. */
    private boolean vanillaPress;
    private int button = -1;
    private int containerId = -1;
    private Slot last;
    private Slot pressed;

    InventoryGestures(AbstractContainerScreen<?> screen, ClickBudget budget) {
        this.screen = screen;
        this.budget = budget;
    }

    // ---- Mouse events (Fabric ScreenMouseEvents); each returns whether vanilla may handle the event ----

    boolean press(MouseButtonEvent event) {
        reset(); // a press always starts a new gesture; a missed release (focus loss) cannot leak into it
        MouseTweaksConfig config = MouseTweaksFeature.config();
        if (config == null || !ready()) {
            return true;
        }
        Slot slot = slotAt(event.x(), event.y());
        ItemStack carried = menu().getCarried();
        boolean shift = event.hasShiftDown();
        if (event.button() == MOUSE_RIGHT && config.rmbDrag() && !carried.isEmpty() && accepts(slot, carried)) {
            begin(Mode.RIGHT_DRAG, event.button(), slot);
            placeOne(slot);
            return false; // vanilla's right-drag spread never starts
        }
        if (event.button() != MOUSE_LEFT || slot == null) {
            return true;
        }
        if (!carried.isEmpty() && config.lmbCollect() && canTake(slot)
                && ItemStack.isSameItemSameComponents(slot.getItem(), carried)) {
            if (shift) {
                begin(Mode.SHIFT_COLLECT, event.button(), slot);
                quickMove(slot, carried);
            } else {
                begin(Mode.COLLECT_PENDING, event.button(), slot);
            }
            return false;
        }
        if (carried.isEmpty() && shift && config.shiftDrag()) {
            begin(Mode.SHIFT_DRAG, event.button(), slot);
            vanillaPress = true;
            moved.add(slot); // vanilla quick-moves the pressed slot itself
        }
        return true; // otherwise vanilla, including its even split (Q-MT3)
    }

    boolean drag(MouseButtonEvent event) {
        if (mode == Mode.NONE || event.button() != button) {
            return true;
        }
        if (mode == Mode.STOPPED || !ready()) {
            stop();
            return vanillaPress;
        }
        Slot slot = slotAt(event.x(), event.y());
        if (slot == last) {
            return vanillaPress;
        }
        if (mode == Mode.COLLECT_PENDING) {
            mode = Mode.COLLECT;
            collect(pressed); // the press slot is the first visit
        }
        last = slot; // leaving to empty space and coming back counts as a new visit (Q-MT2)
        if (slot != null && mode != Mode.STOPPED) {
            ItemStack carried = menu().getCarried();
            switch (mode) {
                case RIGHT_DRAG -> {
                    if (accepts(slot, carried)) {
                        placeOne(slot);
                    }
                }
                case COLLECT -> collect(slot);
                case SHIFT_COLLECT -> {
                    if (!moved.contains(slot) && canTake(slot)
                            && ItemStack.isSameItemSameComponents(slot.getItem(), carried)) {
                        quickMove(slot, carried);
                    }
                }
                case SHIFT_DRAG -> {
                    if (!moved.contains(slot) && canTake(slot) && carried.isEmpty()) {
                        quickMove(slot, carried);
                    }
                }
                default -> {
                }
            }
        }
        return vanillaPress;
    }

    boolean release(MouseButtonEvent event) {
        if (mode == Mode.NONE || event.button() != button) {
            return true;
        }
        boolean vanillaSawPress = vanillaPress;
        if (mode == Mode.COLLECT_PENDING && ready()) {
            // A press and release on the same matching stack: the plain vanilla left click (merge into the slot).
            click(pressed, LEFT, ContainerInput.PICKUP);
        }
        reset();
        return vanillaSawPress; // vanilla gets the release only of a press it handled
    }

    /** Wheel transfers (§4.4); returns whether vanilla may handle the event. */
    boolean scroll(double x, double y, double vertical) {
        MouseTweaksConfig config = MouseTweaksFeature.config();
        if (config == null || !config.wheelTransfer() || mode != Mode.NONE || !ready()) {
            return true;
        }
        Slot slot = slotAt(x, y);
        if (slot == null || !slot.hasItem() || !menu().getCarried().isEmpty() || !canTake(slot)) {
            return true;
        }
        var accessor = (ContainerScreenSlotsAccessor) screen;
        if (accessor.qolUnified$itemSlotMouseActions().stream().anyMatch(a -> a.matches(slot))) {
            return true; // bundles keep vanilla's own wheel behavior
        }
        Section section = section(slot);
        if (section == Section.NONE) {
            return true;
        }
        int steps = wheel.add(config.pushAmount(vertical));
        for (int i = 0; i < Math.abs(steps); i++) {
            boolean done = steps > 0 ? push(slot, section, config) : pull(slot, section, config);
            if (!done) {
                break; // no target, no budget, or a divergence: the rest of this event is discarded
            }
        }
        return false;
    }

    /** Screen removed, re-initialized, or the window lost focus. */
    void cancel() {
        reset();
        wheel.reset();
    }

    // ---- Gestures ----

    private void placeOne(Slot slot) {
        ItemStack carried = menu().getCarried();
        int before = carried.getCount();
        int slotBefore = slot.getItem().getCount();
        if (click(slot, RIGHT, ContainerInput.PICKUP)
                && !(menu().getCarried().getCount() == before - 1 && slot.getItem().getCount() == slotBefore + 1)) {
            stop();
        }
    }

    /**
     * Two left clicks: the cursor merges into the slot, then the merged stack is picked up. Only when everything
     * fits in one stack, so the second click always empties the slot; otherwise the slot is skipped.
     */
    private void collect(Slot slot) {
        if (slot == null || mode != Mode.COLLECT) {
            return;
        }
        ItemStack carried = menu().getCarried();
        if (carried.isEmpty() || !canTake(slot) || !ItemStack.isSameItemSameComponents(slot.getItem(), carried)) {
            return;
        }
        int sum = carried.getCount() + slot.getItem().getCount();
        if (sum > carried.getMaxStackSize() || sum > slot.getMaxStackSize(carried) || !slot.mayPlace(carried)
                || !reserve(2)) {
            return;
        }
        if (!send(slot, LEFT, ContainerInput.PICKUP) || !menu().getCarried().isEmpty()
                || slot.getItem().getCount() != sum) {
            stop();
            return;
        }
        if (!send(slot, LEFT, ContainerInput.PICKUP) || menu().getCarried().getCount() != sum || slot.hasItem()) {
            stop();
        }
    }

    private void quickMove(Slot slot, ItemStack carriedBefore) {
        moved.add(slot);
        int carriedCount = carriedBefore.getCount();
        if (click(slot, LEFT, ContainerInput.QUICK_MOVE) && menu().getCarried().getCount() != carriedCount) {
            stop(); // a quick move never changes the cursor
        }
    }

    /** One item from {@code source} to the first accepting slot of the other section. */
    private boolean push(Slot source, Section section, MouseTweaksConfig config) {
        if (!source.hasItem() || !canTake(source)) {
            return false;
        }
        ItemStack item = source.getItem();
        Slot target = findTarget(item, source, section, config);
        return target != null && moveOne(source, target);
    }

    /** One matching item from the other section into {@code target}. */
    private boolean pull(Slot target, Section section, MouseTweaksConfig config) {
        ItemStack item = target.getItem();
        if (!accepts(target, item)) {
            return false;
        }
        Slot source = findSource(item, target, section, config);
        return source != null && moveOne(source, target);
    }

    /**
     * Pick up the source, place one on the target, put the rest back. The predicted state is checked after every
     * click and the sequence stops at the first difference.
     */
    private boolean moveOne(Slot source, Slot target) {
        int count = source.getItem().getCount();
        int targetBefore = target.getItem().getCount();
        if (!reserve(count == 1 ? 2 : 3)) {
            return false; // never start a sequence that cannot finish within the tick's budget
        }
        if (!send(source, LEFT, ContainerInput.PICKUP) || menu().getCarried().getCount() != count
                || source.hasItem()) {
            return false;
        }
        if (!send(target, RIGHT, ContainerInput.PICKUP) || menu().getCarried().getCount() != count - 1
                || target.getItem().getCount() != targetBefore + 1) {
            return false;
        }
        if (count > 1 && (!send(source, LEFT, ContainerInput.PICKUP) || !menu().getCarried().isEmpty()
                || source.getItem().getCount() != count - 1)) {
            return false;
        }
        return true;
    }

    private Slot findTarget(ItemStack item, Slot source, Section from, MouseTweaksConfig config) {
        Slot empty = null;
        var slots = menu().slots;
        for (int n = 0; n < slots.size(); n++) {
            Slot s = slots.get(config.lastToFirst() ? slots.size() - 1 - n : n);
            if (s == source || !from.other(section(s)) || !accepts(s, item)) {
                continue;
            }
            if (s.hasItem()) {
                return s; // a stack of the same item with room comes first
            }
            if (empty == null) {
                empty = s;
            }
        }
        return empty;
    }

    private Slot findSource(ItemStack item, Slot target, Section to, MouseTweaksConfig config) {
        var slots = menu().slots;
        for (int n = 0; n < slots.size(); n++) {
            Slot s = slots.get(config.lastToFirst() ? slots.size() - 1 - n : n);
            if (s != target && to.other(section(s)) && s.hasItem() && canTake(s)
                    && ItemStack.isSameItemSameComponents(s.getItem(), item)) {
                return s;
            }
        }
        return null;
    }

    // ---- Sections (Q-MT5) ----

    private enum Section {
        NONE, HOTBAR, MAIN, PLAYER, CONTAINER;

        boolean other(Section s) {
            return switch (this) {
                case HOTBAR -> s == MAIN;
                case MAIN -> s == HOTBAR;
                case PLAYER -> s == CONTAINER;
                case CONTAINER -> s == PLAYER;
                case NONE -> false;
            };
        }
    }

    /**
     * Player inventory screen: hotbar ↔ main inventory. Other menus: the container ↔ the player's 36 storage slots.
     * Armor, offhand and crafting slots belong to no section.
     */
    private Section section(Slot slot) {
        boolean playerSlot = slot.container instanceof Inventory;
        int index = slot.getContainerSlot();
        if (menu() instanceof InventoryMenu) {
            if (!playerSlot) {
                return Section.NONE;
            }
            // The nine hotbar slots come first in the player's Inventory, then the 27 main slots.
            return index < 9 ? Section.HOTBAR : index < 36 ? Section.MAIN : Section.NONE;
        }
        if (playerSlot) {
            return index < 36 ? Section.PLAYER : Section.NONE;
        }
        return Section.CONTAINER;
    }

    // ---- Slot rules ----

    /** A stack that may be taken and put back: excludes result, fake and inactive slots. */
    private boolean canTake(Slot slot) {
        LocalPlayer player = Minecraft.getInstance().player;
        return slot != null && !slot.isFake() && slot.isActive() && slot.hasItem() && slot.allowModification(player);
    }

    /** Whether {@code slot} takes one more of {@code item}: restrictions, components and limits. */
    private static boolean accepts(Slot slot, ItemStack item) {
        if (slot == null || item.isEmpty() || slot.isFake() || !slot.isActive() || !slot.mayPlace(item)) {
            return false;
        }
        ItemStack in = slot.getItem();
        if (in.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(in, item) && in.getCount() < slot.getMaxStackSize(item)
                && in.getCount() < in.getMaxStackSize();
    }

    // ---- Clicks ----

    private boolean click(Slot slot, int mouseButton, ContainerInput input) {
        return reserve(1) && send(slot, mouseButton, input);
    }

    private boolean reserve(int clicks) {
        return budget.reserve(MouseTweaksHooks.tick(), clicks);
    }

    /** One vanilla click through the game's own path (local prediction plus the click packet). */
    private boolean send(Slot slot, int mouseButton, ContainerInput input) {
        if (!ready()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gameMode.handleContainerInput(menu().containerId, slot.index, mouseButton, input,
                minecraft.player);
        return true;
    }

    /** The screen is current, focused, and its menu is still the player's open menu. */
    private boolean ready() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.gameMode != null && minecraft.player != null && minecraft.gui.screen() == screen
                && minecraft.isWindowActive() && minecraft.player.containerMenu == menu()
                && (containerId == -1 || containerId == menu().containerId);
    }

    private AbstractContainerMenu menu() {
        return screen.getMenu();
    }

    private Slot slotAt(double x, double y) {
        return ((ContainerScreenSlotsAccessor) screen).qolUnified$slotAt(x, y);
    }

    private void begin(Mode newMode, int mouseButton, Slot slot) {
        mode = newMode;
        button = mouseButton;
        containerId = menu().containerId;
        last = slot;
        pressed = slot;
        moved.clear();
    }

    private void stop() {
        if (mode != Mode.NONE) {
            mode = Mode.STOPPED;
        }
    }

    private void reset() {
        mode = Mode.NONE;
        vanillaPress = false;
        button = -1;
        containerId = -1;
        last = null;
        pressed = null;
        moved.clear();
    }
}
