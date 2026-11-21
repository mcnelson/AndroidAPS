package app.aaps.wear.complications

import android.app.PendingIntent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import app.aaps.core.interfaces.logging.LTag
import app.aaps.wear.R
import dagger.android.AndroidInjection

/**
 * Basal Rate + Temp Target Complication
 *
 * Shows current basal rate, and when a temp target is active, appends the TT value
 * with the AAPS temp target icon.
 * Format: "⎍ 1.2" or "⎍ 1.2  100" + TT icon
 */
class BrTtComplication : ModernBaseComplicationProviderService() {

    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override fun buildComplicationData(
        type: ComplicationType,
        data: app.aaps.wear.data.ComplicationData,
        complicationPendingIntent: PendingIntent
    ): ComplicationData? {
        val statusData = data.statusData
        val hasTT = statusData.tempTargetLevel > 0 && statusData.tempTarget.isNotEmpty()
        val basalText = displayFormat.basalRateSymbol() + statusData.currentBasal
        val ttText = if (hasTT && statusData.tempTargetEndTimestamp > 0) {
            val minutesRemaining = ((statusData.tempTargetEndTimestamp - System.currentTimeMillis()) / 60000).coerceAtLeast(0)
            "${statusData.tempTarget}  ${minutesRemaining}'"
        } else {
            statusData.tempTarget
        }
        val displayText = if (hasTT || true)
            "$basalText  $ttText"
        else
            basalText

        return when (type) {
            ComplicationType.SHORT_TEXT -> {
                val builder = ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(text = displayText).build(),
                    contentDescription = PlainComplicationText.Builder(text = "Basal Rate and Temp Target").build()
                )
                    .setTapAction(complicationPendingIntent)

                if (hasTT) {
                    builder.setMonochromaticImage(
                        MonochromaticImage.Builder(
                            image = Icon.createWithResource(this, R.drawable.ic_temptarget_flat)
                        ).build()
                    )
                }

                builder.build()
            }

            else                       -> {
                aapsLogger.warn(LTag.WEAR, "Unexpected complication type $type")
                null
            }
        }
    }

    override fun getProviderCanonicalName(): String = BrTtComplication::class.java.canonicalName!!
}
