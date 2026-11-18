package com.example.prismlock.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable enum class LayerType { TEXT, STACK, SHAPE, ICON, SVG, IMAGE, DEPTH }
@Serializable enum class Cond { ALWAYS, LOCK_ONLY, HOME_ONLY, CHARGING, NOT_CHARGING, LOW_BATTERY, DAY, NIGHT }
@Serializable enum class Blend { NORMAL, MULTIPLY, SCREEN, OVERLAY, DARKEN, LIGHTEN, COLOR_DODGE, COLOR_BURN, SOFT_LIGHT, HARD_LIGHT, DIFFERENCE, EXCLUSION, PLUS, HUE, SATURATION, COLOR, LUMINOSITY }
@Serializable enum class Fill { SOLID, GRADIENT, ADAPTIVE, GLASS, CRYSTAL, HOLLOW }
@Serializable enum class GradientKind { LINEAR, RADIAL, SWEEP }
@Serializable enum class Anchor { TOP_LEFT, TOP_CENTER, TOP_RIGHT, CENTER_LEFT, CENTER, CENTER_RIGHT, BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT }
@Serializable enum class ShapeKind { RECT, CIRCLE, LINE, BAR, RING }
@Serializable enum class Dir { VERTICAL, HORIZONTAL }
@Serializable enum class Align { START, CENTER, END }

@Serializable
enum class TouchActionType {
    NONE,
    OPEN_APP,
    OPEN_URL,
    TOGGLE_GLOBAL,
    SET_GLOBAL,
    NEXT_GLOBAL_LIST,
    NEXT_WALLPAPER,
    MEDIA_PLAY_PAUSE,
    MEDIA_NEXT,
    MEDIA_PREV,
    SYSTEM_ALARM,
    SYSTEM_CAMERA,
    SYSTEM_SETTINGS,
    OPEN_PRISMLOCK
}

@Serializable
data class TouchAction(
    val type: TouchActionType = TouchActionType.NONE,
    val targetPackage: String = "",
    val targetLabel: String = "",
    val url: String = "",
    val globalName: String = "",
    val globalValue: String = ""
) {
    val isActive: Boolean get() = type != TouchActionType.NONE
}

@Serializable
enum class GlobalType { SWITCH, NUMBER, TEXT, COLOR, LIST }

@Serializable
data class GlobalVar(
    val name: String,
    val type: GlobalType = GlobalType.SWITCH,
    val value: String = "0",
    val options: List<String> = emptyList()
)

@Serializable
data class Glass(
    val blur: Float = 26f,
    val refraction: Float = 36f,
    val bevelWidth: Float = 22f,
    val chromaticAberration: Float = 0.005f,
    val frostAmount: Float = 0.18f,
    val lightAngle: Float = 225f,
    val saturation: Float = 1.35f,
    val tint: Int = 0x26FFFFFF,
    val grain: Float = 0.03f,
    val rim: Float = 0.85f,
    val edge: Float = 18f
)

