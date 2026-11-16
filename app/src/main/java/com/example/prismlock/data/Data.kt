package com.example.prismlock.data

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.BatteryManager
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.prismlock.model.Presets
import com.example.prismlock.model.Project
import com.example.prismlock.model.copyDeep
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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

val Context.prismStore by preferencesDataStore("prismlock")

object ProjectStore {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val KEY = stringPreferencesKey("project")
    private val THEMES_KEY = stringPreferencesKey("saved_themes_v1")
    private val IDX = intPreferencesKey("carousel_index")
    private val LAST_SWITCH = longPreferencesKey("carousel_switch")
    private val CURRENT_URI = stringPreferencesKey("carousel_current_uri")
    private val ACTIVE_BAKE_VERSION = longPreferencesKey("active_bake_version")
    private val listSerializer = ListSerializer(Project.serializer())

    fun flow(ctx: Context): Flow<Project> = ctx.applicationContext.prismStore.data
        .map { it[KEY] }.distinctUntilChanged()
        .map { s -> s?.let { runCatching { json.decodeFromString(Project.serializer(), it) }.getOrNull() } ?: Project() }

    fun themesFlow(ctx: Context): Flow<List<Project>> = ctx.applicationContext.prismStore.data
        .map { it[THEMES_KEY] to it[KEY] }
        .distinctUntilChanged()
        .map { (themesRaw, activeRaw) ->
            val decoded = themesRaw?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() }
            if (!decoded.isNullOrEmpty()) {
                decoded
            } else {
                val active = activeRaw?.let { runCatching { json.decodeFromString(Project.serializer(), it) }.getOrNull() }
                if (active != null) {
                    listOf(active)
                } else {
                    defaultStarterThemes()
                }
            }
        }

    fun defaultStarterThemes(): List<Project> = listOf(
        Project(
            id = "theme_ios_crystal",
            title = "Cristal Óptico",
            layers = Presets.iosGlass()
        ),
        Project(
            id = "theme_samsung_stretch",
            title = "Samsung Esticado",
            layers = Presets.samsung()
        )
    )

    fun newTheme(title: String = "Novo Tema"): Project = Project(
        id = UUID.randomUUID().toString(),
        title = title,
        layers = Presets.iosGlass().map { it.copyDeep() }
    )

    suspend fun save(ctx: Context, p: Project) {
        val s = json.encodeToString(Project.serializer(), p)
        ctx.applicationContext.prismStore.edit { prefs ->
            prefs[KEY] = s
            val curList = prefs[THEMES_KEY]
                ?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() }
                ?.toMutableList()
                ?: mutableListOf()
            val idx = curList.indexOfFirst { it.id == p.id }
            if (idx >= 0) curList[idx] = p else curList.add(0, p)
            prefs[THEMES_KEY] = json.encodeToString(listSerializer, curList)
        }
    }

    suspend fun upsertTheme(ctx: Context, p: Project) {
        ctx.applicationContext.prismStore.edit { prefs ->
            val baseList = prefs[THEMES_KEY]
                ?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() }
                ?.toMutableList()
                ?: run {
                    val active = prefs[KEY]?.let { runCatching { json.decodeFromString(Project.serializer(), it) }.getOrNull() }
                    if (active != null) mutableListOf(active) else defaultStarterThemes().toMutableList()
                }
            val idx = baseList.indexOfFirst { it.id == p.id }
            if (idx >= 0) baseList[idx] = p else baseList.add(p)
            prefs[THEMES_KEY] = json.encodeToString(listSerializer, baseList)
        }
    }

    suspend fun deleteTheme(ctx: Context, themeId: String) {
        ctx.applicationContext.prismStore.edit { prefs ->
            val curList = prefs[THEMES_KEY]
                ?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() }
                ?: defaultStarterThemes()
            val remaining = curList.filterNot { it.id == themeId }.ifEmpty { defaultStarterThemes().take(1) }
            prefs[THEMES_KEY] = json.encodeToString(listSerializer, remaining)
        }
    }

    suspend fun index(ctx: Context): Int = ctx.applicationContext.prismStore.data.first()[IDX] ?: 0
    suspend fun lastSwitch(ctx: Context): Long = ctx.applicationContext.prismStore.data.first()[LAST_SWITCH] ?: 0L
    suspend fun currentWallpaperUri(ctx: Context): String? = ctx.applicationContext.prismStore.data.first()[CURRENT_URI]

    suspend fun setCurrentWallpaperUri(ctx: Context, uri: String?) {
        ctx.applicationContext.prismStore.edit {
            if (uri != null) it[CURRENT_URI] = uri else it.remove(CURRENT_URI)
        }
    }

    suspend fun setIndex(ctx: Context, i: Int, uri: String? = null) {
        ctx.applicationContext.prismStore.edit {
            it[IDX] = i
            it[LAST_SWITCH] = System.currentTimeMillis()
            if (uri != null) it[CURRENT_URI] = uri
        }
    }

    fun bakeVersionFlow(ctx: Context): Flow<Long> = ctx.applicationContext.prismStore.data
        .map { it[ACTIVE_BAKE_VERSION] ?: 0L }
        .distinctUntilChanged()

    suspend fun bakeVersion(ctx: Context): Long = ctx.applicationContext.prismStore.data.first()[ACTIVE_BAKE_VERSION] ?: 0L

    suspend fun setBakeVersion(ctx: Context, version: Long) {
        ctx.applicationContext.prismStore.edit { it[ACTIVE_BAKE_VERSION] = version }
    }
}

