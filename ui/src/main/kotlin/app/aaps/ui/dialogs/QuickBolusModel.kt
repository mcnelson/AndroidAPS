package app.aaps.ui.dialogs

import kotlin.math.abs

class QuickBolusModel(
    val maxInsulin: Double,
    val maxCarbs: Double,
    val bolusStep: Double,
    initialIob: Double = 0.0,
    initialBasalIob: Double = 0.0,
    initialStepModeIndex: Int = 1
) {

    companion object {
        private val stepModes = listOf(
            Triple(0.1, 1.0, "0.1"),
            Triple(0.5, 5.0, "0.5"),
            Triple(1.0, 10.0, "1")
        )

        fun adjustSteppedValue(current: Double, stepSize: Double, direction: Int, suggestion: Double = 0.0): Double {
            val round = if (direction > 0) Math::ceil else Math::floor
            val currentInSteps = current / stepSize
            val gridValueInSteps = if (isOnGrid(current, stepSize)) currentInSteps + direction else round(currentInSteps)
            val resultValue = gridValueInSteps * stepSize

            val suggestionWanted = suggestion > 0.0 && !isOnGrid(suggestion, stepSize)
            val suggestionInRange = (suggestion - current) * direction > 0 && (resultValue - suggestion) * direction > 0
            if (suggestionWanted && suggestionInRange) return suggestion

            return resultValue.coerceAtLeast(0.0)
        }

        fun calculateCarbAmount(currentAmount: Double, currentIc: Double, carbDeficit: Int, currentCarbAmount: Int): Int =
            when {
                currentIc > 0.0 && currentAmount > 0.0 -> (currentAmount * currentIc).toInt()
                carbDeficit > 0                         -> carbDeficit
                else                                    -> currentCarbAmount
            }

        internal fun isOnGrid(value: Double, stepSize: Double): Boolean =
            value == Math.round(value / stepSize) * stepSize

        fun buildIobText(label: String, iob: Double, currentBasalIob: Double): String {
            val displayIob = if (Math.abs(iob) < 0.05) 0.0 else iob
            val base = label + "%.1f".format(displayIob) + " U"
            return if (currentBasalIob < -0.1) "$base  ${"%.1f".format(iob - currentBasalIob)} real" else base
        }
    }

    // Mutable state
    var stepModeIndex: Int = initialStepModeIndex.coerceIn(0, stepModes.size - 1)
    var insulinValue: Double = 0.0
    var carbsValue: Double = 0.0
    var eatingChecked: Boolean = false
    var wizardSuggestion: Double = 0.0
    var carbDeficit: Int = 0
    var currentIc: Double = 0.0
    var currentIob: Double = initialIob
    var currentBasalIob: Double = initialBasalIob
    var userAdjustedCarbs: Boolean = false

    // Derived state
    val insulinStep: Double get() = stepModes[stepModeIndex].first
    val carbStep: Double get() = stepModes[stepModeIndex].second
    val stepModeLabel: String get() = stepModes[stepModeIndex].third

    val isOnInsulinSuggestion: Boolean
        get() = wizardSuggestion > 0.0 && abs(insulinValue - wizardSuggestion) < 0.01

    val isAtCarbDeficit: Boolean
        get() = carbDeficit > 0 && carbsValue.toInt() == carbDeficit

    val shouldShowCarbDeficitHint: Boolean
        get() = carbDeficit > 0

    val projectedIob: Double
        get() = currentIob + insulinValue

    val hasNegativeBasalIob: Boolean
        get() = currentBasalIob < -0.1

    // Events

    /** Step insulin down. Returns true if value changed. */
    fun onInsulinDown(): Boolean {
        val next = adjustSteppedValue(insulinValue, insulinStep, -1, wizardSuggestion)
        if (next < 0.0 || next == insulinValue) return false
        insulinValue = next
        syncCarbsToInsulin()
        return true
    }

    /** Step insulin up. Returns true if value changed. Returns false if at max. */
    fun onInsulinUp(): Boolean {
        val next = adjustSteppedValue(insulinValue, insulinStep, 1, wizardSuggestion)
        if (next > maxInsulin) return false
        insulinValue = next
        syncCarbsToInsulin()
        return true
    }

    /** Step carbs down. Returns true if value changed. */
    fun onCarbsDown(): Boolean {
        val next = adjustSteppedValue(carbsValue, carbStep, -1, carbDeficit.toDouble())
        if (next < 0.0 || next == carbsValue) return false
        carbsValue = next
        userAdjustedCarbs = next.toInt() != carbDeficit
        return true
    }

    /** Step carbs up. Returns true if value changed. */
    fun onCarbsUp(): Boolean {
        val next = adjustSteppedValue(carbsValue, carbStep, 1, carbDeficit.toDouble())
        if (next > maxCarbs) return false
        carbsValue = next
        userAdjustedCarbs = next.toInt() != carbDeficit
        return true
    }

    /** Toggle eating checkbox. Manages carb auto-population and userAdjustedCarbs reset. */
    fun onEatingToggled(checked: Boolean) {
        eatingChecked = checked
        if (!checked) {
            carbsValue = 0.0
            userAdjustedCarbs = false
        } else if (!userAdjustedCarbs) {
            carbsValue = calculateCarbAmount(insulinValue, currentIc, carbDeficit, 0).toDouble()
            userAdjustedCarbs = false
        }
    }

    /** Cycle step mode. Returns new stepModeIndex for SP persistence. */
    fun onStepModeChanged(): Int {
        stepModeIndex = (stepModeIndex + 1) % stepModes.size
        return stepModeIndex
    }

    /** Called when wizard result arrives. Returns true if insulin was auto-filled (value was 0). */
    fun onWizardResult(suggestion: Double, deficit: Int, ic: Double): Boolean {
        wizardSuggestion = suggestion
        carbDeficit = deficit
        currentIc = ic
        val autoFilled = insulinValue == 0.0 && suggestion > 0.0
        if (autoFilled) {
            insulinValue = suggestion
        }
        if (insulinValue > 0.0 && eatingChecked && !userAdjustedCarbs) {
            carbsValue = calculateCarbAmount(insulinValue, currentIc, carbDeficit, 0).toDouble()
        }
        return autoFilled
    }

    // Save/restore

    data class SavedState(
        val insulinValue: Double,
        val carbsValue: Double,
        val eatingChecked: Boolean,
        val userAdjustedCarbs: Boolean,
        val wizardSuggestion: Double,
        val currentIob: Double,
        val currentBasalIob: Double,
        val currentIc: Double,
        val carbDeficit: Int
    )

    fun saveState(): SavedState = SavedState(
        insulinValue = insulinValue,
        carbsValue = carbsValue,
        eatingChecked = eatingChecked,
        userAdjustedCarbs = userAdjustedCarbs,
        wizardSuggestion = wizardSuggestion,
        currentIob = currentIob,
        currentBasalIob = currentBasalIob,
        currentIc = currentIc,
        carbDeficit = carbDeficit
    )

    fun restoreState(state: SavedState) {
        insulinValue = state.insulinValue
        carbsValue = state.carbsValue
        eatingChecked = state.eatingChecked
        userAdjustedCarbs = state.userAdjustedCarbs
        wizardSuggestion = state.wizardSuggestion
        currentIob = state.currentIob
        currentBasalIob = state.currentBasalIob
        currentIc = state.currentIc
        carbDeficit = state.carbDeficit
    }

    private fun syncCarbsToInsulin() {
        if (eatingChecked && !userAdjustedCarbs) {
            carbsValue = calculateCarbAmount(insulinValue, currentIc, carbDeficit, carbsValue.toInt()).toDouble()
        }
    }
}