@Serializable
data class LayerNode(
    val id: String = UUID.randomUUID().toString().take(8),
    val name: String = "Camada",
    val type: LayerType = LayerType.TEXT,
    val x: Float = 0.5f,
    val y: Float = 0.2f,
    val anchor: Anchor = Anchor.TOP_LEFT,
    val rotation: Float = 0f,
    val scale: Float = 1f,
    val opacity: Float = 1f,
    val blend: Blend = Blend.NORMAL,
    val cond: Cond = Cond.ALWAYS,
    val hidden: Boolean = false,
    val locked: Boolean = false,
    val text: String = "{HH}:{mm}",
    val font: String = "sans-serif",
    val size: Float = 120f,
    val weight: Int = 400,
    val axes: Map<String, Float> = emptyMap(),
    val letterSpacing: Float = 0f,
    val lineSpacing: Float = 0f,
    val align: Align = Align.CENTER,
    val stretch: Float = 1f,
    val strokeCompensation: Float = 1.0f,
    val stretchTop: Float = 0.42f,
    val stretchBottom: Float = 0.82f,
    val fill: Fill = Fill.SOLID,
    val color: Int = 0xFFFFFFFF.toInt(),
    val color2: Int = 0xFF7AA7FF.toInt(),
    val gradientKind: GradientKind = GradientKind.LINEAR,
    val gradientAngle: Float = 90f,
    val glass: Glass = Glass(),
    val shadowRadius: Float = 0f,
    val shadowColor: Int = 0x80000000.toInt(),
    val shadowDx: Float = 0f,
    val shadowDy: Float = 4f,
    val strokeWidth: Float = 0f,
    val strokeColor: Int = 0xFF000000.toInt(),
    val shape: ShapeKind = ShapeKind.RECT,
    val w: Float = 300f,
    val h: Float = 120f,
    val corner: Float = 40f,
    val value: String = "bat",
    val trackColor: Int = 0x40FFFFFF,
    val icon: String = "weather",
    val svgData: List<String> = emptyList(),
    val svgW: Float = 24f,
    val svgH: Float = 24f,
    val imageUri: String = "",
    val dir: Dir = Dir.VERTICAL,
    val spacing: Float = 12f,
    val parentId: String? = null,
    val children: List<LayerNode> = emptyList(),
    val touch: TouchAction = TouchAction(),
    val visibilityExpr: String = "",
    val fxColor: String = "",
    val fxOpacity: String = "",
    val fxRotation: String = "",
    val fxScale: String = "",
    val fxOffsetX: String = "",
    val fxOffsetY: String = "",
    val fxSize: String = "",
    val fxAsset: String = ""
) {
    fun hasFx(): Boolean =
        fxColor.isNotEmpty() ||
            fxOpacity.isNotEmpty() ||
            fxRotation.isNotEmpty() ||
            fxScale.isNotEmpty() ||
            fxOffsetX.isNotEmpty() ||
            fxOffsetY.isNotEmpty() ||
            fxSize.isNotEmpty() ||
            fxAsset.isNotEmpty()
}

