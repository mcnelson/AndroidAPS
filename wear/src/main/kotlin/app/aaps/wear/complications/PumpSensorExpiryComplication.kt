package app.aaps.wear.complications

import android.app.PendingIntent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import app.aaps.core.interfaces.logging.LTag
import dagger.android.AndroidInjection
import kotlin.math.max

class PumpSensorExpiryComplication : ModernBaseComplicationProviderService() {

    companion object {

        private const val CANNULA_MAX_HOURS = 80L
        private const val SENSOR_MAX_HOURS = 360L
        private const val RESERVOIR_LOW_THRESHOLD = 50.0
    }

    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override fun buildComplicationData(
        type: ComplicationType,
        data: app.aaps.wear.data.ComplicationData,
        complicationPendingIntent: PendingIntent
    ): ComplicationData? {
        aapsLogger.debug(LTag.WEAR, "PumpSensorExpiryComplication.buildComplicationData: type=$type")
        val statusData = data.statusData

        val now = System.currentTimeMillis()
        val pumpRemaining = remainingHours(statusData.cannulaChangeTimestamp, now, CANNULA_MAX_HOURS)
        val sensorRemaining = remainingHours(statusData.sensorChangeTimestamp, now, SENSOR_MAX_HOURS)

        val pumpStr = if (statusData.cannulaChangeTimestamp > 0) pumpRemaining.toString() else "?"
        val sensorStr = if (statusData.sensorChangeTimestamp > 0) sensorRemaining.toString() else "?"
        val reservoir = statusData.reservoir
        val displayText = if (reservoir in 0.0..<RESERVOIR_LOW_THRESHOLD)
            "$sensorStr H  $pumpStr H  ${reservoir.toInt()} U"
        else
            "$sensorStr H  $pumpStr H"

        return when (type) {
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(text = displayText).build(),
                    contentDescription = PlainComplicationText.Builder(text = "Pump ${pumpStr}h Sensor ${sensorStr}h remaining").build()
                )
                    .setTapAction(complicationPendingIntent)
                    .build()
            }

            else                       -> {
                aapsLogger.warn(LTag.WEAR, "Unexpected complication type $type")
                null
            }
        }
    }

    private fun remainingHours(changeTimestamp: Long, now: Long, maxHours: Long): Long {
        val elapsedMs = now - changeTimestamp
        val elapsedHours = elapsedMs / (1000 * 60 * 60)
        return max(0, maxHours - elapsedHours)
    }

    override fun getProviderCanonicalName(): String = PumpSensorExpiryComplication::class.java.canonicalName!!
}
