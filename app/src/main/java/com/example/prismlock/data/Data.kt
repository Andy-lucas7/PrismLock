package com.example.prismlock.data

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.media.AudioManager
import android.os.BatteryManager
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.prismlock.model.Presets
import com.example.prismlock.model.Project
import com.example.prismlock.model.copyDeep
import com.example.prismlock.model.migrated
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

val Context.prismStore by preferencesDataStore("prismlock")

@Serializable
private data class CarouselIpcState(
    val index: Int = 0,
    val lastSwitch: Long = 0L,
    val currentUri: String? = null,
    val bakeVersion: Long = 0L
)

@Serializable
data class WeatherIpcState(
    val key: String = "",
    val time: Long = 0L,
    val temp: Float? = null,
    val code: Int = 0,
    val day: Int = 1,
    val city: String = ""
)

object ProjectStore {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    const val ACTION_RELOAD_PROJECT = "com.example.prismlock.ACTION_RELOAD_PROJECT"
    private val ipcLock = Any()
    private val KEY = stringPreferencesKey("project")
    private val THEMES_KEY = stringPreferencesKey("saved_themes_v1")
    private val WIDGETS_KEY = stringPreferencesKey("saved_widgets_v1")
    private val ACTIVE_WIDGET_KEY = stringPreferencesKey("active_widget_project")
    private val HERO_SEEN_KEY = booleanPreferencesKey("hero_seen_v1")
    private val BATTERY_PROMPT_DISMISSED_KEY = booleanPreferencesKey("battery_prompt_dismissed_v1")
    private val HUB_GRID_MODE_KEY = booleanPreferencesKey("hub_grid_mode_v1")
    private val IDX = intPreferencesKey("carousel_index")
    private val LAST_SWITCH = longPreferencesKey("carousel_switch")
    private val CURRENT_URI = stringPreferencesKey("carousel_current_uri")
    private val ACTIVE_BAKE_VERSION = longPreferencesKey("active_bake_version")
    private val FONT_FOLDER_URI = stringPreferencesKey("font_folder_uri")
    private val listSerializer = ListSerializer(Project.serializer())
    private val globalsMapSerializer = MapSerializer(String.serializer(), String.serializer())

    private fun backupCorruptedJson(ctx: Context, prefix: String, raw: String) {
        android.util.Log.e("ProjectStore", "Corrupted JSON detected for $prefix; backing up and preserving store")
        runCatching {
            val f = File(ctx.applicationContext.filesDir, "${prefix}_backup_${System.currentTimeMillis()}.json")
            f.writeText(raw, Charsets.UTF_8)
        }
    }

    private fun runtimeGlobalsFile(ctx: Context, projectId: String): File {
        val safe = projectId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(ipcDir(ctx), "runtime_globals_$safe.json")
    }

    private fun writeRuntimeGlobals(ctx: Context, p: Project) {
        val map = p.globals.associate { it.name.lowercase() to it.value }
        writeAtomicText(runtimeGlobalsFile(ctx, p.id), json.encodeToString(globalsMapSerializer, map))
    }

    private fun applyRuntimeGlobals(ctx: Context, p: Project): Project {
        if (p.globals.isEmpty()) return p
        val f = runtimeGlobalsFile(ctx, p.id)
        if (!f.exists()) return p
        val map = synchronized(ipcLock) {
            runCatching { json.decodeFromString(globalsMapSerializer, f.readText()) }.getOrNull()
        } ?: return p
        if (map.isEmpty()) return p
        var changed = false
        val updated = p.globals.map { g ->
            val v = map[g.name.lowercase()]
            if (v != null && v != g.value) {
                changed = true
                g.copy(value = v)
            } else g
        }
        return if (changed) p.copy(globals = updated) else p
    }

    private fun decodeProject(raw: String): Project? =
        runCatching { json.decodeFromString(Project.serializer(), raw).migrated().refreshedStarter() }.getOrNull()

    private fun decodeProjectList(raw: String): List<Project>? =
        runCatching { json.decodeFromString(listSerializer, raw).map { it.migrated().refreshedStarter() } }.getOrNull()

