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

            val label = if (book == null) context.getString(R.string.widget_not_set)
            else book.title.ifBlank { context.getString(R.string.widget_untitled) }
            views.setTextViewText(R.id.widget_title, label)

            // One button that toggles: Stop while Spotify is playing on this phone, otherwise Play.
            val playingHere = book != null && PlaybackStatus.isPlayingLocally(context)
            val click = when {
                book == null -> PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                playingHere -> PendingIntent.getBroadcast(
                    context, 1, Intent(context, StopReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                else -> PendingIntent.getActivity(
                    context, 2, Intent(context, PlayActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }
            views.setImageViewResource(R.id.widget_icon, if (playingHere) R.drawable.ic_stop else R.drawable.ic_play)
            views.setInt(R.id.widget_icon, "setBackgroundResource", if (playingHere) R.drawable.stop_circle else R.drawable.play_circle)
            views.setContentDescription(R.id.widget_icon, context.getString(if (playingHere) R.string.stop else R.string.play))
            views.setOnClickPendingIntent(R.id.widget_play, click)
            return views
        }
    }
}
