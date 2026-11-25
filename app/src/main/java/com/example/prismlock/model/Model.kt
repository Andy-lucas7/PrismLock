package com.example.prismlock.model

import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.roundToInt

@Serializable enum class LayerType { TEXT, STACK, SHAPE, ICON, SVG, IMAGE, DEPTH }
@Serializable enum class Cond { ALWAYS, LOCK_ONLY, HOME_ONLY, CHARGING, NOT_CHARGING, LOW_BATTERY, DAY, NIGHT }
@Serializable enum class Blend { NORMAL, MULTIPLY, SCREEN, OVERLAY, DARKEN, LIGHTEN, COLOR_DODGE, COLOR_BURN, SOFT_LIGHT, HARD_LIGHT, DIFFERENCE, EXCLUSION, PLUS, HUE, SATURATION, COLOR, LUMINOSITY }
@Serializable enum class Fill { SOLID, GRADIENT, ADAPTIVE, GLASS, CRYSTAL, HOLLOW }
@Serializable enum class GradientKind { LINEAR, RADIAL, SWEEP }
@Serializable enum class Anchor { TOP_LEFT, TOP_CENTER, TOP_RIGHT, CENTER_LEFT, CENTER, CENTER_RIGHT, BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT }
@Serializable enum class ShapeKind { RECT, CIRCLE, LINE, BAR, RING }
@Serializable enum class Dir { VERTICAL, HORIZONTAL }
@Serializable enum class Align { START, CENTER, END }
@Serializable enum class MotionAxes { BOTH, HORIZONTAL, VERTICAL }
@Serializable enum class LayerMotion { FOLLOW_PLANE, STILL, CUSTOM }

@Serializable
data class MotionScene(
    val enabled: Boolean = false,
    val intensity: Float = 1f,
    val backgroundDepth: Float = 1f,
    val itemsDepth: Float = 0f,
    val subjectDepth: Float = -0.35f,
    val axes: MotionAxes = MotionAxes.BOTH,
    val invert: Boolean = false,
    val homeScreen: Boolean = true
) {
    fun maxShiftPx(screenWidthPx: Int): Float =
        48f * (screenWidthPx.coerceAtLeast(1) / 1080f) * intensity.coerceIn(0f, 2f)
}

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
    val fxAsset: String = "",
    val motion: LayerMotion = LayerMotion.FOLLOW_PLANE,
    val motionDepth: Float = 0f
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

const val WIDGET_ASPECT_MIN = 0.6f
const val WIDGET_ASPECT_MAX = 4.5f