    /**
     * Starters bundled by older versions (legacy id → original title, replacement starter id). A saved copy
     * that still carries its original title is treated as untouched and gets the new design; its id, photos,
     * folder and settings are kept so the active theme/widget references stay valid. Renamed copies are left as-is.
     */
    private val legacyStarters: Map<Pair<String, String>, String> = mapOf(
        ("theme_ios_crystal" to "Cristal Óptico") to "style_glass_aurora",
        ("theme_samsung_stretch" to "Samsung Esticado") to "style_modern_depth",
        ("widget_crystal_clock" to "Relógio Cristal Elástico") to "style_w_elastic",
        ("widget_weather_battery" to "Clima & Bateria Vidro") to "style_w_clean",
        ("widget_music_smart" to "Capsula Mídia Cristal") to "style_w_music",
        // Interim starters from the previous redesign.
        ("starter_liquid_aurora" to "Aurora Líquida") to "style_glass_aurora",
        ("starter_elastic_monolith" to "Monolito Elástico") to "style_modern_depth",
        ("starter_neon_prism" to "Prisma Neon") to "style_bold_acid",
        ("starter_editorial_noir" to "Editorial Noir") to "style_elegant_editorial",
        ("starter_w_crystal" to "Cristal Elástico") to "style_w_elastic",
        ("starter_w_energy" to "Clima & Energia") to "style_w_clean",
        ("starter_w_sonic" to "Cápsula Sonora") to "style_w_music",
        ("starter_w_calendar" to "Calendário Prisma") to "style_w_calendar"
    )

    private fun Project.refreshedStarter(): Project {
        val replacementId = legacyStarters[id to title] ?: return this
        val fresh = (starterThemes + starterWidgets).firstOrNull { it.id == replacementId } ?: return this
        return copy(title = fresh.title, layers = fresh.layers, widgetAspect = if (isWidget) fresh.widgetAspect else widgetAspect)
    }

    /** Single shared default: `Project()` mints random layer ids, so fresh instances never compare equal. */
    val emptyProject: Project by lazy { Project() }

    fun flow(ctx: Context): Flow<Project> = ctx.applicationContext.prismStore.data
        .map { it[KEY] }.distinctUntilChanged()
        .map { s -> s?.let { decodeProject(it)?.let { p -> applyRuntimeGlobals(ctx, p) } } ?: emptyProject }

    fun themesFlow(ctx: Context): Flow<List<Project>> = ctx.applicationContext.prismStore.data
        .map { it[THEMES_KEY] to it[KEY] }
        .distinctUntilChanged()
        .map { (themesRaw, activeRaw) ->
            val decoded = themesRaw?.let { decodeProjectList(it) }
            if (!decoded.isNullOrEmpty()) {
                decoded.map { applyRuntimeGlobals(ctx, it) }
            } else {
                val active = activeRaw?.let { decodeProject(it)?.let { p -> applyRuntimeGlobals(ctx, p) } }
                if (active != null) {
                    listOf(active)
                } else {
                    defaultStarterThemes()
                }
            }
        }

    /** One starter per style/audience, mirroring the most-saved lock screen styles (see Presets.all). */
    private val starterThemes: List<Project> by lazy { listOf(
        Project(id = "style_modern_depth", title = "Moderno · Profundidade", layers = Presets.modernDepth(), dim = 0.08f),
        Project(id = "style_clean_mist", title = "Clean · Névoa", layers = Presets.cleanMist(), dim = 0.06f),
        Project(id = "style_cute_peach", title = "Fofo · Pêssego", layers = Presets.cutePeach(), dim = 0.04f),
        Project(id = "style_glass_aurora", title = "Vidro · Aurora", layers = Presets.liquidAurora()),
        Project(id = "style_elegant_editorial", title = "Elegante · Editorial", layers = Presets.elegantEditorial(), dim = 0.18f),
        Project(id = "style_advanced_terminal", title = "Avançado · Terminal", layers = Presets.advancedTerminal(), dim = 0.2f),
        Project(id = "style_simple_corner", title = "Simples · Canto", layers = Presets.simpleCorner(), dim = 0.1f),
        Project(id = "style_bold_acid", title = "Ousado · Ácido", layers = Presets.boldAcid(), dim = 0.15f)
    ) }

