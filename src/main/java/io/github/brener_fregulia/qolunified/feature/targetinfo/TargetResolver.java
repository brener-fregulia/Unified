package io.github.brener_fregulia.qolunified.feature.targetinfo;

import io.github.brener_fregulia.qolunified.core.SettingsSnapshot;
import io.github.brener_fregulia.qolunified.feature.targetinfo.ToolAdvice.Category;
import io.github.brener_fregulia.qolunified.feature.targetinfo.ToolAdvice.Probe;
import io.github.brener_fregulia.qolunified.feature.targetinfo.ToolAdvice.Tier;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Builds a {@link TargetView} from the crosshair target vanilla already computed ({@code Minecraft.hitResult}); it
 * never casts a ray of its own. Stable per-block-state answers (tool probes) are kept in a small bounded cache.
 */
final class TargetResolver {
    private static final int CACHE_SIZE = 256;

    /** Vanilla tools per category, by tier; probed with the game's own checks, never matched by name. */
    private static final Map<Category, Map<Tier, ItemStack>> TOOLS = new EnumMap<>(Category.class);

    static {
        tools(Category.PICKAXE, Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.COPPER_PICKAXE, Items.IRON_PICKAXE,
                Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE);
        tools(Category.AXE, Items.WOODEN_AXE, Items.STONE_AXE, Items.COPPER_AXE, Items.IRON_AXE, Items.DIAMOND_AXE,
                Items.NETHERITE_AXE);
        tools(Category.SHOVEL, Items.WOODEN_SHOVEL, Items.STONE_SHOVEL, Items.COPPER_SHOVEL, Items.IRON_SHOVEL,
                Items.DIAMOND_SHOVEL, Items.NETHERITE_SHOVEL);
        tools(Category.HOE, Items.WOODEN_HOE, Items.STONE_HOE, Items.COPPER_HOE, Items.IRON_HOE, Items.DIAMOND_HOE,
                Items.NETHERITE_HOE);
        tools(Category.SWORD, Items.WOODEN_SWORD, Items.STONE_SWORD, Items.COPPER_SWORD, Items.IRON_SWORD,
                Items.DIAMOND_SWORD, Items.NETHERITE_SWORD);
        Map<Tier, ItemStack> shears = new EnumMap<>(Tier.class);
        shears.put(Tier.WOODEN, new ItemStack(Items.SHEARS));
        TOOLS.put(Category.SHEARS, shears);
    }

    private static void tools(Category category, Item... byTier) {
        Map<Tier, ItemStack> map = new EnumMap<>(Tier.class);
        for (Tier tier : Tier.values()) {
            map.put(tier, new ItemStack(byTier[tier.ordinal()]));
        }
        TOOLS.put(category, map);
    }

