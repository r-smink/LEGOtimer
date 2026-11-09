package nl.nextbuzz.bricktimer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nl.nextbuzz.bricktimer.MainActivity
import nl.nextbuzz.bricktimer.R
import nl.nextbuzz.bricktimer.data.AppData
import nl.nextbuzz.bricktimer.data.Repository
import nl.nextbuzz.bricktimer.logic.Fmt
import nl.nextbuzz.bricktimer.logic.tr

/** Home screen widget showing the current build with a start/stop button. */
class TimerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = render(context, Repository.get(context).value)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) return super.onReceive(context, intent)
        val repo = Repository.get(context)
        val msg = if (repo.value.timer.running) {
            repo.finishSession()?.let { (entry, session) -> tr("Sessie opgeslagen · ${Fmt.dur(session.durSec)} aan ${entry.set.name}", "Session saved · ${Fmt.dur(session.durSec)} on ${entry.set.name}") }
        } else {
            if (repo.startTimer()) tr("Timer gestart", "Timer started") else null
        }
        // The widget may show stale state (e.g. the build was finished in the app); redraw either way.
        if (msg == null) refresh(context)
        msg?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repo.flush()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION_TOGGLE = "nl.nextbuzz.bricktimer.action.TOGGLE_TIMER"

        /** Redraws all placed widgets from the current state. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, TimerWidget::class.java))
            if (ids.isEmpty()) return
            val views = render(context, Repository.get(context).value)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun appIntent(context: Context) = Intent(context, MainActivity::class.java)

        private fun render(context: Context, data: AppData): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_timer)
            val open = PendingIntent.getActivity(context, 0, appIntent(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            v.setOnClickPendingIntent(R.id.widget_root, open)

            val cur = data.current
            if (cur == null) {
                v.setTextViewText(R.id.widget_title, tr("Geen huidige build", "No current build"))
                v.setTextViewText(R.id.widget_status, tr("Tik om een set te kiezen", "Tap to pick a set"))
                v.setViewVisibility(R.id.widget_status, View.VISIBLE)
                v.setViewVisibility(R.id.widget_chrono, View.GONE)
                v.setViewVisibility(R.id.widget_toggle, View.GONE)
                return v
            }

            v.setTextViewText(R.id.widget_title, cur.set.name)
            v.setViewVisibility(R.id.widget_toggle, View.VISIBLE)
            val toggle = PendingIntent.getBroadcast(
                context, 1,
                Intent(context, TimerWidget::class.java).setAction(ACTION_TOGGLE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            v.setOnClickPendingIntent(R.id.widget_toggle, toggle)

            if (data.timer.running) {
                val base = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - data.timer.startMs)
                v.setChronometer(R.id.widget_chrono, base, null, true)
                v.setViewVisibility(R.id.widget_chrono, View.VISIBLE)
                v.setViewVisibility(R.id.widget_status, View.GONE)
                v.setImageViewResource(R.id.widget_toggle, R.drawable.widget_stop)
                v.setContentDescription(R.id.widget_toggle, tr("Stop bouwen", "Stop building"))
            } else {
                v.setChronometer(R.id.widget_chrono, SystemClock.elapsedRealtime(), null, false)
                v.setViewVisibility(R.id.widget_chrono, View.GONE)
                v.setTextViewText(R.id.widget_status, if (cur.totalSec > 0) tr("Totaal ${Fmt.dur(cur.totalSec)}", "Total ${Fmt.dur(cur.totalSec)}") else tr("Klaar om te starten", "Ready to start"))
                v.setViewVisibility(R.id.widget_status, View.VISIBLE)
                v.setImageViewResource(R.id.widget_toggle, R.drawable.widget_play)
                v.setContentDescription(R.id.widget_toggle, tr("Start bouwen", "Start building"))
            }
            return v
        }
    }
}