    fun defaultStarterThemes(): List<Project> = starterThemes

    fun newTheme(title: String = "Novo Tema"): Project = Project(
        id = UUID.randomUUID().toString(),
        title = title,
        layers = Presets.liquidAurora().map { it.copyDeep() }
    )

    fun isWallpaperProcess(): Boolean = runCatching {
        Application.getProcessName().endsWith(":wallpaper")
    }.getOrDefault(false)

    private fun ipcDir(ctx: Context): File =
        File(ctx.applicationContext.filesDir, "ipc_state").apply { mkdirs() }

    private fun activeProjectFile(ctx: Context): File = File(ipcDir(ctx), "active_project.json")
    private fun activeWidgetFile(ctx: Context): File = File(ipcDir(ctx), "active_widget.json")
    private fun carouselFile(ctx: Context): File = File(ipcDir(ctx), "carousel_state.json")
    private fun weatherFile(ctx: Context): File = File(ipcDir(ctx), "weather.json")

    fun readWeatherIpc(ctx: Context): WeatherIpcState? = synchronized(ipcLock) {
        val f = weatherFile(ctx)
        if (!f.exists()) return@synchronized null
        runCatching { json.decodeFromString(WeatherIpcState.serializer(), f.readText()) }.getOrNull()
    }

    fun writeWeatherIpc(ctx: Context, state: WeatherIpcState) {
        writeAtomicText(weatherFile(ctx), json.encodeToString(WeatherIpcState.serializer(), state))
    }

    /** Processo de UI espelha o DataStore nos arquivos IPC; o processo :wallpaper nunca abre o DataStore. */
    suspend fun ensureIpcMirrors(ctx: Context) {
        if (isWallpaperProcess()) return
        val projectFile = activeProjectFile(ctx)
        val widgetFile = activeWidgetFile(ctx)
        val carouselFile = carouselFile(ctx)
        if (projectFile.exists() && widgetFile.exists() && carouselFile.exists()) return
        val prefs = ctx.applicationContext.prismStore.data.first()
        if (!projectFile.exists()) prefs[KEY]?.let { writeAtomicText(projectFile, it) }
        if (!widgetFile.exists()) prefs[ACTIVE_WIDGET_KEY]?.let { writeAtomicText(widgetFile, it) }
        if (!carouselFile.exists()) {
            val state = CarouselIpcState(
                index = prefs[IDX] ?: 0,
                lastSwitch = prefs[LAST_SWITCH] ?: 0L,
                currentUri = prefs[CURRENT_URI],
                bakeVersion = prefs[ACTIVE_BAKE_VERSION] ?: 0L
            )
            writeAtomicText(carouselFile, json.encodeToString(CarouselIpcState.serializer(), state))
        }
    }

    private fun writeAtomicText(file: File, text: String) {
        synchronized(ipcLock) {
            runCatching {
                val tmp = File(file.parentFile, "${file.name}.tmp")
                tmp.writeText(text, Charsets.UTF_8)
                if (!tmp.renameTo(file)) {
                    file.writeText(text, Charsets.UTF_8)
                    tmp.delete()
                }
            }
        }
    }

    private fun readCarouselIpc(ctx: Context): CarouselIpcState? = synchronized(ipcLock) {
        val f = carouselFile(ctx)
        if (!f.exists()) return@synchronized null
        runCatching { json.decodeFromString(CarouselIpcState.serializer(), f.readText()) }.getOrNull()
    }

    private fun updateCarouselIpc(ctx: Context, transform: (CarouselIpcState) -> CarouselIpcState) {
        synchronized(ipcLock) {
            val f = carouselFile(ctx)
            val cur = if (f.exists()) {
                runCatching { json.decodeFromString(CarouselIpcState.serializer(), f.readText()) }.getOrNull()
            } else null
            val next = transform(cur ?: CarouselIpcState())
            writeAtomicText(f, json.encodeToString(CarouselIpcState.serializer(), next))
        }
    }

