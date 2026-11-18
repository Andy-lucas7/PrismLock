package com.example.prismlock.engine

import android.content.Context
import android.graphics.Path
import android.net.Uri
import android.util.Xml
import androidx.core.graphics.PathParser
import org.xmlpull.v1.XmlPullParser

object Icons {
    private const val CLOUD = "M6.5,19h11a4.5,4.5 0 0,0 0.5,-8.97A6,6 0 0,0 6.34,9.03A5,5 0 0,0 6.5,19Z"
    private const val CLOUD_UP = "M6.5,15h11a4,4 0 0,0 0.4,-7.98A5.5,5.5 0 0,0 7.1,6.5A4.3,4.3 0 0,0 6.5,15Z"
    val builtIn: Map<String, String> = linkedMapOf(
        // Clima Completo
        "sun" to "M8,12a4,4 0 1,0 8,0a4,4 0 1,0 -8,0Z M11,1h2v3h-2z M11,20h2v3h-2z M1,11h3v2h-3z M20,11h3v2h-3z M4.2,5.6l1.4,-1.4l2.1,2.1l-1.4,1.4z M16.3,17.7l1.4,-1.4l2.1,2.1l-1.4,1.4z M4.2,18.4l2.1,-2.1l1.4,1.4l-2.1,2.1z M16.3,6.3l2.1,-2.1l1.4,1.4l-2.1,2.1z",
        "moon" to "M12.5,2A10,10 0 1,0 22,13.5A7.5,7.5 0 0,1 12.5,2Z",
        "moon_crescent" to "M15,3A9,9 0 1,0 21,17.5A7,7 0 0,1 15,3Z",
        "cloud" to CLOUD,
        "cloud_sun" to "M3,7a4,4 0 1,0 8,0a4,4 0 1,0 -8,0Z M7,0.5h1v2h-1z M0.5,6.5h2v1h-2z $CLOUD",
        "cloud_moon" to "M15.5,3a4,4 0 1,0 4.5,6.2a3.2,3.2 0 0,1 -4.5,-6.2Z $CLOUD",
        "clouds" to "M9,11h8a3,3 0 0,0 0.2,-6A4,4 0 0,0 9.6,4.5A3.2,3.2 0 0,0 9,11Z $CLOUD",
        "rain" to "$CLOUD_UP M8,17h1.6l-1,4h-1.6z M12,17h1.6l-1,4h-1.6z M16,17h1.6l-1,4h-1.6z",
        "rain_heavy" to "$CLOUD_UP M7,16.5h1.5l-1.4,5.5h-1.5z M10.5,16.5h1.5l-1.4,5.5h-1.5z M14,16.5h1.5l-1.4,5.5h-1.5z M17.5,16.5h1.5l-1.4,5.5h-1.5z",
        "drizzle" to "$CLOUD_UP M8,17.5a1,1 0 1,0 2,0a1,1 0 1,0 -2,0Z M12,19a1,1 0 1,0 2,0a1,1 0 1,0 -2,0Z M16,17.5a1,1 0 1,0 2,0a1,1 0 1,0 -2,0Z",
        "snow" to "$CLOUD_UP M7,19a1.2,1.2 0 1,0 2.4,0a1.2,1.2 0 1,0 -2.4,0Z M11,21a1.2,1.2 0 1,0 2.4,0a1.2,1.2 0 1,0 -2.4,0Z M15,19a1.2,1.2 0 1,0 2.4,0a1.2,1.2 0 1,0 -2.4,0Z",
        "sleet" to "$CLOUD_UP M8,17h1.5l-1,4h-1.5z M11.5,19.5a1.2,1.2 0 1,0 2.4,0a1.2,1.2 0 1,0 -2.4,0Z M16,17h1.5l-1,4h-1.5z",
        "hail" to "$CLOUD_UP M7.5,18l1.2,-1.2l1.2,1.2l-1.2,1.2z M11.5,20.5l1.2,-1.2l1.2,1.2l-1.2,1.2z M15.5,18l1.2,-1.2l1.2,1.2l-1.2,1.2z",
        "storm" to "$CLOUD_UP M11,16h3.5l-2,3h2.5l-5,5l1.5,-4h-2.5z",
        "wind" to "M3,8h11a2.5,2.5 0 1,0 -2.5,-2.5h2a0.8,0.8 0 1,1 0.5,1.5H3V8Z M3,13h15a2.5,2.5 0 1,1 -2.5,2.5h2a0.8,0.8 0 1,0 0.5,-1.5H3V13Z M3,18h8a2,2 0 1,1 -2,2h1.6a0.6,0.6 0 1,0 0.4,-1H3V18Z",
        "fog" to "M3,7h18v2h-18z M5,11h14v2h-14z M3,15h18v2h-18z M6,19h12v2h-12z",
        "humidity" to "M12,2.5C12,2.5 5.5,10 5.5,14.5A6.5,6.5 0 1,0 18.5,14.5C18.5,10 12,2.5 12,2.5Z M9,15a3,3 0 0,0 3,3v1.5a4.5,4.5 0 0,1 -4.5,-4.5Z",
        "thermometer" to "M10,4a2,2 0 0,1 4,0v9.2a4,4 0 1,1 -4,0V4Z M11.2,5v9a2.2,2.2 0 1,0 1.6,0V5Z",
        "uv" to "M12,4a5,5 0 1,0 0,10a5,5 0 1,0 0,-10Z M11,1h2v2h-2z M4.5,4.5l1.4,-1.4l1.5,1.5l-1.4,1.4z M18.1,3.1l1.4,1.4l-1.5,1.5l-1.4,-1.4z M5,17h2v3a1,1 0 0,0 2,0v-3h2v3a3,3 0 0,1 -6,0z M13,17h2l1.5,3.5L18,17h2l-2.5,6h-2z",
        "sunrise" to "M12,4l-3,3h2v3h2V7h2Z M6,15a6,6 0 0,1 12,0Z M2,17h20v2h-20z M6,20h12v2h-12z",
        "sunset" to "M12,10l-3,-3h2V4h2v3h2Z M6,15a6,6 0 0,1 12,0Z M2,17h20v2h-20z M6,20h12v2h-12z",
        "tornado" to "M3,4h18v2.5H3Z M5,8.5h14v2.5H5Z M7,13h10v2.5H7Z M9,17.5h6v2H9Z M10.5,21h3v1.5h-3Z",
        "compass" to "M12,2a10,10 0 1,0 0,20a10,10 0 1,0 0,-20Z M12,4a8,8 0 1,1 0,16a8,8 0 1,1 0,-16Z M15.5,8.5l-2.2,5.8l-5.8,2.2l2.2,-5.8z",

        // Mídia
        "music" to "M9,3h11v12.5a3,3 0 1,1 -2,-2.83v-6.67h-7v9.5a3,3 0 1,1 -2,-2.83z",
        "play" to "M7,5v14l12,-7Z",
        "pause" to "M6,5h4v14H6Z M14,5h4v14h-4Z",
        "skip_next" to "M5,6v12l8.5,-6Z M16,6h2.5v12H16Z",
        "skip_prev" to "M19,6v12l-8.5,-6Z M5.5,6H8v12H5.5Z",
        "headphones" to "M12,3a8,8 0 0,0 -8,8v7a3,3 0 0,0 3,3h2v-7H6v-3a6,6 0 0,1 12,0v3h-3v7h2a3,3 0 0,0 3,-3v-7a8,8 0 0,0 -8,-8Z",
        "volume" to "M3,9v6h4l5,4V5L7,9H3Z M15.5,12a3.5,3.5 0 0,0 -2,-3.16v6.32a3.5,3.5 0 0,0 2,-3.16Z M13.5,5.2v2.1a6,6 0 0,1 0,9.4v2.1a8,8 0 0,0 0,-13.6Z",
        "mic" to "M12,2a3,3 0 0,0 -3,3v6a3,3 0 0,0 6,0V5a3,3 0 0,0 -3,-3Z M6,11h2a4,4 0 0,0 8,0h2a6,6 0 0,1 -5,5.91V20h3v2H8v-2h3v-3.09A6,6 0 0,1 6,11Z",
        "radio" to "M12,10a2,2 0 1,0 0,4a2,2 0 1,0 0,-4Z M8.5,8.5a5,5 0 0,0 0,7l1.4,-1.4a3,3 0 0,1 0,-4.2Z M15.5,8.5l-1.4,1.4a3,3 0 0,1 0,4.2l1.4,1.4a5,5 0 0,0 0,-7Z M5.7,5.7a9,9 0 0,0 0,12.6l1.4,-1.4a7,7 0 0,1 0,-9.8Z M18.3,5.7l-1.4,1.4a7,7 0 0,1 0,9.8l1.4,1.4a9,9 0 0,0 0,-12.6Z",

        // Sistema & Conectividade
        "battery" to "M9,2h6v2h2.5a1.5,1.5 0 0,1 1.5,1.5v15a1.5,1.5 0 0,1 -1.5,1.5h-11a1.5,1.5 0 0,1 -1.5,-1.5v-15a1.5,1.5 0 0,1 1.5,-1.5h2.5z M7,6v14h10v-14z",
        "battery_charging" to "M9,2h6v2h2.5a1.5,1.5 0 0,1 1.5,1.5v15a1.5,1.5 0 0,1 -1.5,1.5h-11a1.5,1.5 0 0,1 -1.5,-1.5v-15a1.5,1.5 0 0,1 1.5,-1.5h2.5z M7,6v14h10v-14z M13,7l-4,6h2.5l-0.5,5l4,-6h-2.5z",
        "bolt" to "M13,2L4,14h6l-1,8l9,-12h-6z",
        "alarm" to "M4,13a8,8 0 1,0 16,0a8,8 0 1,0 -16,0Z M6,13a6,6 0 1,1 12,0a6,6 0 1,1 -12,0Z M11,8h2v3h-2z M11,11h4v2h-4z M2,6l4,-4l1.5,1.5l-4,4z M22,6l-4,-4l-1.5,1.5l4,4z",
        "clock" to "M12,2a10,10 0 1,0 0,20a10,10 0 1,0 0,-20Z M12,4a8,8 0 1,1 0,16a8,8 0 1,1 0,-16Z M11,7h2v5.4l3.5,2.1l-1,1.7l-4.5,-2.7Z",
        "calendar" to "M5,4h2V2h2v2h6V2h2v2h2a2,2 0 0,1 2,2v14a2,2 0 0,1 -2,2H5a2,2 0 0,1 -2,-2V6a2,2 0 0,1 2,-2Z M5,10v10h14V10Z",
        "lock" to "M6,10h12a1,1 0 0,1 1,1v10a1,1 0 0,1 -1,1h-12a1,1 0 0,1 -1,-1v-10a1,1 0 0,1 1,-1z M8,10v-3a4,4 0 0,1 8,0v3h-2v-3a2,2 0 0,0 -4,0v3z",
        "unlock" to "M6,10h12a1,1 0 0,1 1,1v10a1,1 0 0,1 -1,1h-12a1,1 0 0,1 -1,-1v-10a1,1 0 0,1 1,-1z M8,10v-3a4,4 0 0,1 8,0h-2a2,2 0 0,0 -4,0v3z",
        "wifi" to "M12,18a2,2 0 1,0 0,4a2,2 0 1,0 0,-4Z M7.8,14.8a6,6 0 0,1 8.4,0l1.4,-1.4a8,8 0 0,0 -11.2,0Z M4.9,11.9a10,10 0 0,1 14.2,0l1.4,-1.4a12,12 0 0,0 -17,0Z M2.1,9.1a14,14 0 0,1 19.8,0l1.4,-1.4a16,16 0 0,0 -22.6,0Z",
        "bluetooth" to "M17.7,7.7L12,2h-1v7.6L6.4,5L5,6.4L10.6,12L5,17.6L6.4,19l4.6,-4.6V22h1l5.7,-5.7L13.4,12ZM13,5.8l1.9,1.9L13,9.6ZM13,18.2v-3.8l1.9,1.9Z",
        "flashlight" to "M7,2h10v4l-2,3v13H9V9L7,6V2Z M11,12a1,1 0 1,0 2,0a1,1 0 1,0 -2,0Z",
        "camera" to "M9,4l-1.8,2H4a2,2 0 0,0 -2,2v10a2,2 0 0,0 2,2h16a2,2 0 0,0 2,-2V8a2,2 0 0,0 -2,-2h-3.2L15,4H9Z M12,9a4,4 0 1,1 0,8a4,4 0 1,1 0,-8Z",
        "bell" to "M12,2a2,2 0 0,0 -2,2v0.3A6,6 0 0,0 6,10v5l-2,2v1h16v-1l-2,-2v-5a6,6 0 0,0 -4,-5.7V4a2,2 0 0,0 -2,-2Z M10,19a2,2 0 0,0 4,0Z",
        "settings" to "M12,8a4,4 0 1,0 0,8a4,4 0 1,0 0,-8Z M19.4,13a7.8,7.8 0 0,0 0,-2l2.1,-1.6l-2,-3.5l-2.5,1a7.4,7.4 0 0,0 -1.7,-1L14.9,3h-4l-0.4,2.9a7.4,7.4 0 0,0 -1.7,1l-2.5,-1l-2,3.5L6.4,11a7.8,7.8 0 0,0 0,2l-2.1,1.6l2,3.5l2.5,-1a7.4,7.4 0 0,0 1.7,1l0.4,2.9h4l0.4,-2.9a7.4,7.4 0 0,0 1.7,-1l2.5,1l2,-3.5Z M12,10a2,2 0 1,1 0,4a2,2 0 1,1 0,-4Z",
        "phone" to "M6.6,10.8a15.1,15.1 0 0,0 6.6,6.6l2.2,-2.2a1,1 0 0,1 1,-0.25a11.4,11.4 0 0,0 3.6,0.58a1,1 0 0,1 1,1V20a1,1 0 0,1 -1,1A17,17 0 0,1 3,4a1,1 0 0,1 1,-1h3.5a1,1 0 0,1 1,1a11.4,11.4 0 0,0 0.58,3.6a1,1 0 0,1 -0.25,1Z",
        "mail" to "M4,4h16a2,2 0 0,1 2,2v12a2,2 0 0,1 -2,2H4a2,2 0 0,1 -2,-2V6a2,2 0 0,1 2,-2Z M4,6l8,5l8,-5V8l-8,5L4,8Z",

        // Formas & Estilo
        "heart" to "M12,21l-1.5,-1.4C5,14.6 2,11.9 2,8.5C2,5.8 4.2,3.6 6.9,3.6c1.9,0 3.5,0.9 5.1,2.6c1.6,-1.7 3.2,-2.6 5.1,-2.6c2.7,0 4.9,2.2 4.9,4.9c0,3.4 -3,6.1 -8.5,11.1z",
        "steps" to "M6,6a2.5,4 0 1,0 5,0a2.5,4 0 1,0 -5,0Z M13,14a2.5,4 0 1,0 5,0a2.5,4 0 1,0 -5,0Z",
        "flame" to "M13.5,2c0,4 -4.5,6.5 -4.5,11a5,5 0 0,0 10,0c0,-3.5 -2,-6 -2.5,-7.5c-0.5,2 -1.5,3 -2.5,3c1,-2 0,-5 -0.5,-6.5Z M12,18a2.5,2.5 0 0,1 -2.5,-2.5c0,-1.5 1.5,-2.8 2.5,-4c1,1.2 2.5,2.5 2.5,4A2.5,2.5 0 0,1 12,18Z",
        "location" to "M12,2a7,7 0 0,0 -7,7c0,5 7,13 7,13s7,-8 7,-13a7,7 0 0,0 -7,-7z M9.5,9a2.5,2.5 0 1,0 5,0a2.5,2.5 0 1,0 -5,0Z",
        "star" to "M12,2l3,6.5l7,0.8l-5.2,4.8l1.4,7l-6.2,-3.6l-6.2,3.6l1.4,-7l-5.2,-4.8l7,-0.8z",
        "sparkle" to "M12,2l2.2,6.8L21,11l-6.8,2.2L12,20l-2.2,-6.8L3,11l6.8,-2.2Z M18.5,3l0.9,2.1L21.5,6l-2.1,0.9L18.5,9l-0.9,-2.1L15.5,6l2.1,-0.9Z",
        "diamond" to "M12,2L21,12L12,22L3,12Z",
        "shield" to "M12,2L4,5.5V11c0,5.2 3.4,9.8 8,11c4.6,-1.2 8,-5.8 8,-11V5.5Z",
        "home" to "M12,3L2,11h3v9h5v-6h4v6h5v-9h3Z",
        "search" to "M10,3a7,7 0 1,0 4.3,12.5l4.6,4.6l1.4,-1.4l-4.6,-4.6A7,7 0 0,0 10,3Z M10,5a5,5 0 1,1 0,10a5,5 0 1,1 0,-10Z"
    )
    val names: List<String> = listOf("weather") + builtIn.keys

