package unified.qol.core.compat;

/** Answers whether a mod is installed. The Fabric Loader implementation lives at the integration boundary. */
@FunctionalInterface
public interface ModPresence {
    boolean isLoaded(String modId);
}
