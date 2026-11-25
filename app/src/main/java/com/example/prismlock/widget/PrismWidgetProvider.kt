package com.example.prismlock.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import com.example.prismlock.engine.HitBox
import com.example.prismlock.MainActivity
import com.example.prismlock.R
import com.example.prismlock.data.EnvProvider
import com.example.prismlock.data.ProjectStore
import com.example.prismlock.engine.FontManager
import com.example.prismlock.engine.LiquidGlassRenderer
import com.example.prismlock.engine.TouchActionExecutor
import com.example.prismlock.model.Project
import com.example.prismlock.model.WIDGET_ASPECT_MAX
import com.example.prismlock.model.WIDGET_ASPECT_MIN
import com.example.prismlock.model.flatten
import com.example.prismlock.wallpaper.WallpaperCarousel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import kotlin.math.roundToInt
import kotlin.math.sqrt

class PrismWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateIdsAsync(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        updateIdsAsync(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
    }

    private fun updateIdsAsync(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        val pending = goAsync()
        val appCtx = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                renderAndPushWidgets(appCtx, appWidgetManager, appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_WIDGET_TAP = "com.example.prismlock.ACTION_WIDGET_TAP"
        const val ACTION_WIDGET_REFRESH = "com.example.prismlock.ACTION_WIDGET_REFRESH"
        const val EXTRA_NODE_ID = "com.example.prismlock.EXTRA_NODE_ID"
        private const val MIN_TARGET_DP = 40f
        private val lastWidgetRenderSig = java.util.concurrent.ConcurrentHashMap<Int, String>()
        private val HIT_VIEW_IDS = intArrayOf(
            R.id.widget_hit_0, R.id.widget_hit_1, R.id.widget_hit_2,
            R.id.widget_hit_3, R.id.widget_hit_4, R.id.widget_hit_5
        )

        internal fun handleActionIntent(context: Context, intent: Intent, pending: PendingResult) {
            val appCtx = context.applicationContext
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                try {
                    when (intent.action) {
                        ACTION_WIDGET_REFRESH -> {
                            lastWidgetRenderSig.clear()
                            val mgr = AppWidgetManager.getInstance(appCtx)
                            val ids = mgr.getAppWidgetIds(ComponentName(appCtx, PrismWidgetProvider::class.java))
                            if (ids.isNotEmpty()) renderAndPushWidgets(appCtx, mgr, ids)
                        }
                        ACTION_WIDGET_TAP -> {
                            lastWidgetRenderSig.clear()
                            val widget = ProjectStore.getActiveWidget(appCtx)
                            val tappedId = intent.getStringExtra(EXTRA_NODE_ID)
                            val actionable = tappedId?.let { id ->
                                widget.layers.flatten().map { it.first }.firstOrNull { it.id == id && !it.hidden && it.touch.isActive }
                            }
                            if (actionable != null) {
                                val env = EnvProvider.env.value.copy(locked = false)
                                var mutated: Project? = null
                                TouchActionExecutor.execute(
                                    ctx = appCtx,
                                    node = actionable,
                                    project = widget,
                                    env = env,
                                    now = LocalDateTime.now(),
                                    onMutateProject = { updated -> mutated = updated },
                                    onFeedback = { msg ->
                                        Handler(Looper.getMainLooper()).post {
                                            Toast.makeText(appCtx, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                                mutated?.let { ProjectStore.saveActiveWidget(appCtx, it) }
                                val mgr = AppWidgetManager.getInstance(appCtx)
                                val ids = mgr.getAppWidgetIds(ComponentName(appCtx, PrismWidgetProvider::class.java))
                                if (ids.isNotEmpty()) renderAndPushWidgets(appCtx, mgr, ids)
                            } else {
                                val launch = Intent(appCtx, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                }
                                runCatching { appCtx.startActivity(launch) }
                            }
                        }
                    }
                } finally {
                    pending.finish()
                }
            }
        }

        private class HitTarget(val nodeId: String, val leftDp: Float, val topDp: Float, val wDp: Float, val hDp: Float)

        private fun buildTargets(
            widget: Project,
            hits: List<HitBox>,
            bmpW: Int,
            bmpH: Int,
            widgetWDp: Float,
            widgetHDp: Float
        ): List<HitTarget> {
            val scale = minOf(widgetWDp / bmpW, widgetHDp / bmpH)
            val offX = (widgetWDp - bmpW * scale) * 0.5f
            val offY = (widgetHDp - bmpH * scale) * 0.5f
            val out = ArrayList<HitTarget>(HIT_VIEW_IDS.size)
            for (node in widget.layers.flatten().map { it.first }) {
                if (out.size >= HIT_VIEW_IDS.size) break
                if (node.hidden || !node.touch.isActive) continue
                val box = hits.lastOrNull { it.id == node.id } ?: continue
                var w = box.rect.width() * scale
                var h = box.rect.height() * scale
                val cx = offX + box.rect.centerX() * scale
                val cy = offY + box.rect.centerY() * scale
                w = w.coerceAtLeast(MIN_TARGET_DP).coerceAtMost(widgetWDp)
                h = h.coerceAtLeast(MIN_TARGET_DP).coerceAtMost(widgetHDp)
                val left = (cx - w * 0.5f).coerceIn(0f, (widgetWDp - w).coerceAtLeast(0f))
                val top = (cy - h * 0.5f).coerceIn(0f, (widgetHDp - h).coerceAtLeast(0f))
                out.add(HitTarget(node.id, left, top, w, h))
            }
            return out
        }

        fun hasPinnedWidgets(ctx: Context): Boolean {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, PrismWidgetProvider::class.java))
            return ids.isNotEmpty()
        }

        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, PrismWidgetProvider::class.java))
            if (ids.isEmpty()) return
            ctx.sendBroadcast(Intent(ctx, PrismWidgetActionReceiver::class.java).setAction(ACTION_WIDGET_REFRESH))
        }

        fun requestPin(ctx: Context): Boolean {
            val mgr = AppWidgetManager.getInstance(ctx)
            if (!mgr.isRequestPinAppWidgetSupported) return false
            val provider = ComponentName(ctx, PrismWidgetProvider::class.java)
            val callback = PendingIntent.getBroadcast(
                ctx,
                701,
                Intent(ctx, PrismWidgetActionReceiver::class.java).setAction(ACTION_WIDGET_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            return mgr.requestPinAppWidget(provider, null, callback)
        }

        suspend fun resolveReferenceBg(
            ctx: Context,
            widgetProject: Project,
            reqW: Int,
            reqH: Int,
            cacheInMemory: Boolean = true
        ): Bitmap? {
            val directUri = widgetProject.imageUri?.takeIf { it.isNotBlank() }
                ?: widgetProject.folderUri?.let { WallpaperCarousel.currentUri(ctx, widgetProject, advance = false, persist = false) }
                ?: ProjectStore.currentWallpaperUri(ctx)
                ?: ProjectStore.readActiveProject(ctx).let { wallProj ->
                    WallpaperCarousel.currentUri(ctx, wallProj, advance = false, persist = false)
                }
            return WallpaperCarousel.loadBitmap(ctx, directUri, reqW, reqH, preferRgb565 = true, cacheInMemory = cacheInMemory)
        }

        private suspend fun renderAndPushWidgets(
            ctx: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            FontManager.init(ctx)
            EnvProvider.refreshBattery(ctx)
            EnvProvider.refreshAlarm(ctx)
            EnvProvider.refreshAudio(ctx)

            val widget = ProjectStore.getActiveWidget(ctx)
            val now = LocalDateTime.now()
            val env = EnvProvider.env.value.copy(locked = false)
            val renderer = LiquidGlassRenderer().apply { attachContext(ctx) }
            try {
                for (id in appWidgetIds) {
                    val opts = appWidgetManager.getAppWidgetOptions(id)
                    val sizes = opts.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
                    val slotSize = sizes?.firstOrNull { it.width > 10f && it.height > 10f }
                    val slotWDp = slotSize?.width
                        ?: opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 280).coerceAtLeast(100).toFloat()
                    val slotHDp = slotSize?.height
                        ?: opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 140).coerceAtLeast(64).toFloat()
                    val aspect = (slotWDp / slotHDp).takeIf { it > 0.25f }
                        ?.coerceIn(WIDGET_ASPECT_MIN, WIDGET_ASPECT_MAX)
                        ?: widget.widgetAspect.coerceIn(WIDGET_ASPECT_MIN, WIDGET_ASPECT_MAX)

                    // Keep binder payload strictly under ~1.2 MB (max ~300k ARGB_8888 pixels)
                    val maxPixels = 300_000f
                    val targetW = sqrt(maxPixels * aspect).roundToInt().coerceIn(320, 860)
                    val targetH = (targetW / aspect).roundToInt().coerceIn(180, 860)

                    val globals = widget.globalsMap()
                    val evalSig = buildString {
                        append(widget.hashCode()).append('|')
                        append(targetW).append('x').append(targetH).append('|')
                        widget.layers.flatten().forEach { (n, _) ->
                            if (!n.hidden) {
                                append(com.example.prismlock.engine.Dynamic.eval(n.text, env, now, globals)).append(';')
                                if (n.value.isNotBlank()) append(com.example.prismlock.engine.Dynamic.eval(n.value, env, now, globals)).append(';')
                                if (n.visibilityExpr.isNotBlank()) append(com.example.prismlock.engine.Dynamic.eval(n.visibilityExpr, env, now, globals)).append(';')
                            }
                        }
                    }
                    if (lastWidgetRenderSig[id] == evalSig) continue

                    val refBg = resolveReferenceBg(ctx, widget, targetW, targetH)
                    val hits = ArrayList<HitBox>()
                    val bmp = renderer.renderWidgetBitmap(targetW, targetH, widget, refBg, env, now, hits)
                    renderer.releaseOffscreenResources()
                    val targets = buildTargets(widget, hits, targetW, targetH, slotWDp, slotHDp)

                    val tapIntent = Intent(ctx, PrismWidgetActionReceiver::class.java).apply {
                        action = ACTION_WIDGET_TAP
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    }
                    val pi = PendingIntent.getBroadcast(
                        ctx,
                        id,
                        tapIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val views = RemoteViews(ctx.packageName, R.layout.prism_widget).apply {
                        setImageViewBitmap(R.id.widget_canvas_img, bmp)
                        setOnClickPendingIntent(R.id.widget_root, pi)
                        HIT_VIEW_IDS.forEachIndexed { idx, viewId ->
                            val t = targets.getOrNull(idx)
                            if (t == null) {
                                setViewVisibility(viewId, View.GONE)
                            } else {
                                setViewVisibility(viewId, View.VISIBLE)
                                setViewLayoutWidth(viewId, t.wDp, TypedValue.COMPLEX_UNIT_DIP)
                                setViewLayoutHeight(viewId, t.hDp, TypedValue.COMPLEX_UNIT_DIP)
                                setViewLayoutMargin(viewId, RemoteViews.MARGIN_START, t.leftDp, TypedValue.COMPLEX_UNIT_DIP)
                                setViewLayoutMargin(viewId, RemoteViews.MARGIN_TOP, t.topDp, TypedValue.COMPLEX_UNIT_DIP)
                                val nodeIntent = Intent(ctx, PrismWidgetActionReceiver::class.java).apply {
                                    action = ACTION_WIDGET_TAP
                                    data = Uri.fromParts("prismwidget", "$id/${t.nodeId}", null)
                                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                                    putExtra(EXTRA_NODE_ID, t.nodeId)
                                }
                                setOnClickPendingIntent(
                                    viewId,
                                    PendingIntent.getBroadcast(
                                        ctx,
                                        id * 8 + idx + 1,
                                        nodeIntent,
                                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                    )
                                )
                            }
                        }
                    }
                    appWidgetManager.updateAppWidget(id, views)
                    lastWidgetRenderSig[id] = evalSig
                    bmp.recycle()
                }
            } finally {
                renderer.release()
            }
        }
    }
}
