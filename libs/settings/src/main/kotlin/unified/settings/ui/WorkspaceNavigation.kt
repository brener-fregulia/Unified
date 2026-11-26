package unified.settings.ui

import java.text.Normalizer
import java.util.Locale

/**
 * The shared workspace's sidebar (docs/architecture/unified-settings-integration.md §20.3; generalized from QoL's
 * settings-ux-v1.md §4.1, §9): installed modules, their categories and features, the selected feature and the search.
 * With one module its row is omitted and its categories are shown directly, exactly as the QoL workspace did. Pure: it
 * works on translated strings handed in by the screen, never touches a module's configuration, and keeps its state for
 * the whole settings session.
 */
class WorkspaceNavigation(modules: List<Module>, preselectModule: String?, preselectFeature: String?) {

    /** One installed module. [features] is empty when it has nothing to configure; [failed] when it could not load. */
    data class Module(val id: String, val nameKey: String, val features: List<Feature>, val failed: Boolean = false)

    /** One feature as the sidebar sees it, with translated texts for search. */
    data class Feature(
        val categoryId: String,
        val categoryNameKey: String,
        val id: String,
        val nameKey: String,
        val name: String,
        val description: String,
        val settings: List<Setting> = emptyList(),
    )

    /** One setting as search sees it: its key, its translated name and the translated labels of its values. */
    data class Setting(val key: String, val nameKey: String, val label: String, val optionLabels: List<String> = emptyList())

    enum class Kind { MODULE, CATEGORY, FEATURE, SETTING, NOTE }

    /** Why a NOTE row is shown under a module. */
    enum class Note { NO_FEATURES, FAILED }

    /**
     * One visible sidebar row.
     *
     * @param categoryId the category of a CATEGORY, FEATURE or SETTING row, else null
     * @param featureId the feature of a FEATURE or SETTING row, else null
     * @param settingKey the setting of a SETTING row (a search result), else null
     * @param count the number of (matching) features of a MODULE or CATEGORY row
     * @param flagged a FEATURE row with invalid input
     * @param depth indentation level: 1 under a module row, 0 without module rows
     * @param note the reason of a NOTE row, else null
     */
    data class Row(
        val kind: Kind,
        val moduleId: String,
        val categoryId: String?,
        val featureId: String?,
        val settingKey: String?,
        val count: Int,
        val expanded: Boolean,
        val selected: Boolean,
        val flagged: Boolean,
        val depth: Int,
        val note: Note? = null,
    ) {
        /** For Java callers and tests. */
        fun isFlagged(): Boolean = flagged
    }

    private val modules: List<Module> = modules.toList()
    private val expandedModules = modules.map { it.id }.toMutableSet()
    private val expanded = mutableSetOf<String>()
    private var beforeSearch: Set<String>? = null
    private var beforeSearchModules: Set<String>? = null
    private var selectedModule: String? = null
    private var selected: String? = null
    private var query = ""

    init {
        val first = find(preselectModule, preselectFeature)
            ?: (preselectFeature?.let { id -> all().firstOrNull { it.second.id == id } })
            ?: all().firstOrNull()
        if (first != null) {
            selectedModule = first.first
            selected = first.second.id
            expanded += key(first.first, first.second.categoryId)
        }
    }

    /** Whether module rows are shown: only when more than one module is installed. */
    fun showsModules(): Boolean = modules.size > 1

    fun modules(): List<Module> = modules

    /** Whether any module has a feature. */
    fun hasFeatures(): Boolean = modules.any { it.features.isNotEmpty() }

    /** Category ids of [moduleId] that have features, in order of their first feature. */
    fun categories(moduleId: String): List<String> =
        module(moduleId)?.features?.map { it.categoryId }?.distinct() ?: emptyList()

    /** The selected feature's id, or null. */
    fun selected(): String? = selected

    /** The selected feature's module id, or null. */
    fun selectedModule(): String? = selectedModule

    fun selectedFeature(): Feature? = find(selectedModule, selected)?.second

    fun query(): String = query

    fun searching(): Boolean = normalize(query).isNotEmpty()

    fun isExpanded(moduleId: String, categoryId: String): Boolean = key(moduleId, categoryId) in expanded

    /** For single-module callers: whether the category is expanded in the selected (or only) module. */
    fun isExpanded(categoryId: String): Boolean =
        modules.any { key(it.id, categoryId) in expanded }

    fun isModuleExpanded(moduleId: String): Boolean = moduleId in expandedModules

    /**
     * A click on a module row: a collapsed one expands and, unless the selection is already in it, selects its first
     * feature; an expanded one collapses and keeps the selection. Ignored while searching.
     */
    fun clickModule(moduleId: String) {
        if (searching()) return
        if (expandedModules.remove(moduleId)) return
        expandedModules += moduleId
        if (selectedModule != moduleId) {
            module(moduleId)?.features?.firstOrNull()?.let { select(moduleId, it.id) }
        }
    }

    fun setModuleExpanded(moduleId: String, open: Boolean) {
        if (open) expandedModules += moduleId else expandedModules -= moduleId
    }