    suspend fun readActiveProject(ctx: Context): Project {
        val f = activeProjectFile(ctx)
        if (f.exists()) {
            val disk = synchronized(ipcLock) {
                runCatching { decodeProject(f.readText()) }.getOrNull()
            }
            if (disk != null) return applyRuntimeGlobals(ctx, disk)
        }
        if (isWallpaperProcess()) return Project()
        val ds = ctx.applicationContext.prismStore.data.first()[KEY]
            ?.let { decodeProject(it) }
            ?: Project()
        val merged = applyRuntimeGlobals(ctx, ds)
        writeAtomicText(f, json.encodeToString(Project.serializer(), merged))
        return merged
    }

    suspend fun save(ctx: Context, p: Project) {
        val s = json.encodeToString(Project.serializer(), p)
        writeAtomicText(activeProjectFile(ctx), s)
        writeRuntimeGlobals(ctx, p)
        if (!isWallpaperProcess()) {
            ctx.applicationContext.prismStore.edit { prefs ->
                prefs[KEY] = s
                val rawList = prefs[THEMES_KEY]
                val curList = if (rawList == null) {
                    mutableListOf()
                } else {
                    val parsed = decodeProjectList(rawList)
                    if (parsed == null) {
                        backupCorruptedJson(ctx, "themes", rawList)
                        return@edit
                    }
                    parsed.toMutableList()
                }
                val idx = curList.indexOfFirst { it.id == p.id }
                if (idx >= 0) curList[idx] = p else curList.add(p)
                prefs[THEMES_KEY] = json.encodeToString(listSerializer, curList)
            }
        }
    }

    suspend fun upsertTheme(ctx: Context, p: Project) {
        writeRuntimeGlobals(ctx, p)
        ctx.applicationContext.prismStore.edit { prefs ->
            val rawList = prefs[THEMES_KEY]
            val baseList = if (rawList == null) {
                val active = prefs[KEY]?.let { decodeProject(it) }
                if (active != null) mutableListOf(active) else defaultStarterThemes().toMutableList()
            } else {
                val parsed = decodeProjectList(rawList)
                if (parsed == null) {
                    backupCorruptedJson(ctx, "themes", rawList)
                    return@edit
                }
                parsed.toMutableList()
            }
            val idx = baseList.indexOfFirst { it.id == p.id }
            if (idx >= 0) baseList[idx] = p else baseList.add(p)
            prefs[THEMES_KEY] = json.encodeToString(listSerializer, baseList)
        }
    }

    suspend fun deleteTheme(ctx: Context, themeId: String) {
        var activeProject: Project? = null
        var remainingList: List<Project> = emptyList()
        var deletedProject: Project? = null
        var corrupted = false
        ctx.applicationContext.prismStore.edit { prefs ->
            val rawList = prefs[THEMES_KEY]
            val curList = if (rawList == null) {
                defaultStarterThemes()
            } else {
                val parsed = decodeProjectList(rawList)
                if (parsed == null) {
                    backupCorruptedJson(ctx, "themes", rawList)
                    corrupted = true
                    return@edit
                }
                parsed
            }
            deletedProject = curList.firstOrNull { it.id == themeId }
            val remaining = curList.filterNot { it.id == themeId }.ifEmpty { defaultStarterThemes().take(1) }
            remainingList = remaining
            activeProject = prefs[KEY]?.let { decodeProject(it) }
            prefs[THEMES_KEY] = json.encodeToString(listSerializer, remaining)
        }
        if (corrupted) return
        val active = activeProject
        val stillInUse = (active?.folderUri?.contains(themeId) == true || active?.imageUri?.contains(themeId) == true) ||
            remainingList.any { it.folderUri?.contains(themeId) == true || it.imageUri?.contains(themeId) == true }
        if (!stillInUse) {
            runCatching { java.io.File(ctx.applicationContext.filesDir, "imported_themes/$themeId").deleteRecursively() }
        }
        MediaImporter.collectProjectUris(deletedProject).forEach { uri ->
            MediaImporter.deleteIfUnused(ctx, uri)
        }
    }