@Serializable
data class Project(
    val id: String = "default",
    val title: String = "Cristal Óptico",
    val layers: List<LayerNode> = Presets.iosGlass(),
    val folderUri: String? = null,
    val imageUri: String? = null,
    val changeOnWake: Boolean = true,
    val changeOnDoubleTap: Boolean = true,
    val intervalMin: Int = 0,
    val depth: Boolean = true,
    val depthGuard: Boolean = true,
    val defaultDepthForNewImages: Boolean = true,
    val depthOverrides: Map<String, Boolean> = emptyMap(),
    val depthBgBlur: Float = 0f,
    val depthBgDesaturate: Float = 0f,
    val depthInvertColorPop: Boolean = false,
    val depthSubjectPop: Float = 0f,
    val depthVignette: Float = 0f,
    val city: String = "",
    val lat: Double = -23.55,
    val lon: Double = -46.63,
    val dim: Float = 0.12f,
    val globals: List<GlobalVar> = emptyList(),
    val isWidget: Boolean = false,
    val widgetAspect: Float = 2.0f,
    val customAssets: Map<String, String> = emptyMap()
) {
    fun isDepthEnabledFor(imageKey: String): Boolean =
        depthOverrides[imageKey] ?: defaultDepthForNewImages

    fun hasDepthPostProcessing(): Boolean =
        depth && (depthBgBlur > 0.01f || depthBgDesaturate > 0.01f || depthSubjectPop > 0.01f || depthVignette > 0.01f)

    fun globalsMap(): Map<String, String> =
        if (globals.isEmpty()) emptyMap() else globals.associate { it.name.lowercase() to it.value }

    fun toggleGlobal(name: String): Project {
        val key = name.trim()
        if (key.isEmpty()) return this
        val idx = globals.indexOfFirst { it.name.equals(key, ignoreCase = true) }
        val updated = if (idx < 0) {
            globals + GlobalVar(name = key, type = GlobalType.SWITCH, value = "1")
        } else {
            val g = globals[idx]
            val nextVal = when (g.type) {
                GlobalType.SWITCH -> if (g.value == "1" || g.value.equals("true", ignoreCase = true)) "0" else "1"
                GlobalType.LIST -> {
                    val opts = g.options
                    if (opts.isEmpty()) g.value else opts[(opts.indexOf(g.value) + 1) % opts.size]
                }
                GlobalType.NUMBER -> ((g.value.toDoubleOrNull() ?: 0.0) + 1.0).let {
                    if (it == kotlin.math.floor(it)) it.toLong().toString() else it.toString()
                }
                GlobalType.TEXT -> if (g.value.isEmpty()) "1" else ""
                GlobalType.COLOR -> {
                    val opts = g.options
                    if (opts.size >= 2) opts[(opts.indexOf(g.value) + 1) % opts.size] else g.value
                }
            }
            globals.toMutableList().apply { set(idx, g.copy(value = nextVal)) }
        }
        return copy(globals = updated)
    }

    fun cycleGlobal(name: String, step: Int = 1): Project {
        val key = name.trim()
        if (key.isEmpty()) return this
        val idx = globals.indexOfFirst { it.name.equals(key, ignoreCase = true) }
        if (idx < 0) return toggleGlobal(key)
        val g = globals[idx]
        val nextVal = when (g.type) {
            GlobalType.LIST -> {
                val opts = g.options
                if (opts.isEmpty()) g.value else {
                    val cur = opts.indexOf(g.value).coerceAtLeast(0)
                    opts[((cur + step) % opts.size + opts.size) % opts.size]
                }
            }
            GlobalType.SWITCH -> if (g.value == "1") "0" else "1"
            GlobalType.NUMBER -> ((g.value.toDoubleOrNull() ?: 0.0) + step).let {
                if (it == kotlin.math.floor(it)) it.toLong().toString() else it.toString()
            }
            GlobalType.TEXT, GlobalType.COLOR -> g.value
        }
        return copy(globals = globals.toMutableList().apply { set(idx, g.copy(value = nextVal)) })
    }

    fun setGlobalValue(name: String, value: String): Project {
        val key = name.trim()
        if (key.isEmpty()) return this
        val idx = globals.indexOfFirst { it.name.equals(key, ignoreCase = true) }
        val updated = if (idx < 0) {
            globals + GlobalVar(name = key, type = GlobalType.TEXT, value = value)
        } else {
            globals.toMutableList().apply { set(idx, globals[idx].copy(value = value)) }
        }
        return copy(globals = updated)
    }
}

fun LayerNode.copyDeep(newParentId: String? = null): LayerNode {
    val newId = UUID.randomUUID().toString()
    return copy(
        id = newId,
        parentId = newParentId,
        children = children.map { it.copyDeep(newParentId = newId) }
    )
}

fun List<LayerNode>.collectNestedChildIds(out: MutableSet<String> = HashSet()): Set<String> {
    for (n in this) {
        if (n.children.isNotEmpty()) {
            for (c in n.children) {
                out.add(c.id)
                c.children.collectNestedChildIds(out)
            }
        }
    }
    return out
}

fun List<LayerNode>.find(id: String?): LayerNode? {
    if (id == null) return null
    for (n in this) { if (n.id == id) return n; n.children.find(id)?.let { return it } }
    return null
}

fun List<LayerNode>.mapNode(id: String, f: (LayerNode) -> LayerNode): List<LayerNode> =
    map { if (it.id == id) f(it) else if (it.children.isNotEmpty()) it.copy(children = it.children.mapNode(id, f)) else it }

fun List<LayerNode>.removeNode(id: String): List<LayerNode> =
    filter { it.id != id }.map { if (it.children.isNotEmpty()) it.copy(children = it.children.removeNode(id)) else it }

fun List<LayerNode>.parentOf(id: String): LayerNode? {
    for (n in this) { if (n.children.any { it.id == id }) return n; n.children.parentOf(id)?.let { return it } }
    return null
}

fun List<LayerNode>.insertAfter(id: String, node: LayerNode): List<LayerNode> {
    val i = indexOfFirst { it.id == id }
    if (i >= 0) return toMutableList().apply { add(i + 1, node) }
    return map { if (it.children.isNotEmpty()) it.copy(children = it.children.insertAfter(id, node)) else it }
}

