package unified.qol.core.config;

import unified.qol.core.ApplyMode;
import unified.qol.core.Feature;
import unified.qol.core.FeatureCategory;
import unified.qol.core.FeatureContext;
import unified.qol.core.FeatureDescriptor;
import unified.qol.core.FeatureRegistry;
import unified.qol.core.SettingDescriptor;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.util.List;

/** Test-only descriptors and a file system with injectable failures. */
final class ConfigFixtures {
    private ConfigFixtures() {
    }

    static FeatureDescriptor descriptor(String id, boolean defaultEnabled) {
        return new FeatureDescriptor(id, FeatureCategory.HUD_INFORMATION, defaultEnabled, ApplyMode.LIVE, List.of(
                new SettingDescriptor.BooleanSetting("live_flag", false, ApplyMode.LIVE),
                new SettingDescriptor.IntegerSetting("restart_size", 5, 1, 10, ApplyMode.RESTART_REQUIRED),
                new SettingDescriptor.EnumSetting("mode", "a", List.of("a", "b"), ApplyMode.LIVE)), List.of());
    }

    static FeatureRegistry registry() {
        return FeatureRegistry.builder()
                .register(feature(descriptor("alpha", true)))
                .register(feature(descriptor("beta", false)))
                .build();
    }

    static Feature feature(FeatureDescriptor descriptor) {
        return new Feature() {
            @Override
            public FeatureDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public void enable(FeatureContext context) {
            }

            @Override
            public void disable() {
            }
        };
    }

    static final class FaultyFileSystem extends ConfigFileSystem {
        boolean failRead;
        boolean failCopy;
        boolean failWrite;
        boolean failMove;
        boolean atomicUnsupported;
        int nonAtomicMoves;

        @Override
        public byte[] read(Path path) throws IOException {
            if (failRead) {
                throw new IOException("read failure");
            }
            return super.read(path);
        }

        @Override
        public void copy(Path source, Path target) throws IOException {
            if (failCopy) {
                throw new IOException("copy failure");
            }
            super.copy(source, target);
        }

        @Override
        public void write(Path path, byte[] content) throws IOException {
            if (failWrite) {
                super.write(path, new byte[] {'{'});
                throw new IOException("write failure");
            }
            super.write(path, content);
        }

        @Override
        public void moveAtomic(Path source, Path target) throws IOException {
            if (failMove) {
                throw new IOException("move failure");
            }
            if (atomicUnsupported) {
                throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "unsupported");
            }
            super.moveAtomic(source, target);
        }

        @Override
        public void moveReplacing(Path source, Path target) throws IOException {
            nonAtomicMoves++;
            super.moveReplacing(source, target);
        }
    }
}