    /**
     * A click on a category: a collapsed one expands and selects its first feature unless the selection is already in
     * it; an expanded one collapses and keeps the selection. Ignored while searching (all results are shown).
     */
    fun clickCategory(moduleId: String, categoryId: String) {
        if (searching()) return
        val k = key(moduleId, categoryId)
        if (expanded.remove(k)) return
        expanded += k
        val current = selectedFeature()
        if (current == null || selectedModule != moduleId || current.categoryId != categoryId) {
            module(moduleId)?.features?.firstOrNull { it.categoryId == categoryId }?.let {
                selectedModule = moduleId
                selected = it.id
            }
        }
    }

    /** Collapses or expands without changing the selection (keyboard Left/Right). */
    fun setExpanded(moduleId: String, categoryId: String, open: Boolean) {
        val k = key(moduleId, categoryId)
        if (open) expanded += k else expanded -= k
    }

    /** Selects a feature; its module and category expand so the selection stays visible after the search is cleared. */
    fun select(moduleId: String, featureId: String) {
        val entry = find(moduleId, featureId) ?: return
        selectedModule = entry.first
        selected = entry.second.id
        val k = key(entry.first, entry.second.categoryId)
        val saved = beforeSearch
        if (saved != null) {
            beforeSearch = saved + k
            beforeSearchModules = beforeSearchModules.orEmpty() + entry.first
        } else {
            expanded += k
            expandedModules += entry.first
        }
    }

    /**
     * Changes the search text. Starting a search remembers the expansion state and clearing it restores that state.
     * While searching, a selection outside the results moves to the first matching feature, so the panel never shows
     * a feature the results do not list.
     */
    fun setQuery(text: String?) {
        val next = text ?: ""
        val wasSearching = searching()
        query = next
        if (searching() && !wasSearching) {
            beforeSearch = expanded.toSet()
            beforeSearchModules = expandedModules.toSet()
        } else if (!searching() && wasSearching) {
            expanded.clear()
            expanded += beforeSearch.orEmpty()
            expandedModules.clear()
            expandedModules += beforeSearchModules.orEmpty()
            beforeSearch = null
            beforeSearchModules = null
        }
        if (searching()) {
            val matches = all().filter { featureMatches(it.second) }
            if (matches.isNotEmpty() && matches.none { it.first == selectedModule && it.second.id == selected }) {
                select(matches.first().first, matches.first().second.id)
            }
        }
    }

    /** The visible rows; [flagged] marks features with invalid input (settings-ux-v1.md T-5). */
    fun rows(flagged: (moduleId: String, featureId: String) -> Boolean): List<Row> {
        val rows = mutableListOf<Row>()
        val searching = searching()
        val showModules = showsModules()
        val depth = if (showModules) 1 else 0
        for (module in modules) {
            val visible = if (searching) module.features.filter(::featureMatches) else module.features
            if (searching && visible.isEmpty()) continue
            if (showModules) {
                val open = searching || module.id in expandedModules
                rows += Row(Kind.MODULE, module.id, null, null, null, visible.size, open, false, false, 0)
                if (!open) continue
                if (module.features.isEmpty()) {
                    rows += Row(Kind.NOTE, module.id, null, null, null, 0, false, false, false, 1,
                        if (module.failed) Note.FAILED else Note.NO_FEATURES)
                    continue
                }
            }
            for (category in categories(module.id)) {
                val inCategory = visible.filter { it.categoryId == category }
                if (inCategory.isEmpty()) continue
                val open = searching || key(module.id, category) in expanded
                rows += Row(Kind.CATEGORY, module.id, category, null, null, inCategory.size, open, false, false, depth)
                if (!open) continue
                for (f in inCategory) {
                    rows += Row(Kind.FEATURE, module.id, category, f.id, null, 0, false,
                        module.id == selectedModule && f.id == selected, flagged(module.id, f.id), depth)
                    if (searching) {
                        for (s in f.settings) {
                            if (settingMatches(s)) {
                                rows += Row(Kind.SETTING, module.id, category, f.id, s.key, 0, false, false, false,
                                    depth)
                            }
                        }
                    }
                }
            }
        }
        return rows
    }

    /** Whether the search finds anything (false only while searching without results). */
    fun hasResults(): Boolean = !searching() || all().any { featureMatches(it.second) }

    private fun featureMatches(f: Feature): Boolean {
        val q = normalize(query)
        return contains(f.name, q) || contains(f.description, q) || contains(f.id, q) || f.settings.any(::settingMatches)
    }

    private fun settingMatches(s: Setting): Boolean {
        val q = normalize(query)
        return contains(s.label, q) || contains(s.key, q) || s.optionLabels.any { contains(it, q) }
    }

    private fun module(id: String?): Module? = modules.firstOrNull { it.id == id }

    private fun all(): List<Pair<String, Feature>> = modules.flatMap { m -> m.features.map { m.id to it } }

    private fun find(moduleId: String?, featureId: String?): Pair<String, Feature>? {
        if (moduleId == null || featureId == null) return null
        return module(moduleId)?.features?.firstOrNull { it.id == featureId }?.let { moduleId to it }
    }

    private fun key(moduleId: String, categoryId: String) = "$moduleId/$categoryId"

    companion object {
        /** Case- and accent-insensitive form used for matching ("Saturação" matches "saturacao"). */
        @JvmStatic
        fun normalize(text: String?): String {
            if (text == null) return ""
            val decomposed = Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
            return decomposed.replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
        }

        private fun contains(text: String?, normalizedQuery: String): Boolean =
            text != null && normalize(text).contains(normalizedQuery)
    }
}
