package unified.optimizations.core

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * `config/unified_optimizations/config.json`: one `enabled` switch per feature, chosen explicitly by the player or
 * absent (then the feature default applies).
 *
 * ```json
 * {"schemaVersion": 1, "features": {"fast_ip_ping": {"enabled": true}}}
 * ```
 *
 * - Nothing is written until the player changes a switch.
 * - Unknown keys and features are kept as they are, so a newer or hand-edited file is not damaged.
 * - Values are validated before they are used or written; an invalid entry counts as "not chosen".
 * - A file that cannot be parsed is never overwritten silently: it is moved to `config.json.invalid` before the next
 *   save, and the defaults apply meanwhile.
 * - Writes go to a temporary file in the same directory, then replace the file atomically.
 */
class OptimizationsConfig(private val file: Path) {
    /** The status of the last [load]. */
    enum class LoadStatus { MISSING, LOADED, INVALID, NEWER }

    private var root = JsonObject()
    var status: LoadStatus = LoadStatus.MISSING
        private set

    /** Reads the file; never throws. */
    fun load(): LoadStatus {
        root = JsonObject()
        status = try {
            if (!Files.isRegularFile(file)) {
                LoadStatus.MISSING
            } else {
                val parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                if (!parsed.isJsonObject) {
                    LoadStatus.INVALID
                } else {
                    root = parsed.asJsonObject
                    val version = root.get("schemaVersion")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
                        ?.asInt ?: SCHEMA_VERSION
                    if (version > SCHEMA_VERSION) LoadStatus.NEWER else LoadStatus.LOADED
                }
            }
        } catch (e: Exception) {
            LoadStatus.INVALID
        }
        return status
    }

    /** The player's explicit choice for [featureId], or null when none (or an invalid one) is stored. */
    fun choice(featureId: String): Boolean? {
        val entry = features()?.get(featureId)?.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return null
        val enabled = entry.get("enabled") ?: return null
        return if (enabled.isJsonPrimitive && enabled.asJsonPrimitive.isBoolean) enabled.asBoolean else null
    }

    /**
     * Stores [enabled] for [featureId] and writes the file. A file from a newer schema is never rewritten.
     *
     * @throws IOException when the file cannot be written; the previous file is then left untouched
     */
    fun save(featureId: String, enabled: Boolean) {
        if (status == LoadStatus.NEWER) {
            throw IOException("config.json was written by a newer version; not overwritten")
        }
        if (status == LoadStatus.INVALID && Files.exists(file)) {
            Files.move(file, file.resolveSibling("config.json.invalid"), StandardCopyOption.REPLACE_EXISTING)
            root = JsonObject()
        }
        val next = root.deepCopy()
        next.addProperty("schemaVersion", SCHEMA_VERSION)
        val features = next.get("features")?.takeIf(JsonElement::isJsonObject)?.asJsonObject
            ?: JsonObject().also { next.add("features", it) }
        val entry = features.get(featureId)?.takeIf(JsonElement::isJsonObject)?.asJsonObject
            ?: JsonObject().also { features.add(featureId, it) }
        entry.addProperty("enabled", enabled)
        write(next)
        root = next
        status = LoadStatus.LOADED
    }

    private fun features(): JsonObject? = root.get("features")?.takeIf(JsonElement::isJsonObject)?.asJsonObject

    private fun write(json: JsonObject) {
        Files.createDirectories(file.parent)
        val temp = Files.createTempFile(file.parent, "config", ".json.tmp")
        try {
            Files.writeString(temp, GSON.toJson(json) + "\n", StandardCharsets.UTF_8)
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    companion object {
        const val SCHEMA_VERSION: Int = 1
        private val GSON = GsonBuilder().setPrettyPrinting().create()
    }
}