data class Env(
    val battery: Int = 100,
    val charging: Boolean = false,
    val temp: Int? = null,
    val weatherCode: Int = 0,
    val isDay: Boolean = true,
    val city: String = "",
    val alarm: String = "",
    val steps: Int = 0,
    val locked: Boolean = true
)

object EnvProvider {
    private val _env = MutableStateFlow(Env())
    val env: StateFlow<Env> = _env
    private val W_TEMP = floatPreferencesKey("w_temp")
    private val W_CODE = intPreferencesKey("w_code")
    private val W_DAY = intPreferencesKey("w_day")
    private val W_TIME = longPreferencesKey("w_time")
    private val W_KEY = stringPreferencesKey("w_key")
    private val W_CITY = stringPreferencesKey("w_city")

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
        val old = _env.value
        if (old.battery == pct && old.charging == ch) return false
        _env.value = old.copy(battery = pct, charging = ch)
        return true
    }

    fun refreshAlarm(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val next = am?.nextAlarmClock
        val s = next?.let { DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(it.triggerTime).atZone(ZoneId.systemDefault())) } ?: ""
        update { it.copy(alarm = s) }
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

    suspend fun refreshWeather(ctx: Context, lat: Double, lon: Double, city: String, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val store = ctx.applicationContext.prismStore
        val prefs = store.data.first()
        val key = "%.2f,%.2f".format(lat, lon)
        val fresh = prefs[W_KEY] == key && System.currentTimeMillis() - (prefs[W_TIME] ?: 0L) < 3_600_000L
        if (fresh && !force) {
            update { it.copy(temp = prefs[W_TEMP]?.toInt(), weatherCode = prefs[W_CODE] ?: 0, isDay = (prefs[W_DAY] ?: 1) == 1, city = city.ifBlank { prefs[W_CITY] ?: "" }) }
            return@withContext false
        }
        runCatching {
            val r = get("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code,is_day&timezone=auto")
            val cur = ProjectStore.json.parseToJsonElement(r).jsonObject["current"]!!.jsonObject
            val t = cur["temperature_2m"]!!.jsonPrimitive.double.toFloat()
            val code = cur["weather_code"]!!.jsonPrimitive.int
            val day = cur["is_day"]?.jsonPrimitive?.int ?: 1
            store.edit { it[W_TEMP] = t; it[W_CODE] = code; it[W_DAY] = day; it[W_TIME] = System.currentTimeMillis(); it[W_KEY] = key; it[W_CITY] = city }
            update { it.copy(temp = Math.round(t), weatherCode = code, isDay = day == 1, city = city) }
            true
        }.getOrDefault(false)
    }
}

fun CoroutineScope.launchSafe(block: suspend CoroutineScope.() -> Unit) = launch { runCatching { block() } }