    fun label(ic: String): String = when (ic) {
        "weather" -> "Clima Auto"
        "sun" -> "Sol"
        "moon" -> "Lua"
        "moon_crescent" -> "Lua Crescente"
        "cloud" -> "Nuvem"
        "cloud_sun" -> "Parcial Dia"
        "cloud_moon" -> "Parcial Noite"
        "clouds" -> "Nublado"
        "rain" -> "Chuva"
        "rain_heavy" -> "Chuva Forte"
        "drizzle" -> "Garoa"
        "snow" -> "Neve"
        "sleet" -> "Chuva/Neve"
        "hail" -> "Granizo"
        "storm" -> "Tempestade"
        "wind" -> "Vento"
        "fog" -> "Neblina"
        "humidity" -> "Umidade"
        "thermometer" -> "Termômetro"
        "uv" -> "Índice UV"
        "sunrise" -> "Nascer Sol"
        "sunset" -> "Pôr do Sol"
        "tornado" -> "Tornado"
        "compass" -> "Bússola"
        "music" -> "Música"
        "play" -> "Tocar"
        "pause" -> "Pausar"
        "skip_next" -> "Avançar"
        "skip_prev" -> "Voltar"
        "headphones" -> "Fones"
        "volume" -> "Volume"
        "mic" -> "Microfone"
        "radio" -> "Rádio/Podcast"
        "battery" -> "Bateria"
        "battery_charging" -> "Carregando"
        "bolt" -> "Raio"
        "alarm" -> "Alarme"
        "clock" -> "Relógio"
        "calendar" -> "Calendário"
        "lock" -> "Cadeado"
        "unlock" -> "Aberto"
        "wifi" -> "Wi-Fi"
        "bluetooth" -> "Bluetooth"
        "flashlight" -> "Lanterna"
        "camera" -> "Câmera"
        "bell" -> "Notificação"
        "settings" -> "Ajustes"
        "phone" -> "Telefone"
        "mail" -> "E-mail"
        "heart" -> "Coração"
        "steps" -> "Passos"
        "flame" -> "Calorias"
        "location" -> "Local"
        "star" -> "Estrela"
        "sparkle" -> "Brilho"
        "diamond" -> "Diamante"
        "shield" -> "Escudo"
        "home" -> "Início"
        "search" -> "Busca"
        else -> ic
    }