    private final Map<BlockState, List<Probe>> probes = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<BlockState, List<Probe>> eldest) {
            return size() > CACHE_SIZE;
        }
    };
    private final Map<String, Component> modNames = new HashMap<>();
    private Level cachedLevel;

    static ItemStack toolIcon(ToolAdvice.Recommendation recommendation) {
        Map<Tier, ItemStack> tiers = TOOLS.get(recommendation.category());
        return tiers.get(recommendation.tier() == null ? Tier.WOODEN : recommendation.tier());
    }

    /** The view for the current target, or {@code null} when there is nothing to show. */
    TargetView resolve(Minecraft mc, SettingsSnapshot settings) {
        Level level = mc.level;
        HitResult hit = mc.hitResult;
        if (level == null || mc.player == null || hit == null) {
            return null;
        }
        if (level != cachedLevel) {
            // A new world or server may come with different data packs (tags, tool components).
            probes.clear();
            cachedLevel = level;
        }
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
            if (!settings.getBoolean(TargetInfoSettings.SHOW_BLOCKS)) {
                return null;
            }
            BlockPos pos = blockHit.getBlockPos();
            BlockState state = level.getBlockState(pos);
            return state.isAir() ? null : block(mc, level, pos.immutable(), state, settings);
        }
        if (hit.getType() == HitResult.Type.ENTITY && hit instanceof EntityHitResult entityHit) {
            if (!settings.getBoolean(TargetInfoSettings.SHOW_ENTITIES) || entityHit.getEntity() == null) {
                return null;
            }
            return entity(mc, entityHit.getEntity(), settings);
        }
        return null;
    }

    // ---- Blocks ----

    private TargetView block(Minecraft mc, Level level, BlockPos pos, BlockState state, SettingsSnapshot settings) {
        Block block = state.getBlock();
        ItemStack icon = state.getCloneItemStack(level, pos, false);
        if (icon.isEmpty()) {
            icon = new ItemStack(block.asItem());
        }
        float hardness = state.getDestroySpeed(level, pos);
        boolean instant = hardness <= 0.0f; // 0: instant; -1: unbreakable, where no tool helps either
        Component source = settings.getBoolean(TargetInfoSettings.SHOW_SOURCE)
                ? modName(BuiltInRegistries.BLOCK.getKey(block).getNamespace()) : null;

        List<ItemStack> tools = List.of();
        ToolAdvice.Mark mark = ToolAdvice.Mark.NONE;
        List<Component> lines = new ArrayList<>();
        if (settings.getBoolean(TargetInfoSettings.SHOW_TOOLS)) {
            ItemStack held = mc.player.getMainHandItem();
            ToolAdvice.Advice advice = ToolAdvice.evaluate(probes(state), instant,
                    state.requiresCorrectToolForDrops(), held.isCorrectToolForDrops(state),
                    held.getDestroySpeed(state) > 1.0f);
            tools = advice.tools().stream().map(TargetResolver::toolIcon).toList();
            mark = advice.mark();
            if (advice.requirement() != null) {
                lines.add(Component.translatable(TargetInfoTexts.REQUIRES,
                        toolIcon(advice.requirement()).getHoverName()));
            }
        }
        if (settings.getBoolean(TargetInfoSettings.SHOW_BLOCK_DETAILS)) {
            for (DetailLine line : details(state)) {
                lines.add(text(line));
            }
        }
        return new TargetView(pos, icon, block.getName(), source, tools, mark, List.copyOf(lines), null, instant);
    }

    private List<Probe> probes(BlockState state) {
        List<Probe> cached = probes.get(state);
        if (cached != null) {
            return cached;
        }
        List<Probe> result = new ArrayList<>(Category.values().length);
        for (Category category : Category.values()) {
            Map<Tier, ItemStack> tiers = TOOLS.get(category);
            ItemStack best = category == Category.SHEARS ? tiers.get(Tier.WOODEN) : tiers.get(Tier.NETHERITE);
            Tier minimum = null;
            for (Map.Entry<Tier, ItemStack> entry : tiers.entrySet()) {
                if (entry.getValue().isCorrectToolForDrops(state)) {
                    minimum = entry.getKey();
                    break;
                }
            }
            result.add(new Probe(category, best.getDestroySpeed(state) > 1.0f, minimum));
        }
        List<Probe> list = List.copyOf(result);
        probes.put(state, list);
        return list;
    }

    static List<DetailLine> details(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof BeehiveBlock) {
            return BlockDetails.honey(state.getValue(BeehiveBlock.HONEY_LEVEL), BeehiveBlock.MAX_HONEY_LEVELS);
        }
        if (block instanceof RedstoneWireBlock) {
            return BlockDetails.wire(state.getValue(RedstoneWireBlock.POWER));
        }
        if (block instanceof LeverBlock) {
            return BlockDetails.lever(state.getValue(LeverBlock.POWERED));
        }
        if (block instanceof ButtonBlock) {
            return BlockDetails.button(state.getValue(ButtonBlock.POWERED));
        }
        if (block instanceof RedstoneLampBlock) {
            return BlockDetails.lamp(state.getValue(RedstoneLampBlock.LIT));
        }
        if (block instanceof RepeaterBlock) {
            return BlockDetails.repeater(state.getValue(RepeaterBlock.DELAY), state.getValue(DiodeBlock.POWERED),
                    state.getValue(RepeaterBlock.LOCKED));
        }
        if (block instanceof ComparatorBlock) {
            return BlockDetails.comparator(state.getValue(ComparatorBlock.MODE) == ComparatorMode.SUBTRACT,
                    state.getValue(DiodeBlock.POWERED));
        }
        if (block instanceof CropBlock crop) {
            return BlockDetails.growth(crop.getAge(state), crop.getMaxAge());
        }
        if (block instanceof NetherWartBlock) {
            return age(state, NetherWartBlock.AGE);
        }
        if (block instanceof CocoaBlock) {
            return age(state, CocoaBlock.AGE);
        }
        if (block instanceof SweetBerryBushBlock) {
            return age(state, SweetBerryBushBlock.AGE);
        }
        return List.of();
    }

    private static List<DetailLine> age(BlockState state, IntegerProperty property) {
        int max = property.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
        return BlockDetails.growth(state.getValue(property), max);
    }

    // ---- Entities ----

    private TargetView entity(Minecraft mc, Entity entity, SettingsSnapshot settings) {
        if (entity instanceof Player player) {
            // Privacy (Q-TI7): a player's name only.
            return new TargetView(null, ItemStack.EMPTY, player.getName(), null, List.of(), ToolAdvice.Mark.NONE,
                    List.of(), null, false);
        }
        Component source = settings.getBoolean(TargetInfoSettings.SHOW_SOURCE)
                ? modName(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace()) : null;
        List<Component> lines = new ArrayList<>();
        ItemStack icon = ItemStack.EMPTY;
        Component name = entity.getName();
        EntityDetails.Health health = null;
        if (entity instanceof ItemEntity item) {
            ItemStack stack = item.getItem();
            icon = stack;
            name = stack.getHoverName();
            if (stack.getCount() > 1) {
                lines.add(Component.translatable(TargetInfoTexts.COUNT, stack.getCount()));
            }
        }
        if (entity instanceof LivingEntity living && settings.getBoolean(TargetInfoSettings.SHOW_HEALTH)) {
            health = EntityDetails.health(living.getHealth(), living.getMaxHealth());
        }
        if (entity instanceof OwnableEntity ownable && settings.getBoolean(TargetInfoSettings.SHOW_OWNER)) {
            EntityReference<LivingEntity> reference = ownable.getOwnerReference();
            Optional<UUID> owner = Optional.ofNullable(reference).map(EntityReference::getUUID);
            Level level = mc.level;
            ClientPacketListener connection = mc.getConnection();
            EntityDetails.Owner resolved = EntityDetails.owner(owner,
                    id -> {
                        LivingEntity loaded = reference.getEntity(level, LivingEntity.class);
                        return loaded == null ? null : loaded.getName().getString();
                    },
                    id -> {
                        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(id);
                        return info == null ? null : info.getProfile().name();
                    });
            switch (resolved) {
                case EntityDetails.Owner.Named named ->
                        lines.add(Component.translatable(TargetInfoTexts.OWNER, named.name()));
                case EntityDetails.Owner.Unknown unknown ->
                        lines.add(Component.translatable(TargetInfoTexts.OWNER_UNKNOWN));
                case EntityDetails.Owner.None none -> {
                }
            }
        }
        return new TargetView(null, icon, name, source, List.of(), ToolAdvice.Mark.NONE, List.copyOf(lines), health,
                false);
    }

    // ---- Shared ----

    private Component modName(String namespace) {
        return modNames.computeIfAbsent(namespace, ns -> Component.literal(FabricLoader.getInstance()
                .getModContainer(ns).map(c -> c.getMetadata().getName()).filter(n -> !n.isBlank()).orElse(ns)));
    }

    static Component text(DetailLine line) {
        Object[] args = line.args().stream()
                .map(a -> a instanceof DetailLine nested ? text(nested) : a).toArray();
        return Component.translatable(line.key(), args);
    }
}