    suspend fun index(ctx: Context): Int {
        readCarouselIpc(ctx)?.let { return it.index }
        if (isWallpaperProcess()) return 0
        return ctx.applicationContext.prismStore.data.first()[IDX] ?: 0
    }

    suspend fun lastSwitch(ctx: Context): Long {
        readCarouselIpc(ctx)?.let { return it.lastSwitch }
        if (isWallpaperProcess()) return 0L
        return ctx.applicationContext.prismStore.data.first()[LAST_SWITCH] ?: 0L
    }

    suspend fun currentWallpaperUri(ctx: Context): String? {
        val ipc = readCarouselIpc(ctx)
        if (ipc != null) return ipc.currentUri
        if (isWallpaperProcess()) return null
        return ctx.applicationContext.prismStore.data.first()[CURRENT_URI]
    }

    suspend fun setCurrentWallpaperUri(ctx: Context, uri: String?) {
        updateCarouselIpc(ctx) { it.copy(currentUri = uri) }
        if (!isWallpaperProcess()) {
            ctx.applicationContext.prismStore.edit {
                if (uri != null) it[CURRENT_URI] = uri else it.remove(CURRENT_URI)
            }
        }
    }

    suspend fun setIndex(ctx: Context, i: Int, uri: String? = null) {
        val now = System.currentTimeMillis()
        updateCarouselIpc(ctx) { it.copy(index = i, lastSwitch = now, currentUri = uri ?: it.currentUri) }
        if (!isWallpaperProcess()) {
            ctx.applicationContext.prismStore.edit {
                it[IDX] = i
                it[LAST_SWITCH] = now
                if (uri != null) it[CURRENT_URI] = uri
            }
        }
    }

    fun bakeVersionFlow(ctx: Context): Flow<Long> = ctx.applicationContext.prismStore.data
        .map { it[ACTIVE_BAKE_VERSION] ?: 0L }
        .distinctUntilChanged()

    suspend fun bakeVersion(ctx: Context): Long {
        readCarouselIpc(ctx)?.let { if (it.bakeVersion > 0L) return it.bakeVersion }
        if (isWallpaperProcess()) return 0L
        return ctx.applicationContext.prismStore.data.first()[ACTIVE_BAKE_VERSION] ?: 0L
    }

    suspend fun setBakeVersion(ctx: Context, version: Long) {
        updateCarouselIpc(ctx) { it.copy(bakeVersion = version) }
        if (!isWallpaperProcess()) {
            ctx.applicationContext.prismStore.edit { it[ACTIVE_BAKE_VERSION] = version }
        }
    }

    suspend fun fontFolderUri(ctx: Context): String? = ctx.applicationContext.prismStore.data.first()[FONT_FOLDER_URI]

    suspend fun setFontFolderUri(ctx: Context, uri: String?) {
        ctx.applicationContext.prismStore.edit {
            if (uri != null) it[FONT_FOLDER_URI] = uri else it.remove(FONT_FOLDER_URI)
        }
    }

    private val starterWidgets: List<Project> by lazy { listOf(
        Project(id = "style_w_clean", title = "Clean · Relógio", layers = Presets.widgetCleanClock(), isWidget = true, widgetAspect = 2.2f),
        Project(id = "style_w_elastic", title = "Moderno · Cristal Elástico", layers = Presets.widgetElasticClock(), isWidget = true, widgetAspect = 2.0f),
        Project(id = "style_w_cute", title = "Fofo · Energia", layers = Presets.widgetCuteBattery(), isWidget = true, widgetAspect = 2.0f),
        Project(id = "style_w_advanced", title = "Avançado · Painel", layers = Presets.widgetAdvancedPanel(), isWidget = true, widgetAspect = 2.0f),
        Project(id = "style_w_music", title = "Música · Cápsula", layers = Presets.widgetMusicSmartCard(), isWidget = true, widgetAspect = 2.2f),
        Project(id = "style_w_calendar", title = "Elegante · Calendário", layers = Presets.widgetElegantCalendar(), isWidget = true, widgetAspect = 2.0f)
    ) }