@Serializable
data class Project(
    val id: String = "default",
    val title: String = "Aurora Líquida",
    val layers: List<LayerNode> = Presets.liquidAurora(),
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
    val lat: Double? = null,
    val lon: Double? = null,
    val dim: Float = 0.12f,
    val globals: List<GlobalVar> = emptyList(),
    val isWidget: Boolean = false,
    val widgetAspect: Float = 2.0f,
    val customAssets: Map<String, String> = emptyMap(),
    val motion: MotionScene = MotionScene()
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

fun List<LayerNode>.rootIndexOf(id: String?): Int {
    if (id == null) return -1
    for (i in indices) {
        val root = this[i]
        if (root.id == id || root.children.find(id) != null) return i
    }
    return -1
}

fun List<LayerNode>.isAboveSubject(id: String?): Boolean {
    if (id == null) return false
    val depthIdx = indexOfFirst { it.type == LayerType.DEPTH }
    if (depthIdx < 0) return false
    val rootIdx = rootIndexOf(id)
    return rootIdx > depthIdx
}

fun List<LayerNode>.setAboveSubject(id: String, above: Boolean): List<LayerNode> {
    val depthIdx = indexOfFirst { it.type == LayerType.DEPTH }
    val rootIdx = rootIndexOf(id)
    if (depthIdx < 0 || rootIdx < 0 || rootIdx == depthIdx) return this
    val alreadyAbove = rootIdx > depthIdx
    if (alreadyAbove == above) return this
    val mutable = toMutableList()
    val item = mutable.removeAt(rootIdx)
    val newDepthIdx = mutable.indexOfFirst { it.type == LayerType.DEPTH }
    val targetIdx = if (above) (newDepthIdx + 1).coerceAtMost(mutable.size) else newDepthIdx.coerceAtLeast(0)
    mutable.add(targetIdx, item)
    return mutable
}

private fun LayerNode.stripNestedDepth(): LayerNode {
    if (children.isEmpty()) return this
    var changed = false
    val out = ArrayList<LayerNode>(children.size)
    for (child in children) {
        if (child.type == LayerType.DEPTH) {
            changed = true
            continue
        }
        val cleaned = child.stripNestedDepth()
        if (cleaned !== child) changed = true
        out.add(cleaned)
    }
    return if (changed) copy(children = out) else this
}

fun List<LayerNode>.ensureSingleRootDepth(): List<LayerNode> {
    var seenDepth = false
    var changed = false
    val out = ArrayList<LayerNode>(size)
    for (layer in this) {
        if (layer.type == LayerType.DEPTH) {
            if (seenDepth) {
                changed = true
                continue
            }
            seenDepth = true
            val cleaned = if (layer.children.isNotEmpty()) {
                changed = true
                layer.copy(children = emptyList())
            } else layer
            out.add(cleaned)
        } else {
            val cleaned = layer.stripNestedDepth()
            if (cleaned !== layer) changed = true
            out.add(cleaned)
        }
    }
    return if (changed) out else this
}

fun LayerNode.migrated(): LayerNode {
    val w = axes["wght"]
    val cleaned = if (w != null) {
        copy(
            weight = if (w.isFinite()) w.roundToInt().coerceIn(1, 1000) else weight,
            axes = axes - "wght"
        )
    } else {
        this
    }
    if (cleaned.children.isEmpty()) return cleaned
    var changed = false
    val migratedChildren = cleaned.children.map { child ->
        val m = child.migrated()
        if (m !== child) changed = true
        m
    }
    return if (changed) cleaned.copy(children = migratedChildren) else cleaned
}

fun Project.migrated(): Project {
    val phantomSp = lat == -23.55 && lon == -46.63 && city.isBlank()
    var changed = phantomSp
    val migratedLayers = if (layers.isEmpty()) layers else layers.map { layer ->
        val m = layer.migrated()
        if (m !== layer) changed = true
        m
    }
    val deduped = migratedLayers.ensureSingleRootDepth()
    if (deduped !== migratedLayers) changed = true
    if (!changed) return this
    return copy(
        layers = deduped,
        lat = if (phantomSp) null else lat,
        lon = if (phantomSp) null else lon
    )
}

object Presets {
    private const val INTER = "res:inter"
    private const val BEBAS = "res:bebas_neue"
    private const val JAKARTA = "res:plus_jakarta_sans"
    private const val PLAYFAIR = "res:playfair_display"
    private const val MONO = "res:jetbrains_mono"
    private const val OUTFIT = "res:outfit"
    private const val SYNE = "res:syne"
    private const val GROTESK = "res:space_grotesk"

    private fun t(
        name: String,
        text: String,
        size: Float,
        weight: Int = 400,
        align: Align = Align.START,
        font: String = INTER,
        ls: Float = 0f,
        color: Int = 0xFFFFFFFF.toInt()
    ) = LayerNode(name = name, text = text, size = size, weight = weight, align = align, font = font, letterSpacing = ls, color = color)

    private fun softShadow(alpha: Int = 0x55) = alpha shl 24

    // ───────────────────────────── Telas (tela de bloqueio) ─────────────────────────────

    /**
     * Vidro · Aurora — vitrine do motor: relógio alto em Cristal Óptico elástico refratando a foto,
     * sujeito 3D na frente e uma linha discreta de status. Público: quem quer o efeito "uau".
     */
    fun liquidAurora(): List<LayerNode> = listOf(
        LayerNode(name = "Dia", text = "{EEEE_UPPER}", size = 30f, weight = 600, font = INTER, x = 0.5f, y = 0.075f,
            letterSpacing = 0.38f, cond = Cond.LOCK_ONLY, color = 0xE6FFFFFF.toInt()),
        LayerNode(
            name = "Hora cristal", text = "{H}:{mm}", size = 320f, weight = 700, font = OUTFIT, x = 0.5f, y = 0.1f,
            fill = Fill.CRYSTAL, cond = Cond.LOCK_ONLY, stretch = 1.25f,
            glass = Glass(blur = 20f, refraction = 50f, bevelWidth = 24f, frostAmount = 0.14f, tint = 0x2EFFFFFF, rim = 0.95f)
        ),
        LayerNode(name = "Sujeito 3D", type = LayerType.DEPTH),
        LayerNode(name = "Status", type = LayerType.STACK, dir = Dir.HORIZONTAL, x = 0.5f, y = 0.335f, align = Align.CENTER,
            spacing = 22f, cond = Cond.LOCK_ONLY, children = listOf(
                LayerNode(name = "Clima", type = LayerType.ICON, icon = "weather", w = 54f, h = 54f),
                t("Info", "{temp}°  ·  {d} {MMM}  ·  {bat}%", 36f, 500, font = INTER, ls = 0.04f, color = 0xE6FFFFFF.toInt())
            ))
    )

    /**
     * Moderno · Profundidade — o visual de relógio-atrás-do-sujeito que domina o Pinterest: Bebas Neue
     * gigante em tom areia, o sujeito da foto sobreposto. Público: quem usa fotos de pessoas/pets/objetos.
     */
    fun modernDepth(): List<LayerNode> = listOf(
        LayerNode(name = "Data", text = "{EEE_UPPER}  {d}  {MMM_UPPER}", size = 34f, weight = 400, font = BEBAS, x = 0.5f, y = 0.07f,
            letterSpacing = 0.3f, cond = Cond.LOCK_ONLY, color = 0xFFF1E4D3.toInt()),
        LayerNode(name = "Hora", text = "{HH}:{mm}", size = 380f, weight = 400, font = BEBAS, x = 0.5f, y = 0.085f,
            stretch = 1.35f, letterSpacing = -0.01f, cond = Cond.LOCK_ONLY, color = 0xFFF1E4D3.toInt(),
            shadowRadius = 30f, shadowColor = softShadow(0x40), shadowDy = 8f),
        LayerNode(name = "Sujeito 3D", type = LayerType.DEPTH)
    )

    /**
     * Clean · Névoa — tipografia fina, muito respiro, nada de efeito. Público: minimalistas,
     * quem quer a foto em destaque e um relógio elegante.
     */
    fun cleanMist(): List<LayerNode> = listOf(
        LayerNode(name = "Bloco", type = LayerType.STACK, x = 0.5f, y = 0.1f, align = Align.CENTER, spacing = 4f, cond = Cond.LOCK_ONLY, children = listOf(
            t("Data", "{EEEE}, {d} de {MMMM}", 38f, 500, align = Align.CENTER, font = INTER, ls = 0.02f, color = 0xD9FFFFFF.toInt()),
            t("Hora", "{HH}:{mm}", 230f, 200, align = Align.CENTER, font = INTER, ls = -0.03f)
        )),
        LayerNode(name = "Sujeito 3D", type = LayerType.DEPTH)
    )

    /**
     * Fofo · Pêssego — tons pastel, cantos redondos e "adesivos": relógio arredondado em degradê
     * pêssego→rosa, chips com coração e bateria. Público: estética soft/cute.
     */
    fun cutePeach(): List<LayerNode> = listOf(
        LayerNode(name = "Hora", text = "{HH}:{mm}", size = 250f, weight = 800, font = JAKARTA, x = 0.5f, y = 0.1f, cond = Cond.LOCK_ONLY,
            fill = Fill.GRADIENT, color = 0xFFFFD6A5.toInt(), color2 = 0xFFFFADC7.toInt(), gradientAngle = 90f,
            shadowRadius = 26f, shadowColor = 0x66FF8FB1, shadowDy = 6f),
        LayerNode(name = "Sujeito 3D", type = LayerType.DEPTH),
        LayerNode(name = "Chip data", type = LayerType.STACK, dir = Dir.HORIZONTAL, x = 0.5f, y = 0.29f, align = Align.CENTER, spacing = 14f,
            cond = Cond.LOCK_ONLY, children = listOf(
                LayerNode(name = "Coração", type = LayerType.ICON, icon = "heart", w = 46f, h = 46f, color = 0xFFFF8FB1.toInt()),
                t("Data", "{EEEE}, {d} de {MMMM}", 38f, 700, font = JAKARTA, color = 0xFFFFF0F5.toInt()),
                LayerNode(name = "Brilho", type = LayerType.ICON, icon = "sparkle", w = 40f, h = 40f, color = 0xFFFFD6A5.toInt())
            )),
        LayerNode(name = "Pílula bateria", type = LayerType.SHAPE, shape = ShapeKind.RECT, x = 0.5f, y = 0.9f, anchor = Anchor.CENTER,
            w = 300f, h = 96f, corner = 48f, fill = Fill.SOLID, color = 0xCCFFE4EC.toInt(), cond = Cond.LOCK_ONLY),
        LayerNode(name = "Bateria", type = LayerType.STACK, dir = Dir.HORIZONTAL, x = 0.5f, y = 0.9f, anchor = Anchor.CENTER, align = Align.CENTER,
            spacing = 10f, cond = Cond.LOCK_ONLY, children = listOf(
                LayerNode(name = "Ícone", type = LayerType.ICON, icon = "battery", w = 40f, h = 40f, color = 0xFFE5708F.toInt(),
                    fxAsset = "\$if(bi(charging), \"battery_charging\", \"battery\")\$"),
                t("Nível", "{bat}%", 38f, 800, font = JAKARTA, color = 0xFFE5708F.toInt())
            ))
    )

    /**
     * Elegante · Editorial — serifada de revista em creme, regra fina e caixa-alta espaçada.
     * Público: estética "old money"/aesthetic, fotos de paisagem e moda.
     */
    fun elegantEditorial(): List<LayerNode> = listOf(
        LayerNode(name = "Editorial", type = LayerType.STACK, x = 0.5f, y = 0.09f, align = Align.CENTER, spacing = 8f, cond = Cond.LOCK_ONLY, children = listOf(
            t("Dia", "{EEEE_UPPER}", 26f, 600, align = Align.CENTER, font = INTER, ls = 0.5f, color = 0xE6F6EFE6.toInt()),
            t("Hora", "{HH}:{mm}", 210f, 400, align = Align.CENTER, font = PLAYFAIR, color = 0xFFF6EFE6.toInt()),
            LayerNode(name = "Linha", type = LayerType.SHAPE, shape = ShapeKind.LINE, w = 180f, h = 2f, color = 0xB3F6EFE6.toInt()),
            t("Data", "{d} de {MMMM}", 40f, 400, align = Align.CENTER, font = PLAYFAIR, color = 0xFFF6EFE6.toInt())
        )),
        LayerNode(name = "Sujeito 3D", type = LayerType.DEPTH)
    )

    /**
     * Avançado · Terminal — painel de informações em monoespaçada sobre vidro escuro: bateria,
     * progresso do dia, clima e alarme com barras. Público: power users, quem gosta de dados.
     */
    fun advancedTerminal(): List<LayerNode> = listOf(
        LayerNode(name = "Painel", type = LayerType.SHAPE, shape = ShapeKind.RECT, x = 0.5f, y = 0.2f, anchor = Anchor.CENTER,
            w = 940f, h = 620f, corner = 44f, fill = Fill.GLASS, cond = Cond.LOCK_ONLY,
            glass = Glass(blur = 36f, refraction = 18f, bevelWidth = 12f, frostAmount = 0.7f, tint = 0x66080B10, rim = 0.6f)),
        LayerNode(name = "Dados", type = LayerType.STACK, x = 0.5f, y = 0.2f, anchor = Anchor.CENTER, align = Align.START, spacing = 14f,
            cond = Cond.LOCK_ONLY, children = listOf(
                t("Hora", "{HH}:{mm}", 170f, 700, font = MONO, ls = -0.02f, color = 0xFF7CFFB2.toInt()),
                t("Data", "> {EEE_LOWER} {dd}.{MM}.{yyyy}", 34f, 400, font = MONO, color = 0xCC7CFFB2.toInt()),
                t("Bateria", "bat   {bat}% {charging}", 32f, 400, font = MONO, color = 0xE6FFFFFF.toInt()),
                LayerNode(name = "Barra bateria", type = LayerType.SHAPE, shape = ShapeKind.BAR, w = 760f, h = 10f, corner = 5f, value = "bat",
                    color = 0xFF7CFFB2.toInt(), trackColor = 0x33FFFFFF,
                    fxColor = "\$if(bi(level) <= 20, \"#FFFF6B6B\", \"#FF7CFFB2\")\$"),
                t("Dia", "dia   {dayprog}%", 32f, 400, font = MONO, color = 0xE6FFFFFF.toInt()),
                LayerNode(name = "Barra dia", type = LayerType.SHAPE, shape = ShapeKind.BAR, w = 760f, h = 10f, corner = 5f, value = "dayprog",
                    color = 0xFF63D2FF.toInt(), trackColor = 0x33FFFFFF),
                t("Clima", "tmp   {temp}° {cond}", 32f, 400, font = MONO, color = 0xE6FFFFFF.toInt()),
                t("Alarme", "alm   {alarm}", 32f, 400, font = MONO, color = 0xE6FFFFFF.toInt())
            ))
    )

    /** Simples · Canto — relógio pequeno no canto inferior, a foto inteira em evidência. Público: quem quer só o essencial. */
    fun simpleCorner(): List<LayerNode> = listOf(
        LayerNode(name = "Bloco", type = LayerType.STACK, x = 0.08f, y = 0.74f, align = Align.START, spacing = 2f, cond = Cond.LOCK_ONLY, children = listOf(
            t("Hora", "{HH}:{mm}", 150f, 300, font = OUTFIT, ls = -0.02f),
            t("Data", "{EEEE}, {d} {MMM}  ·  {temp}°", 36f, 500, font = OUTFIT, color = 0xD9FFFFFF.toInt())
        ))
    )

    /**
     * Ousado · Ácido — display Syne pesado em degradê lima→violeta, estética Y2K/streetwear.
     * Público: quem quer cor e atitude.
     */
    fun boldAcid(): List<LayerNode> = listOf(
        LayerNode(name = "Horas", text = "{HH}", size = 330f, weight = 800, font = SYNE, x = 0.08f, y = 0.07f, align = Align.START,
            letterSpacing = -0.05f, cond = Cond.LOCK_ONLY, fill = Fill.GRADIENT, color = 0xFFD4FF3F.toInt(), color2 = 0xFFA78BFA.toInt(),
            gradientAngle = 0f),
        LayerNode(name = "Minutos", text = "{mm}", size = 330f, weight = 800, font = SYNE, x = 0.08f, y = 0.215f, align = Align.START,
            letterSpacing = -0.05f, cond = Cond.LOCK_ONLY, fill = Fill.GRADIENT, color = 0xFFA78BFA.toInt(), color2 = 0xFFD4FF3F.toInt(),
            gradientAngle = 0f),
        LayerNode(name = "Sujeito 3D", type = LayerType.DEPTH),
        LayerNode(name = "Etiqueta", text = "{EEEE_UPPER} / {dd}.{MM}", size = 30f, weight = 500, font = GROTESK, x = 0.08f, y = 0.375f,
            align = Align.START, letterSpacing = 0.12f, cond = Cond.LOCK_ONLY, color = 0xFFD4FF3F.toInt())
    )

    // ───────────────────────────────────── Widgets ─────────────────────────────────────

    /** Moderno · Cristal Elástico — relógio esticado em cristal óptico sobre placa de vidro. */
    fun widgetElasticClock(): List<LayerNode> = listOf(
        LayerNode(name = "Base Cristal", type = LayerType.SHAPE, shape = ShapeKind.RECT, x = 0.5f, y = 0.5f, anchor = Anchor.CENTER,
            w = 980f, h = 460f, corner = 72f, fill = Fill.CRYSTAL,
            glass = Glass(blur = 28f, refraction = 44f, bevelWidth = 24f, frostAmount = 0.24f, tint = 0x28FFFFFF, rim = 0.9f)),
        LayerNode(name = "Bloco Relógio", type = LayerType.STACK, dir = Dir.VERTICAL, x = 0.5f, y = 0.5f, anchor = Anchor.CENTER,
            align = Align.CENTER, spacing = 8f, children = listOf(
                LayerNode(name = "Hora Elástica", text = "\$df(HH)\$:\$df(mm)\$", size = 215f, weight = 700, font = OUTFIT, stretch = 1.6f,
                    fill = Fill.CRYSTAL,
                    glass = Glass(blur = 14f, refraction = 54f, bevelWidth = 18f, frostAmount = 0.12f, tint = 0x44FFFFFF, rim = 0.95f)),
                LayerNode(name = "Data & Clima", text = "\$tc(cap, df(EEE))\$, \$df(dd)\$ \$df(MMM)\$ · \$wi(temp)\$° · \$bi(level)\$%",
                    size = 38f, weight = 500, font = INTER, color = 0xEEFFFFFF.toInt())
            ))
    )

    /** Clean · Relógio — pílula de vidro fosco claro com hora fina e data. */
    fun widgetCleanClock(): List<LayerNode> = listOf(
        LayerNode(name = "Pílula", type = LayerType.SHAPE, shape = ShapeKind.RECT, x = 0.5f, y = 0.5f, anchor = Anchor.CENTER,
            w = 980f, h = 440f, corner = 220f, fill = Fill.GLASS,
            glass = Glass(blur = 34f, refraction = 16f, bevelWidth = 10f, frostAmount = 0.75f, tint = 0x40FFFFFF, rim = 0.5f)),
        LayerNode(name = "Linha", type = LayerType.STACK, dir = Dir.HORIZONTAL, x = 0.5f, y = 0.5f, anchor = Anchor.CENTER,
            align = Align.CENTER, spacing = 36f, children = listOf(
                t("Hora", "{HH}:{mm}", 190f, 200, font = INTER, ls = -0.03f),
                LayerNode(name = "Data", type = LayerType.STACK, align = Align.START, spacing = 2f, children = listOf(
                    t("Dia", "{EEEE}", 46f, 600, font = INTER),
                    t("Mês", "{d} de {MMMM}", 36f, 400, font = INTER, color = 0xCCFFFFFF.toInt())
                ))
            ))
    )

    /** Fofo · Energia — cartão pastel com coração, anel de bateria rosa e estado de carga. */
    fun widgetCuteBattery(): List<LayerNode> = listOf(
        LayerNode(name = "Cartão", type = LayerType.SHAPE, shape = ShapeKind.RECT, x = 0.5f, y = 0.5f, anchor = Anchor.CENTER,
            w = 980f, h = 460f, corner = 120f, fill = Fill.GRADIENT, color = 0xFFFFE4EC.toInt(), color2 = 0xFFFFF1DC.toInt(), gradientAngle = 135f),
        LayerNode(name = "Conteúdo", type = LayerType.STACK, dir = Dir.HORIZONTAL, x = 0.5f, y = 0.5f, anchor = Anchor.CENTER,
            align = Align.CENTER, spacing = 48f, children = listOf(
                LayerNode(name = "Anel", type = LayerType.SHAPE, shape = ShapeKind.RING, w = 220f, h = 220f, strokeWidth = 26f, value = "bat",
                    color = 0xFFFF8FB1.toInt(), trackColor = 0x33E5708F,
                    fxColor = "\$if(bi(charging), \"#FF7ED6A8\", \"#FFFF8FB1\")\$"),
                LayerNode(name = "Texto", type = LayerType.STACK, align = Align.START, spacing = 4f, children = listOf(
                    t("Nível", "{bat}%", 110f, 800, font = JAKARTA, color = 0xFFE5708F.toInt()),
                    t("Estado", "\$if(bi(charging), \"carregando ♡\", \"energia do dia ♡\")\$", 38f, 700, font = JAKARTA, color = 0xFFB98AA0.toInt())
                ))
            ))
    )

    /** Avançado · Painel — monoespaçada com bateria, dia, clima e alarme em barras. */
    fun widgetAdvancedPanel(): List<LayerNode> = listOf(
        LayerNode(name = "Painel", type = LayerType.SHAPE, shape = ShapeKind.RECT, x = 0.5f, y = 0.5f, anchor = Anchor.CENTER,
            w = 980f, h = 460f, corner = 48f, fill = Fill.SOLID, color = 0xE60B0F14.toInt()),
        LayerNode(name = "Dados", type = LayerType.STACK, x = 0.5f, y = 0.5f, anchor = Anchor.CENTER, align = Align.START, spacing = 12f, children = listOf(
            t("Topo", "{HH}:{mm}  {EEE_LOWER} {dd}.{MM}", 64f, 700, font = MONO, color = 0xFF7CFFB2.toInt()),
            t("Bateria", "bat {bat}% {charging}", 32f, 400, font = MONO, color = 0xE6FFFFFF.toInt()),
            LayerNode(name = "Barra bateria", type = LayerType.SHAPE, shape = ShapeKind.BAR, w = 820f, h = 10f, corner = 5f, value = "bat",
                color = 0xFF7CFFB2.toInt(), trackColor = 0x33FFFFFF),
            t("Dia", "dia {dayprog}%  ·  tmp {temp}°  ·  alm {alarm}", 32f, 400, font = MONO, color = 0xE6FFFFFF.toInt()),
            LayerNode(name = "Barra dia", type = LayerType.SHAPE, shape = ShapeKind.BAR, w = 820f, h = 10f, corner = 5f, value = "dayprog",
                color = 0xFF63D2FF.toInt(), trackColor = 0x33FFFFFF)
        ))
    )

    /** Música · Cápsula — toque para tocar/pausar; ícone muda com o estado da mídia. */
    fun widgetMusicSmartCard(): List<LayerNode> = listOf(
        LayerNode(name = "Cápsula", type = LayerType.SHAPE, shape = ShapeKind.RECT, x = 0.5f, y = 0.5f, anchor = Anchor.CENTER,
            w = 980f, h = 420f, corner = 210f, fill = Fill.CRYSTAL,
            glass = Glass(blur = 28f, refraction = 38f, bevelWidth = 22f, frostAmount = 0.35f, tint = 0x30101420, rim = 0.9f),
            touch = TouchAction(type = TouchActionType.MEDIA_PLAY_PAUSE)),
        LayerNode(name = "Conteúdo", type = LayerType.STACK, dir = Dir.HORIZONTAL, x = 0.5f, y = 0.5f, anchor = Anchor.CENTER,
            align = Align.CENTER, spacing = 40f, children = listOf(
                LayerNode(name = "Ícone", type = LayerType.ICON, icon = "play", w = 120f, h = 120f,
                    fxAsset = "\$if(si(mplaying), \"pause\", \"play\")\$"),
                LayerNode(name = "Faixa", type = LayerType.STACK, align = Align.START, spacing = 8f, children = listOf(
                    LayerNode(name = "Título", text = "\$if(si(mplaying), \"Tocando agora\", df(HH) + \":\" + df(mm))\$",
                        size = 66f, weight = 700, font = GROTESK, align = Align.START),
                    LayerNode(name = "Subtítulo", text = "\$if(si(mplaying), \"Toque para pausar\", \"Toque para tocar\")\$",
                        size = 34f, weight = 400, font = GROTESK, align = Align.START, color = 0xCCFFFFFF.toInt())
                ))
            ))
    )

    /** Elegante · Calendário — dia em serifada grande, mês e semana em caixa-alta espaçada. */
    fun widgetElegantCalendar(): List<LayerNode> = listOf(
        LayerNode(name = "Placa", type = LayerType.SHAPE, shape = ShapeKind.RECT, x = 0.5f, y = 0.5f, anchor = Anchor.CENTER,
            w = 980f, h = 460f, corner = 56f, fill = Fill.SOLID, color = 0xF2F6EFE6.toInt(),
            touch = TouchAction(type = TouchActionType.SYSTEM_ALARM)),
        LayerNode(name = "Linha", type = LayerType.STACK, dir = Dir.HORIZONTAL, x = 0.5f, y = 0.5f, anchor = Anchor.CENTER,
            align = Align.CENTER, spacing = 44f, children = listOf(
                t("Dia", "{dd}", 230f, 400, font = PLAYFAIR, color = 0xFF2B2622.toInt()),
                LayerNode(name = "Detalhes", type = LayerType.STACK, align = Align.START, spacing = 8f, children = listOf(
                    t("Semana", "{EEEE_UPPER}", 34f, 600, font = INTER, ls = 0.3f, color = 0xFF2B2622.toInt()),
                    t("Mês", "{MMMM} {yyyy}", 44f, 400, font = PLAYFAIR, color = 0xFF5C5148.toInt()),
                    t("Alarme", "alarme {alarm}", 30f, 500, font = INTER, color = 0xFF8C7E72.toInt())
                ))
            ))
    )

    val all: List<Pair<String, () -> List<LayerNode>>> = listOf(
        "Vidro · Aurora" to ::liquidAurora,
        "Moderno · Profundidade" to ::modernDepth,
        "Clean · Névoa" to ::cleanMist,
        "Fofo · Pêssego" to ::cutePeach,
        "Elegante · Editorial" to ::elegantEditorial,
        "Avançado · Terminal" to ::advancedTerminal,
        "Simples · Canto" to ::simpleCorner,
        "Ousado · Ácido" to ::boldAcid
    )

    val allWidgets: List<Pair<String, () -> List<LayerNode>>> = listOf(
        "Clean · Relógio" to ::widgetCleanClock,
        "Moderno · Cristal Elástico" to ::widgetElasticClock,
        "Fofo · Energia" to ::widgetCuteBattery,
        "Avançado · Painel" to ::widgetAdvancedPanel,
        "Música · Cápsula" to ::widgetMusicSmartCard,
        "Elegante · Calendário" to ::widgetElegantCalendar
    )
}