fun List<LayerNode>.move(id: String, delta: Int): List<LayerNode> {
    val i = indexOfFirst { it.id == id }
    if (i >= 0) {
        val j = (i + delta).coerceIn(0, size - 1)
        if (i == j) return this
        return toMutableList().apply { add(j, removeAt(i)) }
    }
    return map { if (it.children.isNotEmpty()) it.copy(children = it.children.move(id, delta)) else it }
}

fun List<LayerNode>.flatten(depth: Int = 0, out: MutableList<Pair<LayerNode, Int>> = mutableListOf()): List<Pair<LayerNode, Int>> {
    for (n in this) { out.add(n to depth); n.children.flatten(depth + 1, out) }
    return out
}

object Presets {
    private fun t(name: String, text: String, size: Float, weight: Int = 400, align: Align = Align.START, font: String = "sans-serif", ls: Float = 0f) =
        LayerNode(name = name, text = text, size = size, weight = weight, align = align, font = font, letterSpacing = ls)

    fun xiaomi(): List<LayerNode> = listOf(
        LayerNode(name = "Bloco Xiaomi", type = LayerType.STACK, x = 0.08f, y = 0.09f, align = Align.START, spacing = 6f, children = listOf(
            t("Hora", "{HH}:{mm}", 210f, 300, font = "sans-serif-light"),
            t("Data", "{EEEE}, {d} {MMMM}", 44f, 500),
            LayerNode(name = "Clima", type = LayerType.STACK, dir = Dir.HORIZONTAL, align = Align.CENTER, spacing = 14f, children = listOf(
                LayerNode(name = "Ícone clima", type = LayerType.ICON, icon = "weather", w = 54f, h = 54f),
                t("Temperatura", "{temp}° {city}", 40f, 400)
            ))
        )),
        LayerNode(name = "Alarme", text = "⏰ {alarm}", size = 34f, x = 0.08f, y = 0.92f, align = Align.START, cond = Cond.LOCK_ONLY)
    )

    fun iosGlass(): List<LayerNode> = listOf(
        LayerNode(name = "Data", text = "{EEEE}, {d} de {MMMM}", size = 46f, weight = 600, x = 0.5f, y = 0.085f, cond = Cond.LOCK_ONLY),
        LayerNode(name = "Hora vidro", text = "{H}:{mm}", size = 330f, weight = 800, x = 0.5f, y = 0.11f, fill = Fill.GLASS, cond = Cond.LOCK_ONLY,
            stretch = 1.15f, glass = Glass(blur = 30f, refraction = 26f, tint = 0x40FFFFFF)),
        LayerNode(name = "Sujeito 3D", type = LayerType.DEPTH),
        LayerNode(name = "Widgets", type = LayerType.STACK, dir = Dir.HORIZONTAL, x = 0.5f, y = 0.34f, align = Align.CENTER, spacing = 36f, cond = Cond.LOCK_ONLY, children = listOf(
            LayerNode(name = "Anel bateria", type = LayerType.SHAPE, shape = ShapeKind.RING, w = 110f, h = 110f, value = "bat", strokeWidth = 12f),
            LayerNode(name = "Clima", type = LayerType.ICON, icon = "weather", w = 80f, h = 80f),
            t("Temp", "{temp}°", 64f, 600)
        ))
    )

    fun samsung(): List<LayerNode> = listOf(
        LayerNode(name = "Pilha Samsung", type = LayerType.STACK, x = 0.5f, y = 0.12f, align = Align.CENTER, spacing = -30f, cond = Cond.LOCK_ONLY, children = listOf(
            LayerNode(name = "HH", text = "{HH}", size = 280f, weight = 700, font = "sans-serif-condensed", stretch = 1.9f, letterSpacing = -0.02f),
            LayerNode(name = "mm", text = "{mm}", size = 280f, weight = 700, font = "sans-serif-condensed", stretch = 1.9f, letterSpacing = -0.02f, color = 0xFFB9D4FF.toInt())
        )),
        LayerNode(name = "Sujeito 3D", type = LayerType.DEPTH),
        LayerNode(name = "Data", text = "{EEE} {d} {MMM}", size = 44f, weight = 500, x = 0.5f, y = 0.075f, cond = Cond.LOCK_ONLY)
    )