    fun defaultStarterWidgets(): List<Project> = starterWidgets

    fun newWidget(title: String = "Prism Widget"): Project = Project(
        id = UUID.randomUUID().toString(),
        title = title,
        layers = Presets.widgetElasticClock().map { it.copyDeep() },
        isWidget = true,
        widgetAspect = 2.0f
    )

    fun widgetsFlow(ctx: Context): Flow<List<Project>> = ctx.applicationContext.prismStore.data
        .map { it[WIDGETS_KEY] }
        .distinctUntilChanged()
        .map { raw ->
            val decoded = raw?.let { decodeProjectList(it) }
            if (!decoded.isNullOrEmpty()) decoded.map { applyRuntimeGlobals(ctx, it.copy(isWidget = true)) } else defaultStarterWidgets()
        }

    fun activeWidgetFlow(ctx: Context): Flow<Project> = ctx.applicationContext.prismStore.data
        .map { it[ACTIVE_WIDGET_KEY] }
        .distinctUntilChanged()
        .map { raw ->
            raw?.let { decodeProject(it) }?.copy(isWidget = true)?.let { applyRuntimeGlobals(ctx, it) }
                ?: defaultStarterWidgets().first()
        }

    suspend fun getActiveWidget(ctx: Context): Project {
        val f = activeWidgetFile(ctx)
        if (f.exists()) {
            val disk = synchronized(ipcLock) {
                runCatching { decodeProject(f.readText()) }.getOrNull()?.copy(isWidget = true)
            }
            if (disk != null) return applyRuntimeGlobals(ctx, disk)
        }
        if (isWallpaperProcess()) return defaultStarterWidgets().first()
        val raw = ctx.applicationContext.prismStore.data.first()[ACTIVE_WIDGET_KEY]
        val w = raw?.let { decodeProject(it) }?.copy(isWidget = true)
            ?: defaultStarterWidgets().first()
        val merged = applyRuntimeGlobals(ctx, w)
        writeAtomicText(f, json.encodeToString(Project.serializer(), merged))
        return merged
    }

    suspend fun saveActiveWidget(ctx: Context, p: Project) {
        val clean = p.copy(isWidget = true)
        val encoded = json.encodeToString(Project.serializer(), clean)
        writeAtomicText(activeWidgetFile(ctx), encoded)
        writeRuntimeGlobals(ctx, clean)
        if (!isWallpaperProcess()) {
            ctx.applicationContext.prismStore.edit { prefs ->
                prefs[ACTIVE_WIDGET_KEY] = encoded
                val rawList = prefs[WIDGETS_KEY]
                val curList = if (rawList == null) {
                    defaultStarterWidgets().toMutableList()
                } else {
                    val parsed = decodeProjectList(rawList)
                    if (parsed == null) {
                        backupCorruptedJson(ctx, "widgets", rawList)
                        return@edit
                    }
                    parsed.toMutableList()
                }
                val idx = curList.indexOfFirst { it.id == clean.id }
                if (idx >= 0) curList[idx] = clean else curList.add(clean)
                prefs[WIDGETS_KEY] = json.encodeToString(listSerializer, curList)
            }
        }
    }

    suspend fun upsertWidget(ctx: Context, p: Project) {
        val clean = p.copy(isWidget = true)
        writeRuntimeGlobals(ctx, clean)
        if (isWallpaperProcess()) {
            saveActiveWidget(ctx, clean)
            return
        }
        var shouldWriteActiveIpc = false
        ctx.applicationContext.prismStore.edit { prefs ->
            val rawList = prefs[WIDGETS_KEY]
            val baseList = if (rawList == null) {
                defaultStarterWidgets().toMutableList()
            } else {
                val parsed = decodeProjectList(rawList)
                if (parsed == null) {
                    backupCorruptedJson(ctx, "widgets", rawList)
                    return@edit
                }
                parsed.toMutableList()
            }
            val idx = baseList.indexOfFirst { it.id == clean.id }
            if (idx >= 0) baseList[idx] = clean else baseList.add(clean)
            prefs[WIDGETS_KEY] = json.encodeToString(listSerializer, baseList)
            val activeRaw = prefs[ACTIVE_WIDGET_KEY]
            val active = activeRaw?.let { decodeProject(it) }
            if (active?.id == clean.id) {
                prefs[ACTIVE_WIDGET_KEY] = json.encodeToString(Project.serializer(), clean)
                shouldWriteActiveIpc = true
            }
        }
        if (shouldWriteActiveIpc) {
            writeAtomicText(activeWidgetFile(ctx), json.encodeToString(Project.serializer(), clean))
        }
    }

