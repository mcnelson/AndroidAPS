package app.aaps.wear.interaction.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import app.aaps.wear.R
import app.aaps.wear.data.TreatmentItem
import app.aaps.wear.interaction.utils.DisplayFormat
import java.util.Locale

class TreatmentsAdapter(
    private val items: List<TreatmentItem>,
    private val displayFormat: DisplayFormat
) : RecyclerView.Adapter<TreatmentsAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val timeText: TextView = view.findViewById(R.id.treatment_time)
        val typeIcon: ImageView = view.findViewById(R.id.treatment_icon)
        val bolusText: TextView = view.findViewById(R.id.treatment_bolus)
        val carbsText: TextView = view.findViewById(R.id.treatment_carbs)
        val smbIndicator: TextView = view.findViewById(R.id.treatment_smb_indicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.treatments_log_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val resources = holder.itemView.context.resources

        holder.timeText.text = displayFormat.shortTimeSince(item.timestamp)

        val hasBolus = item.bolus > 0
        val hasCarbs = item.carbs > 0

        when {
            hasBolus && hasCarbs -> {
                holder.typeIcon.setImageResource(R.drawable.ic_bolus_carbs)
                holder.typeIcon.setColorFilter(resources.getColor(R.color.carbs, null))
            }
            hasBolus -> {
                holder.typeIcon.setImageResource(R.drawable.ic_bolus)
                holder.typeIcon.setColorFilter(resources.getColor(R.color.inrange, null))
            }
            hasCarbs -> {
                holder.typeIcon.setImageResource(R.drawable.ic_carbs)
                holder.typeIcon.setColorFilter(resources.getColor(R.color.carbs, null))
            }
            else -> {
                holder.typeIcon.setImageResource(R.drawable.ic_treatment)
                holder.typeIcon.setColorFilter(resources.getColor(R.color.dark_midColor, null))
            }
        }

        if (hasBolus) {
            holder.bolusText.text = String.format(Locale.getDefault(), "%.2f U", item.bolus)
            holder.bolusText.visibility = View.VISIBLE
        } else {
            holder.bolusText.visibility = View.GONE
        }

        if (hasCarbs) {
            holder.carbsText.text = String.format(Locale.getDefault(), "%.0f g", item.carbs)
            holder.carbsText.visibility = View.VISIBLE
        } else {
            holder.carbsText.visibility = View.GONE
        }

        if (item.isSMB && hasBolus) {
            holder.smbIndicator.visibility = View.VISIBLE
        } else {
            holder.smbIndicator.visibility = View.GONE
        }

        if (!item.isValid) {
            holder.itemView.alpha = 0.4f
        } else {
            holder.itemView.alpha = 1.0f
        }
    }

    override fun getItemCount(): Int = items.size
}
