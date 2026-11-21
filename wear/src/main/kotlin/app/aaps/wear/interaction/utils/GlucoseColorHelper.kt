package app.aaps.wear.interaction.utils

import android.graphics.Color
import androidx.annotation.ColorInt

object GlucoseColorHelper {

    @ColorInt
    fun getColorForGlucoseValue(mgdl: Int): Int {
        return when {
            mgdl < 0    -> Color.parseColor("#999999")
            mgdl >= 200 -> Color.parseColor("#ff0000")
            mgdl < 40   -> Color.parseColor("#0000ff")
            mgdl >= 120 -> {
                val indices = highHexCharacterIndices(mgdl)
                val hexColor = String.format("#%s%s00",
                    repeatChar(indices[0]),
                    repeatChar(indices[1]))
                Color.parseColor(hexColor)
            }
            mgdl < 70   -> {
                val indices = lowHexCharacterIndices(mgdl)
                val hexColor = String.format("#00%s%s",
                    repeatChar(indices[0]),
                    repeatChar(indices[1]))
                Color.parseColor(hexColor)
            }
            else        -> Color.parseColor("#00ff00")
        }
    }

    private fun highHexCharacterIndices(input: Int): IntArray {
        val hexChrCount = 15
        val red: Int
        val green: Int

        if (input < 160) {
            val proportionBad = (input - 120f) / 40f
            red = (proportionBad * hexChrCount).toInt()
            green = hexChrCount
        } else {
            val proportionBad = (input - 160f) / 40f
            red = hexChrCount
            green = hexChrCount - (proportionBad * hexChrCount).toInt()
        }

        return intArrayOf(red, green)
    }

    private fun lowHexCharacterIndices(input: Int): IntArray {
        val hexChrCount = 15
        val green: Int
        val blue: Int

        if (input > 55) {
            val proportionBad = (input - 55f) / 15f
            blue = hexChrCount - (proportionBad * hexChrCount).toInt()
            green = hexChrCount
        } else {
            val proportionBad = (input - 40f) / 15f
            blue = hexChrCount
            green = (proportionBad * hexChrCount).toInt()
        }

        return intArrayOf(green, blue)
    }

    private fun repeatChar(index: Int): String {
        val clampedIndex = index.coerceIn(0, 15)
        val hexChar = Integer.toHexString(clampedIndex)
        return hexChar + hexChar
    }
}