    suspend fun deleteWidget(ctx: Context, widgetId: String) {
        var deletedProject: Project? = null
        var replacementActive: Project? = null
        var corrupted = false
        ctx.applicationContext.prismStore.edit { prefs ->
            val rawList = prefs[WIDGETS_KEY]
            val curList = if (rawList == null) {
                defaultStarterWidgets()
            } else {
                val parsed = decodeProjectList(rawList)
                if (parsed == null) {
                    backupCorruptedJson(ctx, "widgets", rawList)
                    corrupted = true
                    return@edit
                }
                parsed
            }
            deletedProject = curList.firstOrNull { it.id == widgetId }
            val remaining = curList.filterNot { it.id == widgetId }.ifEmpty { defaultStarterWidgets().take(1) }
            prefs[WIDGETS_KEY] = json.encodeToString(listSerializer, remaining)
            val activeId = prefs[ACTIVE_WIDGET_KEY]?.let { decodeProject(it) }?.id
            if (activeId == widgetId) {
                replacementActive = remaining.first()
            }
        }
        if (corrupted) return
        replacementActive?.let { nextActive ->
            saveActiveWidget(ctx, nextActive)
            com.example.prismlock.widget.PrismWidgetProvider.updateAll(ctx)
        }
        MediaImporter.collectProjectUris(deletedProject).forEach { uri ->
            MediaImporter.deleteIfUnused(ctx, uri)
        }
    }

    fun heroSeenFlow(ctx: Context): Flow<Boolean> = ctx.applicationContext.prismStore.data
        .map { it[HERO_SEEN_KEY] ?: false }
        .distinctUntilChanged()

    suspend fun setHeroSeen(ctx: Context, seen: Boolean) {
        ctx.applicationContext.prismStore.edit { it[HERO_SEEN_KEY] = seen }
    }

    fun batteryPromptDismissedFlow(ctx: Context): Flow<Boolean> = ctx.applicationContext.prismStore.data
        .map { it[BATTERY_PROMPT_DISMISSED_KEY] ?: false }
        .distinctUntilChanged()

    suspend fun setBatteryPromptDismissed(ctx: Context, dismissed: Boolean) {
        ctx.applicationContext.prismStore.edit { it[BATTERY_PROMPT_DISMISSED_KEY] = dismissed }
    }

    fun hubGridModeFlow(ctx: Context): Flow<Boolean> = ctx.applicationContext.prismStore.data
        .map { it[HUB_GRID_MODE_KEY] ?: false }
        .distinctUntilChanged()

    suspend fun setHubGridMode(ctx: Context, grid: Boolean) {
        ctx.applicationContext.prismStore.edit { it[HUB_GRID_MODE_KEY] = grid }
    }
}

data class Env(
    val battery: Int = 100,
    val charging: Boolean = false,
    val batteryTempC: Float = 28.0f,
    val temp: Int? = null,
    val weatherCode: Int = 0,
    val isDay: Boolean = true,
    val city: String = "",
    val alarm: String = "",
    val steps: Int = 0,
    val locked: Boolean = true,
    val mediaVolume: Int = 50,
    val mediaPlaying: Boolean = false,
    val ringerMode: Int = AudioManager.RINGER_MODE_NORMAL
)

object EnvProvider {
    private val _env = MutableStateFlow(Env())
    val env: StateFlow<Env> = _env
    fun update(f: (Env) -> Env) { _env.value = f(_env.value) }

