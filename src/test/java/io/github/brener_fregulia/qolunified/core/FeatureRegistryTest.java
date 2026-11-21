package io.github.brener_fregulia.qolunified.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FeatureRegistryTest {
    @Test
    void keepsRegistrationOrderAndLooksUpById() {
        var a = TestFeature.live("alpha");
        var b = TestFeature.live("beta");
        FeatureRegistry registry = FeatureRegistry.builder().register(b).register(a).build();
        assertEquals(List.of(b, a), registry.features());
        assertEquals(a, registry.require("alpha"));
        assertTrue(registry.find("gamma").isEmpty());
    }

    @Test
    void duplicateIdFailsNamingTheId() {
        var builder = FeatureRegistry.builder().register(TestFeature.live("alpha"));
        var error = assertThrows(IllegalArgumentException.class, () -> builder.register(TestFeature.live("alpha")));
        assertTrue(error.getMessage().contains("alpha"));
    }

    @Test
    void registryIsFrozenAfterBuild() {
        var builder = FeatureRegistry.builder().register(TestFeature.live("alpha"));
        FeatureRegistry registry = builder.build();
        assertThrows(IllegalStateException.class, () -> builder.register(TestFeature.live("beta")));
        assertThrows(IllegalStateException.class, builder::build);
        assertThrows(UnsupportedOperationException.class, () -> registry.features().clear());
        assertEquals(1, registry.features().size());
    }

    @Test
    void emptyRegistryIsValid() {
        assertTrue(FeatureRegistry.builder().build().features().isEmpty());
    }
}
