package app.aaps.wear.complications

import android.app.PendingIntent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import app.aaps.core.interfaces.logging.LTag
import dagger.android.AndroidInjection

/**
 * COB+IOB Complication
 *
 * Shows both carbs on board (COB) and insulin on board (IOB)
 * Text: COB value
 * Title: IOB value (minimized to fit)
 * When negative basal IOB is masking real bolus insulin, appends "+" to net IOB
 * When there's an active bolus session, appends "Y.Y S" (total insulin since IOB last neared zero)
 *
 */
class CobIobComplication : ModernBaseComplicationProviderService() {

    // Not derived from DaggerService, do injection here
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

        return when (type) {
            ComplicationType.SHORT_TEXT      -> {
                val cob = statusData.cob
                val basalIob = statusData.basalIob
                val iobDisplay = if (basalIob < -0.1) "${statusData.iobSum}+" else statusData.iobSum
                val sessionSuffix = if (statusData.bolusSession > 0.05) "  ${"%.1f".format(statusData.bolusSession)} S" else ""
                val displayText = (if (cob.equals("0g", ignoreCase = true)) iobDisplay else "$cob   $iobDisplay") + sessionSuffix
                aapsLogger.debug(LTag.WEAR, "CobIobComplication: bolusSession=${statusData.bolusSession} sessionSuffix='$sessionSuffix' displayText='$displayText'")

                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(text = displayText).build(),
                    contentDescription = PlainComplicationText.Builder(text = "COB $cob IOB $iobDisplay").build()
                )
                    .setTapAction(complicationPendingIntent)
                    .build()
            }

            else                             -> {
                aapsLogger.warn(LTag.WEAR, "Unexpected complication type $type")
                null
            }
        }
    }

    override fun getProviderCanonicalName(): String = CobIobComplication::class.java.canonicalName!!
}
