package app.aaps.wear.complications

import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import app.aaps.core.interfaces.logging.LTag
import app.aaps.wear.interaction.utils.GlucoseColorHelper
import dagger.android.AndroidInjection

/**
 * Complication that displays a colored dot representing current glucose level.
 */
class GlucoseColorDotComplication : ModernBaseComplicationProviderService() {

    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override fun buildComplicationData(
        type: ComplicationType,
        data: app.aaps.wear.data.ComplicationData,
        complicationPendingIntent: PendingIntent
    ): ComplicationData? {
        val mgdl = data.bgData.sgv.toInt()
        val color = GlucoseColorHelper.getColorForGlucoseValue(mgdl)

        return when (type) {
            ComplicationType.SMALL_IMAGE -> {
                val bitmap = createColoredDotBitmap(color, 128)
                val burnInBitmap = createColoredDotBitmap(color, 128, burnIn = true)

                SmallImageComplicationData.Builder(
                    smallImage = SmallImage.Builder(
                        image = Icon.createWithBitmap(bitmap),
                        type = SmallImageType.PHOTO // Prevents tinting
                    )
                        .setAmbientImage(Icon.createWithBitmap(burnInBitmap))
                        .build(),
                    contentDescription = PlainComplicationText.Builder(text = "Glucose $mgdl").build()
                )
                    .setTapAction(complicationPendingIntent)
                    .build()
            }

            else                         -> {
                aapsLogger.warn(LTag.WEAR, "Unexpected complication type $type")
                null
            }
        }
    }

    private fun createColoredDotBitmap(color: Int, size: Int, burnIn: Boolean = false): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint().apply {
            isAntiAlias = !burnIn
            style = Paint.Style.FILL
            this.color = color
        }

        val radius = size / 2f
        val centerX = size / 2f
        val centerY = size / 2f

        if (burnIn) {
            canvas.drawCircle(centerX, centerY, radius * 0.7f, paint)
        } else {
            canvas.drawCircle(centerX, centerY, radius * 0.9f, paint)
        }

        return bitmap
    }

    override fun getProviderCanonicalName(): String = GlucoseColorDotComplication::class.java.canonicalName!!
}
