package app.aaps.wear.data

data class TreatmentItem(
    val timestamp: Long,
    val bolus: Double,
    val carbs: Double,
    val isSMB: Boolean,
    val isValid: Boolean
)
