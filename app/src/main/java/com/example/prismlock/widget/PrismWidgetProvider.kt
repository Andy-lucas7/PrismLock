package com.example.prismlock.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.RemoteViews
import com.example.prismlock.MainActivity
import com.example.prismlock.R
import com.example.prismlock.data.EnvProvider
import com.example.prismlock.data.ProjectStore
import com.example.prismlock.engine.FontManager
import com.example.prismlock.engine.LiquidGlassRenderer
import com.example.prismlock.engine.TouchActionExecutor
import com.example.prismlock.model.Project
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
        when (intent.action) {
            ACTION_WIDGET_REFRESH -> {
                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(ComponentName(context, PrismWidgetProvider::class.java))
                if (ids.isNotEmpty()) {
                    updateIdsAsync(context, mgr, ids)
                }
            }
            ACTION_WIDGET_TAP -> {
                val pending = goAsync()
                val appCtx = context.applicationContext
                CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                    try {
                        val widget = ProjectStore.getActiveWidget(appCtx)
                        val actionable = widget.layers.flatten().map { it.first }.firstOrNull { !it.hidden && it.touch.isActive }
                        if (actionable != null) {
                            val env = EnvProvider.env.value.copy(locked = false)
                            var mutated: Project? = null
                            TouchActionExecutor.execute(
                                ctx = appCtx,
                                node = actionable,
                                project = widget,
                                env = env,
                                now = LocalDateTime.now(),
                                onMutateProject = { updated -> mutated = updated }
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
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
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

        fun hasPinnedWidgets(ctx: Context): Boolean {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, PrismWidgetProvider::class.java))
            return ids.isNotEmpty()
        }

        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, PrismWidgetProvider::class.java))
            if (ids.isEmpty()) return
            ctx.sendBroadcast(Intent(ctx, PrismWidgetProvider::class.java).setAction(ACTION_WIDGET_REFRESH))
        }

        fun requestPin(ctx: Context): Boolean {
            val mgr = AppWidgetManager.getInstance(ctx)
            if (!mgr.isRequestPinAppWidgetSupported) return false
            val provider = ComponentName(ctx, PrismWidgetProvider::class.java)
            val callback = PendingIntent.getBroadcast(
                ctx,
                701,
                Intent(ctx, PrismWidgetProvider::class.java).setAction(ACTION_WIDGET_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            return mgr.requestPinAppWidget(provider, null, callback)
        }

        suspend fun resolveReferenceBg(
            ctx: Context,
            widgetProject: Project,
            reqW: Int,
            reqH: Int
        ): Bitmap? {
            val directUri = widgetProject.imageUri?.takeIf { it.isNotBlank() }
                ?: widgetProject.folderUri?.let { WallpaperCarousel.currentUri(ctx, widgetProject, advance = false) }
                ?: ProjectStore.currentWallpaperUri(ctx)
                ?: ProjectStore.flow(ctx).first().let { wallProj ->
                    WallpaperCarousel.currentUri(ctx, wallProj, advance = false)
                }
            return WallpaperCarousel.loadBitmap(ctx, directUri, reqW, reqH, preferRgb565 = true)
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
                    val density = ctx.resources.displayMetrics.density.coerceAtLeast(1f)
                    val minW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 280).coerceAtLeast(100)
                    val maxH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 140).coerceAtLeast(64)
                    val aspect = (minW.toFloat() / maxH.toFloat()).takeIf { it > 0.25f } ?: widget.widgetAspect.coerceIn(0.6f, 4.5f)

                    // Keep binder payload strictly under ~1.2 MB (max ~300k ARGB_8888 pixels)
                    val maxPixels = 300_000f
                    val targetW = sqrt(maxPixels * aspect).roundToInt().coerceIn(320, 860)
                    val targetH = (targetW / aspect).roundToInt().coerceIn(180, 860)

                    val refBg = resolveReferenceBg(ctx, widget, targetW, targetH)
                    val bmp = renderer.renderWidgetBitmap(targetW, targetH, widget, refBg, env, now)
                    renderer.releaseOffscreenResources()

                    val tapIntent = Intent(ctx, PrismWidgetProvider::class.java).apply {
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
                    }
                    appWidgetManager.updateAppWidget(id, views)
                    bmp.recycle()
                }
            } finally {
                renderer.release()
                WallpaperCarousel.clearMemoryCache()
            }
        }
    }
}
