package unified.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import org.junit.jupiter.api.Test;
import unified.settings.api.ModuleOrder;
import unified.settings.api.UnifiedSettingsProvider;
import unified.settings.registry.ModuleEntry;
import unified.settings.registry.ProviderRegistry;

/** The contract is implementable from Java with only the required members (QoL's provider is Java). */
class JavaProviderTest {
    static final class JavaProvider implements UnifiedSettingsProvider {
        @Override
        public String getModuleId() {
            return "java_mod";
        }

        @Override
        public String getNameKey() {
            return "name.java";
        }

        @Override
        public int getOrder() {
            return ModuleOrder.QOL;
        }

        @Override
        public Screen openSettings(Screen parent) {
            return null;
        }
    }

    @Test
    void javaProvidersUseTheDefaults() {
        UnifiedSettingsProvider p = new JavaProvider();
        assertTrue(p.categories().isEmpty(), "default method callable from Java");
        assertTrue(p.capabilities().isEmpty());
        List<ModuleEntry> modules = ProviderRegistry.resolve(List.of(
                new ProviderRegistry.Candidate("java_mod", JavaProvider::new)));
        assertEquals("java_mod", modules.getFirst().getModId());
    }
}