    fun weatherIcon(code: Int, day: Boolean): String = when (code) {
        0 -> if (day) "sun" else "moon"
        1, 2 -> if (day) "cloud_sun" else "cloud_moon"
        3 -> "clouds"
        45, 48 -> "fog"
        in 51..57 -> "drizzle"
        in 61..65, in 80..81 -> "rain"
        66, 67 -> "sleet"
        82 -> "rain_heavy"
        in 71..77, 85, 86 -> "snow"
        in 95..99 -> "storm"
        else -> "cloud"
    }

    private val cache = HashMap<String, Path>()

    fun path(data: List<String>): Path = synchronized(cache) {
        val key = data.joinToString("|")
        cache.getOrPut(key) {
            val p = Path()
            data.forEach { d -> runCatching { p.addPath(PathParser.createPathFromPathData(d)) } }
            p.fillType = Path.FillType.EVEN_ODD
            p
        }
    }

    fun builtinPath(name: String): Path = path(listOf(builtIn[name] ?: builtIn.getValue("star")))

    data class Svg(val data: List<String>, val w: Float, val h: Float)

    fun encodeSvgAsset(svg: Svg): String = "svg:${svg.w}x${svg.h}:${svg.data.joinToString("||")}"

    fun isSvgAsset(spec: String?): Boolean = spec != null && spec.startsWith("svg:")

