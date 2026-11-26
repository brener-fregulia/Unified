package unified.qol.core.config;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** File operations used by the configuration service. Tests override methods to inject failures. */
public class ConfigFileSystem {
    public boolean exists(Path path) {
        return Files.exists(path);
    }

    public byte[] read(Path path) throws IOException {
        return Files.readAllBytes(path);
    }

    public void createDirectories(Path dir) throws IOException {
        Files.createDirectories(dir);
    }

    public void write(Path path, byte[] content) throws IOException {
        Files.write(path, content);
    }

    /** Copies without replacing an existing target. */
    public void copy(Path source, Path target) throws IOException {
        Files.copy(source, target);
    }

    /** @throws AtomicMoveNotSupportedException if the file system cannot move atomically */
    public void moveAtomic(Path source, Path target) throws IOException {
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    public void moveReplacing(Path source, Path target) throws IOException {
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    public void deleteIfExists(Path path) throws IOException {
        Files.deleteIfExists(path);
    }
}
