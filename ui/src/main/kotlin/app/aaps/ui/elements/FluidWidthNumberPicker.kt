package app.aaps.ui.elements

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import app.aaps.core.ui.databinding.NumberPickerLayoutBinding
import app.aaps.core.ui.elements.NumberPicker
import app.aaps.core.ui.elements.NumberPickerViewAdapter
import app.aaps.ui.R

/**
 * NumberPicker that fills its parent width instead of using the default fixed 130dp.
 */
class FluidWidthNumberPicker(context: Context, attrs: AttributeSet? = null) : NumberPicker(context, attrs) {

    override fun inflate(context: Context) {
        val inflater = LayoutInflater.from(context)
        inflater.inflate(R.layout.number_picker_fluid, this, true)
        binding = NumberPickerViewAdapter(
            NumberPickerLayoutBinding.bind(getChildAt(0)),
            null
        )
    }
}
