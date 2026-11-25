package com.example.prismlock.engine

import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import com.example.prismlock.MainActivity
import com.example.prismlock.data.Env
import com.example.prismlock.model.LayerNode
import com.example.prismlock.model.Project
import com.example.prismlock.model.TouchAction
import com.example.prismlock.model.TouchActionType
import com.example.prismlock.model.find
import com.example.prismlock.model.parentOf
import java.time.LocalDateTime

data class LaunchableApp(
    val packageName: String,
    val label: String
)

object TouchActionExecutor {
    fun labelOf(type: TouchActionType): String = when (type) {
        TouchActionType.NONE -> "Nenhum"
        TouchActionType.OPEN_APP -> "Abrir Aplicativo"
        TouchActionType.OPEN_URL -> "Abrir Link / URI"
        TouchActionType.TOGGLE_GLOBAL -> "Alternar Global"
        TouchActionType.SET_GLOBAL -> "Definir Global"
        TouchActionType.NEXT_GLOBAL_LIST -> "Ciclar Global / Contador"
        TouchActionType.NEXT_WALLPAPER -> "Próximo Wallpaper"
        TouchActionType.MEDIA_PLAY_PAUSE -> "Mídia: Play / Pause"
        TouchActionType.MEDIA_NEXT -> "Mídia: Próxima"
        TouchActionType.MEDIA_PREV -> "Mídia: Anterior"
        TouchActionType.SYSTEM_ALARM -> "Abrir Alarme / Relógio"
        TouchActionType.SYSTEM_CAMERA -> "Abrir Câmera"
        TouchActionType.SYSTEM_SETTINGS -> "Abrir Configurações"
        TouchActionType.OPEN_PRISMLOCK -> "Abrir PrismLock"
    }

    fun describe(action: TouchAction): String = when (action.type) {
        TouchActionType.NONE -> "Sem ação"
        TouchActionType.OPEN_APP -> action.targetLabel.ifBlank { action.targetPackage }.ifBlank { "Selecionar app" }
        TouchActionType.OPEN_URL -> action.url.ifBlank { "Definir link" }
        TouchActionType.TOGGLE_GLOBAL -> "Alternar \$gv(${action.globalName.ifBlank { "?" }})\$"
        TouchActionType.SET_GLOBAL -> "\$gv(${action.globalName.ifBlank { "?" }})\$ = ${action.globalValue}"
        TouchActionType.NEXT_GLOBAL_LIST -> "Avançar \$gv(${action.globalName.ifBlank { "?" }})\$"
        else -> labelOf(action.type)
    }