    fun decodeSvgAsset(spec: String?): Svg? {
        if (spec == null || !spec.startsWith("svg:")) return null
        val rest = spec.removePrefix("svg:")
        val sep = rest.indexOf(':')
        if (sep <= 0) return null
        val dims = rest.substring(0, sep).split('x')
        if (dims.size != 2) return null
        val w = dims[0].toFloatOrNull()?.coerceAtLeast(1f) ?: return null
        val h = dims[1].toFloatOrNull()?.coerceAtLeast(1f) ?: return null
        val paths = rest.substring(sep + 1).split("||").filter { it.isNotBlank() }
        val valid = paths.filter { d -> runCatching { PathParser.createPathFromPathData(d) }.isSuccess }
        return if (valid.isEmpty()) null else Svg(valid, w, h)
    }

    fun importSvg(ctx: Context, uri: Uri): Svg? = runCatching {
        ctx.contentResolver.openInputStream(uri)!!.use { input ->
            val xp = Xml.newPullParser()
            xp.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            xp.setInput(input, null)
            val out = mutableListOf<String>()
            var w = 24f; var h = 24f
            fun a(n: String) = xp.getAttributeValue(null, n)
            fun f(n: String, d: Float = 0f) = a(n)?.replace(Regex("[a-z%]+$"), "")?.toFloatOrNull() ?: d
            while (xp.next() != XmlPullParser.END_DOCUMENT) {
                if (xp.eventType != XmlPullParser.START_TAG) continue
                when (xp.name) {
                    "svg" -> {
                        val vb = a("viewBox")?.trim()?.split(Regex("[ ,]+"))?.mapNotNull { it.toFloatOrNull() }
                        if (vb != null && vb.size == 4) { w = vb[2]; h = vb[3] }
                        else { w = f("width", 24f); h = f("height", 24f) }
                    }
                    "path" -> a("d")?.let { out.add(it) }
                    "rect" -> {
                        val x = f("x"); val y = f("y"); val rw = f("width"); val rh = f("height"); val rx = f("rx", f("ry"))
                        out.add(if (rx > 0f) "M${x + rx},${y}h${rw - 2 * rx}a$rx,$rx 0 0,1 $rx,${rx}v${rh - 2 * rx}a$rx,$rx 0 0,1 ${-rx},${rx}h${-(rw - 2 * rx)}a$rx,$rx 0 0,1 ${-rx},${-rx}v${-(rh - 2 * rx)}a$rx,$rx 0 0,1 $rx,${-rx}z"
                        else "M$x,${y}h${rw}v${rh}h${-rw}z")
                    }
                    "circle", "ellipse" -> {
                        val cx = f("cx"); val cy = f("cy"); val rx = f("r", f("rx")); val ry = f("r", f("ry"))
                        out.add("M${cx - rx},${cy}a$rx,$ry 0 1,0 ${2 * rx},0a$rx,$ry 0 1,0 ${-2 * rx},0z")
                    }
                    "polygon", "polyline" -> a("points")?.trim()?.split(Regex("[ ,]+"))?.mapNotNull { it.toFloatOrNull() }?.chunked(2)?.filter { it.size == 2 }?.let { pts ->
                        if (pts.isNotEmpty()) out.add("M" + pts.joinToString("L") { "${it[0]},${it[1]}" } + if (xp.name == "polygon") "z" else "")
                    }
                }
            }
            val valid = out.filter { d -> runCatching { PathParser.createPathFromPathData(d) }.isSuccess }
            if (valid.isEmpty()) null else Svg(valid, w, h)
        }
    }.getOrNull()
}
