package com.example.prismlock.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Non-exported receiver handling internal widget PendingIntents (tap and refresh)
 * so external apps cannot spoof widget clicks or trigger user actions.
 */
class PrismWidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PrismWidgetProvider.handleActionIntent(context, intent, goAsync())
    }
}