    fun queryLaunchableApps(ctx: Context): List<LaunchableApp> {
        val pm = ctx.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = runCatching { pm.queryIntentActivities(mainIntent, 0) }.getOrDefault(emptyList())
        return resolved.mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            val lbl = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg)
            LaunchableApp(packageName = pkg, label = lbl)
        }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * Encontra a camada acionável sob (baseX, baseY) na ordem inversa de desenho.
     * Se um item filho dentro de uma Pilha não tiver ação própria, sobe para a Pilha pai caso ela tenha ação.
     */
    fun hitTestActionable(
        hits: List<HitBox>,
        layers: List<LayerNode>,
        baseX: Float,
        baseY: Float,
        minTouchPx: Float = 42f
    ): LayerNode? {
        val pt = floatArrayOf(baseX, baseY)
        for (box in hits.asReversed()) {
            pt[0] = baseX
            pt[1] = baseY
            box.invMatrix.mapPoints(pt)
            val padX = ((minTouchPx - box.localW) * 0.5f).coerceAtLeast(8f)
            val padY = ((minTouchPx - box.localH) * 0.5f).coerceAtLeast(8f)
            if (pt[0] !in -padX..(box.localW + padX) || pt[1] !in -padY..(box.localH + padY)) continue

            var cur: LayerNode? = layers.find(box.id)
            while (cur != null) {
                if (cur.touch.isActive) return cur
                cur = layers.parentOf(cur.id)
            }
        }
        return null
    }

    private val ALLOWED_URL_SCHEMES = setOf("http", "https", "mailto", "tel")

    fun isAllowedUrlScheme(normalizedUrl: String): Boolean {
        val scheme = normalizedUrl.substringBefore(':', "").lowercase()
        return scheme in ALLOWED_URL_SCHEMES
    }

    fun execute(
        ctx: Context,
        node: LayerNode,
        project: Project,
        env: Env,
        now: LocalDateTime = LocalDateTime.now(),
        onMutateProject: (Project) -> Unit = {},
        onNextWallpaper: () -> Unit = {},
        onFeedback: (String) -> Unit = {}
    ): Boolean {
        val action = node.touch
        if (!action.isActive) return false
        val globals = project.globalsMap()

        return when (action.type) {
            TouchActionType.NONE -> false
            TouchActionType.TOGGLE_GLOBAL -> {
                if (action.globalName.isBlank()) return false
                onMutateProject(project.toggleGlobal(action.globalName))
                true
            }
            TouchActionType.SET_GLOBAL -> {
                if (action.globalName.isBlank()) return false
                val evaluated = Dynamic.eval(action.globalValue, env, now, globals)
                onMutateProject(project.setGlobalValue(action.globalName, evaluated))
                true
            }
            TouchActionType.NEXT_GLOBAL_LIST -> {
                if (action.globalName.isBlank()) return false
                onMutateProject(project.cycleGlobal(action.globalName, 1))
                true
            }
            TouchActionType.NEXT_WALLPAPER -> {
                onNextWallpaper()
                true
            }
            TouchActionType.MEDIA_PLAY_PAUSE -> dispatchMediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            TouchActionType.MEDIA_NEXT -> dispatchMediaKey(ctx, KeyEvent.KEYCODE_MEDIA_NEXT)
            TouchActionType.MEDIA_PREV -> dispatchMediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            TouchActionType.OPEN_APP -> {
                val pkg = action.targetPackage.trim()
                if (pkg.isEmpty()) {
                    onFeedback("Nenhum aplicativo configurado nesta camada")
                    return false
                }
                val launch = ctx.packageManager.getLaunchIntentForPackage(pkg)
                if (launch == null) {
                    onFeedback("Aplicativo não encontrado: $pkg")
                    return false
                }
                launchActivitySafely(ctx, launch, requestCode = node.id.hashCode(), onFeedback = onFeedback)
            }
            TouchActionType.OPEN_URL -> {
                val raw = Dynamic.eval(action.url, env, now, globals).trim()
                if (raw.isEmpty()) {
                    onFeedback("Nenhum link configurado")
                    return false
                }
                val normalized = if ("://" !in raw && ':' !in raw.substringBefore('/')) "https://$raw" else raw
                if (!isAllowedUrlScheme(normalized)) {
                    onFeedback("Esquema de link não permitido")
                    return false
                }
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized))
                launchActivitySafely(ctx, intent, requestCode = node.id.hashCode() xor 0x1337, onFeedback = onFeedback)
            }
            TouchActionType.SYSTEM_ALARM -> {
                val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
                launchActivitySafely(ctx, intent, requestCode = 9001, onFeedback = onFeedback)
            }
            TouchActionType.SYSTEM_CAMERA -> {
                val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                launchActivitySafely(ctx, intent, requestCode = 9002, onFeedback = onFeedback)
            }
            TouchActionType.SYSTEM_SETTINGS -> {
                val intent = Intent(Settings.ACTION_SETTINGS)
                launchActivitySafely(ctx, intent, requestCode = 9003, onFeedback = onFeedback)
            }
            TouchActionType.OPEN_PRISMLOCK -> {
                val intent = Intent(ctx, MainActivity::class.java)
                launchActivitySafely(ctx, intent, requestCode = 9004, onFeedback = onFeedback)
            }
        }
    }

    private fun dispatchMediaKey(ctx: Context, keyCode: Int): Boolean {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return false
        val t = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, keyCode, 0))
        am.dispatchMediaKeyEvent(KeyEvent(t, t + 15L, KeyEvent.ACTION_UP, keyCode, 0))
        return true
    }

    private fun launchActivitySafely(
        ctx: Context,
        intent: Intent,
        requestCode: Int,
        onFeedback: (String) -> Unit
    ): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (ctx is Activity) {
            return runCatching {
                ctx.startActivity(intent)
                true
            }.getOrElse {
                onFeedback("Não foi possível abrir o destino")
                false
            }
        }
        val piResult = runCatching {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pi = PendingIntent.getActivity(ctx, requestCode, intent, flags)
            val bundle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                @Suppress("DEPRECATION")
                ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    .toBundle()
            } else {
                null
            }
            pi.send(ctx, 0, null, null, null, null, bundle)
            true
        }
        if (piResult.getOrDefault(false)) return true

        return runCatching {
            ctx.startActivity(intent)
            true
        }.getOrElse {
            onFeedback("Bloqueado pelo sistema: permita início em segundo plano nas configurações da bateria")
            false
        }
    }
}
