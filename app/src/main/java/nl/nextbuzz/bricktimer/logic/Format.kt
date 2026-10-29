package nl.nextbuzz.bricktimer.logic

import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

/** Dutch display formatting, mirroring the BrickTimer design. */
object Fmt {
    private val nl = Locale.forLanguageTag("nl-NL")
    private val months = listOf("jan", "feb", "mrt", "apr", "mei", "jun", "jul", "aug", "sep", "okt", "nov", "dec")

    private fun pad(n: Long) = n.toString().padStart(2, '0')

    /** 01:23:45 */
    fun clock(sec: Long) = "${pad(sec / 3600)}:${pad(sec % 3600 / 60)}:${pad(sec % 60)}"

    /** "2u 05m" or "4m 07s" */
    fun dur(sec: Long): String {
        val h = sec / 3600
        val m = sec % 3600 / 60
        return if (h > 0) "${h}u ${pad(m)}m" else "${m}m ${pad(sec % 60)}s"
    }

    /** Seconds per brick, e.g. "3,7s" */
    fun spb(sec: Long, pieces: Int): String =
        if (pieces <= 0) "—" else String.format(nl, "%.1fs", sec.toDouble() / pieces)

    fun pieces(n: Int): String = NumberFormat.getIntegerInstance(nl).format(n)

    private fun cal(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }

    fun date(ms: Long) = cal(ms).let { "${it.get(Calendar.DAY_OF_MONTH)} ${months[it.get(Calendar.MONTH)]} ${it.get(Calendar.YEAR)}" }
    fun dateShort(ms: Long) = cal(ms).let { "${it.get(Calendar.DAY_OF_MONTH)} ${months[it.get(Calendar.MONTH)]}" }
    fun time(ms: Long) = cal(ms).let { "${pad(it.get(Calendar.HOUR_OF_DAY).toLong())}:${pad(it.get(Calendar.MINUTE).toLong())}" }
    fun range(startMs: Long, durSec: Long) = time(startMs) + "–" + time(startMs + durSec * 1000)

    fun hourOf(ms: Long) = cal(ms).get(Calendar.HOUR_OF_DAY)

    fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
