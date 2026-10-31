package com.tpmanley.audiobookbutton

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class AudiobookWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = buildViews(context)
        appWidgetIds.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, AudiobookWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                val views = buildViews(context)
                ids.forEach { manager.updateAppWidget(it, views) }
            }
        }

        private fun buildViews(context: Context): RemoteViews {
            val book = BookStore.load(context)
            val views = RemoteViews(context.packageName, R.layout.widget_audiobook)

            val (label, target) = if (book == null) {
                context.getString(R.string.widget_not_set) to Intent(context, MainActivity::class.java)
            } else {
                book.title.ifBlank { context.getString(R.string.widget_untitled) } to
                    Intent(context, PlayActivity::class.java)
            }
            views.setTextViewText(R.id.widget_title, label)

            target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pending = PendingIntent.getActivity(
                context, 0, target,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            return views
        }
    }
}
