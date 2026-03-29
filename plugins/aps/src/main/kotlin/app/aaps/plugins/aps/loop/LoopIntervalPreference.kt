package app.aaps.plugins.aps.loop

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.data.time.T
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.aps.R
import com.google.android.material.button.MaterialButtonToggleGroup
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class LoopIntervalPreference(
    ctx: Context,
    attrs: AttributeSet? = null
) : Preference(ctx, attrs) {

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var persistenceLayer: PersistenceLayer

    private val valueToButton = mapOf(
        0 to R.id.btn_auto,
        1 to R.id.btn_1,
        2 to R.id.btn_2,
        3 to R.id.btn_3,
        4 to R.id.btn_4,
        5 to R.id.btn_5
    )
    private val buttonToValue = valueToButton.entries.associate { it.value to it.key }

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        layoutResource = R.layout.preference_loop_interval
        key = IntKey.LoopMinBgRecalcInterval.key
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.isDividerAllowedAbove = false
        holder.isDividerAllowedBelow = false
        // Disable click on the row background so taps reach the toggle buttons instead
        holder.itemView.isClickable = false

        val titleView = holder.findViewById(R.id.title) as? TextView
        val dynamicSummary = holder.findViewById(R.id.dynamic_summary) as? TextView
        val warningText = holder.findViewById(R.id.warning_text) as? TextView
        val helpText = holder.findViewById(R.id.help_text) as? TextView
        val toggleGroup = holder.findViewById(R.id.toggle_group) as? MaterialButtonToggleGroup

        titleView?.text = rh.gs(R.string.loop_min_bg_recalc_interval_title)
        helpText?.text = rh.gs(R.string.loop_min_bg_recalc_interval_summary)

        val currentValue = preferences.get(IntKey.LoopMinBgRecalcInterval)
        updateDynamicText(dynamicSummary, warningText, currentValue)

        toggleGroup?.clearOnButtonCheckedListeners()
        valueToButton[currentValue]?.let { toggleGroup?.check(it) }

        toggleGroup?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val minutes = buttonToValue[checkedId] ?: return@addOnButtonCheckedListener
            preferences.put(IntKey.LoopMinBgRecalcInterval, minutes)
            updateDynamicText(dynamicSummary, warningText, minutes)
        }
    }

    private fun updateDynamicText(summaryView: TextView?, warningText: TextView?, minutes: Int) {
        val now = dateUtil.now()
        val dailyCount = persistenceLayer.getApsResults(now - T.hours(24).msecs(), now).size
        val loopsPerDay = if (minutes == 0) dailyCount else 1440 / minutes

        // 1st line: always white, describes selected mode
        summaryView?.text = if (minutes == 0) rh.gs(R.string.loop_recalc_every_sensor_reading)
            else rh.gs(R.string.loop_recalc_loops_per_day, loopsPerDay)

        // 2nd line: red warning, shown if actual or projected high usage
        if (dailyCount > 500) { // actual high usage
            warningText?.text = rh.gs(R.string.loop_recalc_daily_count_warning, dailyCount)
            warningText?.setTextColor(rh.gc(app.aaps.core.ui.R.color.warning))
        } else if (minutes in 1..2) { // low data but high-rate selection
            warningText?.text = rh.gs(R.string.loop_recalc_high_rate_warning)
            warningText?.setTextColor(rh.gc(app.aaps.core.ui.R.color.warning))
        } else {
            warningText?.text = rh.gs(R.string.loop_recalc_daily_count, dailyCount)
            warningText?.setTextColor(rh.gc(android.R.color.white))
        }
    }
}