    fun refreshBattery(ctx: Context): Boolean {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        return applyBattery(i)
    }

    fun applyBattery(i: Intent): Boolean {
        val lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val pct = if (lvl >= 0) lvl * 100 / sc else _env.value.battery
        val ch = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        val rawTemp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        val bTemp = if (rawTemp > 0) rawTemp / 10f else _env.value.batteryTempC
        val old = _env.value
        if (old.battery == pct && old.charging == ch && kotlin.math.abs(old.batteryTempC - bTemp) < 1.0f) return false
        _env.value = old.copy(battery = pct, charging = ch, batteryTempC = bTemp)
        return true
    }

    fun refreshAlarm(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val next = am?.nextAlarmClock
        val s = next?.let { DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(it.triggerTime).atZone(ZoneId.systemDefault())) } ?: ""
        update { it.copy(alarm = s) }
    }

    fun refreshAudio(ctx: Context): Boolean {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return false
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, maxVol)
        val pct = (curVol * 100f / maxVol).toInt().coerceIn(0, 100)
        val playing = am.isMusicActive
        val ringer = am.ringerMode
        val old = _env.value
        if (old.mediaVolume == pct && old.mediaPlaying == playing && old.ringerMode == ringer) return false
        _env.value = old.copy(mediaVolume = pct, mediaPlaying = playing, ringerMode = ringer)
        return true
    }

    @SuppressLint("MissingPermission")
    fun lastLocation(ctx: Context): Pair<Double, Double>? = runCatching {
        val lm = ctx.getSystemService(LocationManager::class.java)
        val l = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
        l?.let { it.latitude to it.longitude }
    }.getOrNull()

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        return try { c.inputStream.bufferedReader().readText() } finally { c.disconnect() }
    }

    suspend fun geocode(name: String): Triple<Double, Double, String>? = withContext(Dispatchers.IO) {
        runCatching {
            val r = get("https://geocoding-api.open-meteo.com/v1/search?count=1&language=pt&name=" + URLEncoder.encode(name, "UTF-8"))
            val o = ProjectStore.json.parseToJsonElement(r).jsonObject["results"]!!.jsonArray[0].jsonObject
            Triple(o["latitude"]!!.jsonPrimitive.double, o["longitude"]!!.jsonPrimitive.double, o["name"]!!.jsonPrimitive.content)
        }.getOrNull()
    }

    suspend fun refreshWeather(ctx: Context, lat: Double?, lon: Double?, city: String, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (lat == null || lon == null) {
            update { it.copy(temp = null, city = city) }
            return@withContext false
        }
        val cached = ProjectStore.readWeatherIpc(ctx.applicationContext)
        val key = "%.2f,%.2f".format(lat, lon)
        val fresh = cached != null && cached.key == key && System.currentTimeMillis() - cached.time < 3_600_000L
        if (fresh && !force) {
            update { it.copy(temp = cached?.temp?.toInt(), weatherCode = cached?.code ?: 0, isDay = (cached?.day ?: 1) == 1, city = city.ifBlank { cached?.city ?: "" }) }
            return@withContext false
        }
        runCatching {
            val r = get("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code,is_day&timezone=auto")
            val cur = ProjectStore.json.parseToJsonElement(r).jsonObject["current"]!!.jsonObject
            val t = cur["temperature_2m"]!!.jsonPrimitive.double.toFloat()
            val code = cur["weather_code"]!!.jsonPrimitive.int
            val day = cur["is_day"]?.jsonPrimitive?.int ?: 1
            ProjectStore.writeWeatherIpc(
                ctx.applicationContext,
                WeatherIpcState(key = key, time = System.currentTimeMillis(), temp = t, code = code, day = day, city = city)
            )
            update { it.copy(temp = Math.round(t), weatherCode = code, isDay = day == 1, city = city) }
            true
        }.getOrDefault(false)
    }
}

fun CoroutineScope.launchSafe(block: suspend CoroutineScope.() -> Unit) = launch {
    try {
        block()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        android.util.Log.w("PrismLaunchSafe", "coroutine failed", e)
    }
}