    fun huawei(): List<LayerNode> = listOf(
        LayerNode(name = "Editorial", type = LayerType.STACK, x = 0.07f, y = 0.1f, align = Align.START, spacing = 10f, cond = Cond.LOCK_ONLY, children = listOf(
            t("Dia", "{EEEE_UPPER}", 34f, 600, ls = 0.32f),
            t("Hora", "{HH}:{mm}", 190f, 200, font = "serif"),
            LayerNode(name = "Linha", type = LayerType.SHAPE, shape = ShapeKind.LINE, w = 420f, h = 3f),
            t("Data", "{yyyy. MM. dd.}", 40f, 400, ls = 0.08f),
            LayerNode(name = "Barra bateria", type = LayerType.SHAPE, shape = ShapeKind.BAR, w = 420f, h = 14f, corner = 7f, value = "bat"),
            t("Info", "{bat}% · {temp}° {city}", 30f, 400)
        )),
        LayerNode(name = "Sujeito 3D", type = LayerType.DEPTH)
    )

    fun widgetElasticClock(): List<LayerNode> = listOf(
        LayerNode(
            name = "Base Cristal Widget",
            type = LayerType.SHAPE,
            shape = ShapeKind.RECT,
            x = 0.5f,
            y = 0.5f,
            anchor = Anchor.CENTER,
            w = 980f,
            h = 460f,
            corner = 72f,
            fill = Fill.CRYSTAL,
            glass = Glass(blur = 28f, refraction = 44f, bevelWidth = 24f, frostAmount = 0.24f, tint = 0x28FFFFFF, rim = 0.9f)
        ),
        LayerNode(
            name = "Bloco Relógio Widget",
            type = LayerType.STACK,
            dir = Dir.VERTICAL,
            x = 0.5f,
            y = 0.5f,
            anchor = Anchor.CENTER,
            align = Align.CENTER,
            spacing = 8f,
            children = listOf(
                LayerNode(
                    name = "Hora Elástica",
                    text = "\$df(HH)\$:\$df(mm)\$",
                    size = 215f,
                    weight = 700,
                    font = "sans-serif-condensed",
                    stretch = 1.65f,
                    fill = Fill.CRYSTAL,
                    glass = Glass(blur = 14f, refraction = 54f, bevelWidth = 18f, frostAmount = 0.12f, tint = 0x44FFFFFF, rim = 0.95f)
                ),
                LayerNode(
                    name = "Data & Clima",
                    text = "\$tc(cap, df(EEE))\$, \$df(dd)\$ \$df(MMM)\$ · \$wi(temp)\$° · \$bi(level)\$%",
                    size = 38f,
                    weight = 500,
                    fill = Fill.SOLID,
                    color = 0xEEFFFFFF.toInt()
                )
            )
        )
    )

