package unified.qol.feature.foodhud;

import unified.qol.core.SettingsSnapshot;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

/** F3 line with food and saturation. Exhaustion is never shown: the client does not receive it (R-8). */
final class FoodDebugEntry implements DebugScreenEntry {
    @Override
    public void display(DebugScreenDisplayer displayer, Level level, LevelChunk clientChunk, LevelChunk serverChunk) {
        SettingsSnapshot settings = FoodHudFeature.activeSettings();
        LocalPlayer player = Minecraft.getInstance().player;
        if (settings == null || !settings.getBoolean(FoodHudSettings.DEBUG_INFO) || player == null) {
            return;
        }
        var food = player.getFoodData();
        displayer.addLine(I18n.get(FoodHudSettings.DEBUG_LINE_KEY, food.getFoodLevel(),
                String.format(Locale.ROOT, "%.1f", food.getSaturationLevel())));
    }
}
