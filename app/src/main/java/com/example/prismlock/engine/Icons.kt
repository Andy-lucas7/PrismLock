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
        "sun" to "M8,12a4,4 0 1,0 8,0a4,4 0 1,0 -8,0Z M11,1h2v3h-2z M11,20h2v3h-2z M1,11h3v2h-3z M20,11h3v2h-3z M4.2,5.6l1.4,-1.4l2.1,2.1l-1.4,1.4z M16.3,17.7l1.4,-1.4l2.1,2.1l-1.4,1.4z M4.2,18.4l2.1,-2.1l1.4,1.4l-2.1,2.1z M16.3,6.3l2.1,-2.1l1.4,1.4l-2.1,2.1z",
        "moon" to "M12.5,2A10,10 0 1,0 22,13.5A7.5,7.5 0 0,1 12.5,2Z",
        "cloud" to CLOUD,
        "cloud_sun" to "M3,7a4,4 0 1,0 8,0a4,4 0 1,0 -8,0Z M7,0.5h1v2h-1z M0.5,6.5h2v1h-2z $CLOUD",
        "rain" to "$CLOUD_UP M8,17h1.6l-1,4h-1.6z M12,17h1.6l-1,4h-1.6z M16,17h1.6l-1,4h-1.6z",
        "snow" to "$CLOUD_UP M7,19a1.2,1.2 0 1,0 2.4,0a1.2,1.2 0 1,0 -2.4,0Z M11,21a1.2,1.2 0 1,0 2.4,0a1.2,1.2 0 1,0 -2.4,0Z M15,19a1.2,1.2 0 1,0 2.4,0a1.2,1.2 0 1,0 -2.4,0Z",
        "storm" to "$CLOUD_UP M11,16h3.5l-2,3h2.5l-5,5l1.5,-4h-2.5z",
        "fog" to "M3,7h18v2h-18z M5,11h14v2h-14z M3,15h18v2h-18z M6,19h12v2h-12z",
        "battery" to "M9,2h6v2h2.5a1.5,1.5 0 0,1 1.5,1.5v15a1.5,1.5 0 0,1 -1.5,1.5h-11a1.5,1.5 0 0,1 -1.5,-1.5v-15a1.5,1.5 0 0,1 1.5,-1.5h2.5z M7,6v14h10v-14z",
        "bolt" to "M13,2L4,14h6l-1,8l9,-12h-6z",
        "alarm" to "M4,13a8,8 0 1,0 16,0a8,8 0 1,0 -16,0Z M6,13a6,6 0 1,1 12,0a6,6 0 1,1 -12,0Z M11,8h2v3h-2z M11,11h4v2h-4z M2,6l4,-4l1.5,1.5l-4,4z M22,6l-4,-4l-1.5,1.5l4,4z",
        "lock" to "M6,10h12a1,1 0 0,1 1,1v10a1,1 0 0,1 -1,1h-12a1,1 0 0,1 -1,-1v-10a1,1 0 0,1 1,-1z M8,10v-3a4,4 0 0,1 8,0v3h-2v-3a2,2 0 0,0 -4,0v3z",
        "unlock" to "M6,10h12a1,1 0 0,1 1,1v10a1,1 0 0,1 -1,1h-12a1,1 0 0,1 -1,-1v-10a1,1 0 0,1 1,-1z M8,10v-3a4,4 0 0,1 8,0h-2a2,2 0 0,0 -4,0v3z",
        "music" to "M9,3h11v12.5a3,3 0 1,1 -2,-2.83v-6.67h-7v9.5a3,3 0 1,1 -2,-2.83z",
        "heart" to "M12,21l-1.5,-1.4C5,14.6 2,11.9 2,8.5C2,5.8 4.2,3.6 6.9,3.6c1.9,0 3.5,0.9 5.1,2.6c1.6,-1.7 3.2,-2.6 5.1,-2.6c2.7,0 4.9,2.2 4.9,4.9c0,3.4 -3,6.1 -8.5,11.1z",
        "steps" to "M6,6a2.5,4 0 1,0 5,0a2.5,4 0 1,0 -5,0Z M13,14a2.5,4 0 1,0 5,0a2.5,4 0 1,0 -5,0Z",
        "location" to "M12,2a7,7 0 0,0 -7,7c0,5 7,13 7,13s7,-8 7,-13a7,7 0 0,0 -7,-7z M9.5,9a2.5,2.5 0 1,0 5,0a2.5,2.5 0 1,0 -5,0Z",
        "star" to "M12,2l3,6.5l7,0.8l-5.2,4.8l1.4,7l-6.2,-3.6l-6.2,3.6l1.4,-7l-5.2,-4.8l7,-0.8z"
    )
    val names: List<String> = listOf("weather") + builtIn.keys

    fun weatherIcon(code: Int, day: Boolean): String = when (code) {
        0 -> if (day) "sun" else "moon"; 1, 2 -> if (day) "cloud_sun" else "cloud"; 3 -> "cloud"; 45, 48 -> "fog"
        in 51..67, in 80..82 -> "rain"; in 71..77, 85, 86 -> "snow"; in 95..99 -> "storm"; else -> "cloud"
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