    fun widgetWeatherBatteryCard(): List<LayerNode> = listOf(
        LayerNode(
            name = "Card Vidro Fosco",
            type = LayerType.SHAPE,
            shape = ShapeKind.RECT,
            x = 0.5f,
            y = 0.5f,
            anchor = Anchor.CENTER,
            w = 980f,
            h = 460f,
            corner = 68f,
            fill = Fill.GLASS,
            glass = Glass(blur = 32f, refraction = 28f, bevelWidth = 18f, frostAmount = 0.55f, tint = 0x36FFFFFF, rim = 0.85f)
        ),
        LayerNode(
            name = "Linha Clima & Bateria",
            type = LayerType.STACK,
            dir = Dir.HORIZONTAL,
            x = 0.5f,
            y = 0.5f,
            anchor = Anchor.CENTER,
            align = Align.CENTER,
            spacing = 54f,
            children = listOf(
                LayerNode(
                    name = "Ícone Dinâmico",
                    type = LayerType.ICON,
                    icon = "weather",
                    fxAsset = "\$if(bi(charging), \"bolt\", \"weather\")\$",
                    w = 132f,
                    h = 132f,
                    fill = Fill.SOLID,
                    color = 0xFFFFFFFF.toInt()
                ),
                LayerNode(
                    name = "Clima Agora",
                    type = LayerType.STACK,
                    dir = Dir.VERTICAL,
                    align = Align.START,
                    spacing = 8f,
                    children = listOf(
                        LayerNode(name = "Temp", text = "\$wi(temp)\$°", size = 128f, weight = 700, align = Align.START),
                        LayerNode(name = "Resumo", text = "\$wi(cond)\$ · \$wi(city)\$", size = 34f, weight = 500, align = Align.START, color = 0xCCFFFFFF.toInt())
                    )
                ),
                LayerNode(
                    name = "Anel Bateria",
                    type = LayerType.SHAPE,
                    shape = ShapeKind.RING,
                    w = 136f,
                    h = 136f,
                    strokeWidth = 14f,
                    value = "\$bi(level)\$",
                    fxColor = "\$if(bi(level) <= 20, \"#FFFF5252\", if(bi(charging), \"#FF80FFB5\", \"#FFFFFFFF\"))\$"
                )
            )
        )
    )

    fun widgetMusicSmartCard(): List<LayerNode> = listOf(
        LayerNode(
            name = "Capsula Mídia Vidro",
            type = LayerType.SHAPE,
            shape = ShapeKind.RECT,
            x = 0.5f,
            y = 0.5f,
            anchor = Anchor.CENTER,
            w = 980f,
            h = 420f,
            corner = 72f,
            fill = Fill.CRYSTAL,
            glass = Glass(blur = 28f, refraction = 38f, bevelWidth = 22f, frostAmount = 0.35f, tint = 0x30101420, rim = 0.9f),
            touch = TouchAction(type = TouchActionType.MEDIA_PLAY_PAUSE)
        ),
        LayerNode(
            name = "Conteúdo Mídia",
            type = LayerType.STACK,
            dir = Dir.HORIZONTAL,
            x = 0.5f,
            y = 0.5f,
            anchor = Anchor.CENTER,
            align = Align.CENTER,
            spacing = 40f,
            children = listOf(
                LayerNode(
                    name = "Ícone Mídia",
                    type = LayerType.ICON,
                    icon = "music",
                    fxAsset = "\$if(si(mplaying), \"music\", \"weather\")\$",
                    w = 120f,
                    h = 120f
                ),
                LayerNode(
                    name = "Faixa & Status",
                    type = LayerType.STACK,
                    dir = Dir.VERTICAL,
                    align = Align.START,
                    spacing = 8f,
                    children = listOf(
                        LayerNode(
                            name = "Título",
                            text = "\$if(si(mplaying), \"Tocando Agora\", df(HH) + \":\" + df(mm))\$",
                            size = 66f,
                            weight = 700,
                            align = Align.START
                        ),
                        LayerNode(
                            name = "Subtítulo",
                            text = "\$if(si(mplaying), \"Toque para Pausar/Play\", tc(cap, df(EEEE)) + \" · \" + wi(temp) + \"°\")\$",
                            size = 34f,
                            weight = 400,
                            align = Align.START,
                            color = 0xCCFFFFFF.toInt()
                        )
                    )
                )
            )
        )
    )

    val all: List<Pair<String, () -> List<LayerNode>>> = listOf(
        "Xiaomi Canto Clássico" to ::xiaomi,
        "iOS Vidro 3D" to ::iosGlass,
        "Samsung Esticado" to ::samsung,
        "Huawei Editorial" to ::huawei
    )

    val allWidgets: List<Pair<String, () -> List<LayerNode>>> = listOf(
        "Relógio Cristal Elástico" to ::widgetElasticClock,
        "Card Clima & Bateria Vidro" to ::widgetWeatherBatteryCard,
        "Card Mídia & Status Cristal" to ::widgetMusicSmartCard
    )
}
