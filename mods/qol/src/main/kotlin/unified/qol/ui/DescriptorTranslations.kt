package unified.qol.ui

import unified.qol.core.FeatureDescriptor
import unified.qol.core.SettingDescriptor

/**
 * The translation keys a feature's entry in the settings needs: name, description, each setting, each option of an
 * enum setting and each conflict reason. Written in Kotlin (monorepo language policy) over the Java descriptor API,
 * and callable from Java through the static methods.
 */
object DescriptorTranslations {
    @JvmStatic
    fun requiredKeys(descriptor: FeatureDescriptor): List<String> = buildList {
        val id = descriptor.id()
        add(descriptor.nameKey())
        add(descriptor.descriptionKey())
        for (setting in descriptor.settings()) {
            add(setting.nameKey(id))
            if (setting is SettingDescriptor.EnumSetting) {
                setting.values().forEach { add(UiKeys.enumValue(id, setting.key(), it)) }
            }
        }
        descriptor.conflicts().forEach { add(it.reasonKey()) }
    }

    /** The required keys that [available] lacks, in declaration order. */
    @JvmStatic
    fun missing(descriptor: FeatureDescriptor, available: Set<String>): List<String> =
        requiredKeys(descriptor).filterNot(available::contains)
}
