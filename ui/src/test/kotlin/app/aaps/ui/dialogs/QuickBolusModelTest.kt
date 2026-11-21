package app.aaps.ui.dialogs

import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class QuickBolusModelTest : TestBase() {

    private val tolerance = 0.01

    private fun model(
        maxInsulin: Double = 5.0,
        maxCarbs: Double = 100.0,
        bolusStep: Double = 0.1,
        iob: Double = 0.0,
        basalIob: Double = 0.0,
        stepModeIndex: Int = 1 // 0.5 U / 5 g
    ) = QuickBolusModel(maxInsulin, maxCarbs, bolusStep, iob, basalIob, stepModeIndex)

    @Nested
    inner class Initialization {

        @Test
        fun `new model starts at zero with no suggestions`() {
            val m = model()
            assertThat(m.insulinValue).isEqualTo(0.0)
            assertThat(m.carbsValue).isEqualTo(0.0)
            assertThat(m.eatingChecked).isFalse()
            assertThat(m.userAdjustedCarbs).isFalse()
            assertThat(m.wizardSuggestion).isEqualTo(0.0)
            assertThat(m.carbDeficit).isEqualTo(0)
            assertThat(m.isOnInsulinSuggestion).isFalse()
            assertThat(m.isAtCarbDeficit).isFalse()
            assertThat(m.shouldShowCarbDeficitHint).isFalse()
        }

        @Test
        fun `constructor sets IOB and step mode`() {
            val m = model(iob = 3.5, basalIob = -0.3, stepModeIndex = 0)
            assertThat(m.currentIob).isWithin(tolerance).of(3.5)
            assertThat(m.currentBasalIob).isWithin(tolerance).of(-0.3)
            assertThat(m.projectedIob).isWithin(tolerance).of(3.5) // no insulin yet
            assertThat(m.hasNegativeBasalIob).isTrue()
            assertThat(m.insulinStep).isWithin(tolerance).of(0.1)
            assertThat(m.carbStep).isWithin(tolerance).of(1.0)
            assertThat(m.stepModeLabel).isEqualTo("0.1")
        }
    }

    @Nested
    inner class InsulinStepping {

        @Test
        fun `steps up and down on grid`() {
            val m = model()
            assertThat(m.onInsulinUp()).isTrue()
            assertThat(m.insulinValue).isWithin(tolerance).of(0.5)
            assertThat(m.onInsulinUp()).isTrue()
            assertThat(m.insulinValue).isWithin(tolerance).of(1.0)
            assertThat(m.onInsulinDown()).isTrue()
            assertThat(m.insulinValue).isWithin(tolerance).of(0.5)
            assertThat(m.onInsulinDown()).isTrue()
            assertThat(m.insulinValue).isWithin(tolerance).of(0.0)
        }

        @Test
        fun `down at zero returns false`() {
            val m = model()
            assertThat(m.onInsulinDown()).isFalse()
            assertThat(m.insulinValue).isEqualTo(0.0)
        }

        @Test
        fun `up at max returns false`() {
            val m = model(maxInsulin = 1.0)
            m.insulinValue = 1.0
            assertThat(m.onInsulinUp()).isFalse()
            assertThat(m.insulinValue).isWithin(tolerance).of(1.0)
        }

        @Test
        fun `snaps to wizard suggestion going up`() {
            val m = model()
            m.wizardSuggestion = 1.7
            m.insulinValue = 1.5
            assertThat(m.onInsulinUp()).isTrue()
            assertThat(m.insulinValue).isWithin(tolerance).of(1.7)
            assertThat(m.isOnInsulinSuggestion).isTrue()
            assertThat(m.onInsulinUp()).isTrue()
            assertThat(m.insulinValue).isWithin(tolerance).of(2.0)
            assertThat(m.isOnInsulinSuggestion).isFalse()
        }

        @Test
        fun `snaps to wizard suggestion going down`() {
            val m = model()
            m.wizardSuggestion = 1.7
            m.insulinValue = 2.0
            assertThat(m.onInsulinDown()).isTrue()
            assertThat(m.insulinValue).isWithin(tolerance).of(1.7)
            assertThat(m.onInsulinDown()).isTrue()
            assertThat(m.insulinValue).isWithin(tolerance).of(1.5)
        }

        @Test
        fun `starting at wizard suggestion steps away cleanly`() {
            val m = model()
            m.wizardSuggestion = 1.7
            m.insulinValue = 1.7
            assertThat(m.isOnInsulinSuggestion).isTrue()
            assertThat(m.onInsulinUp()).isTrue()
            assertThat(m.insulinValue).isWithin(tolerance).of(2.0)
            assertThat(m.onInsulinDown()).isTrue()
            assertThat(m.insulinValue).isWithin(tolerance).of(1.7)
        }
    }

    @Nested
    inner class CarbsStepping {

        @Test
        fun `steps up and down on grid`() {
            val m = model()
            m.eatingChecked = true
            m.userAdjustedCarbs = true
            assertThat(m.onCarbsUp()).isTrue()
            assertThat(m.carbsValue).isWithin(tolerance).of(5.0)
            assertThat(m.onCarbsUp()).isTrue()
            assertThat(m.carbsValue).isWithin(tolerance).of(10.0)
            assertThat(m.onCarbsDown()).isTrue()
            assertThat(m.carbsValue).isWithin(tolerance).of(5.0)
            assertThat(m.onCarbsDown()).isTrue()
            assertThat(m.carbsValue).isWithin(tolerance).of(0.0)
        }

        @Test
        fun `down at zero returns false`() {
            val m = model()
            assertThat(m.onCarbsDown()).isFalse()
        }

        @Test
        fun `snaps to deficit`() {
            val m = model()
            m.carbDeficit = 17
            m.carbsValue = 15.0
            assertThat(m.onCarbsUp()).isTrue()
            assertThat(m.carbsValue).isWithin(tolerance).of(17.0)
            assertThat(m.isAtCarbDeficit).isTrue()
            assertThat(m.userAdjustedCarbs).isFalse()
        }

        @Test
        fun `stepping away from deficit sets userAdjustedCarbs`() {
            val m = model()
            m.carbDeficit = 17
            m.carbsValue = 17.0
            m.userAdjustedCarbs = false
            assertThat(m.onCarbsUp()).isTrue()
            assertThat(m.carbsValue).isWithin(tolerance).of(20.0)
            assertThat(m.userAdjustedCarbs).isTrue()
        }

        @Test
        fun `stepping back to deficit clears userAdjustedCarbs`() {
            val m = model()
            m.carbDeficit = 17
            m.carbsValue = 20.0
            m.userAdjustedCarbs = true
            assertThat(m.onCarbsDown()).isTrue()
            assertThat(m.carbsValue).isWithin(tolerance).of(17.0)
            assertThat(m.userAdjustedCarbs).isFalse()
        }
    }

    @Nested
    inner class EatingToggle {

        @Test
        fun `on with deficit auto-populates carbs`() {
            val m = model()
            m.carbDeficit = 17
            m.onEatingToggled(true)
            assertThat(m.eatingChecked).isTrue()
            assertThat(m.carbsValue).isWithin(tolerance).of(17.0)
            assertThat(m.userAdjustedCarbs).isFalse()
            assertThat(m.isAtCarbDeficit).isTrue()
        }

        @Test
        fun `on with IC and insulin uses IC ratio`() {
            val m = model()
            m.currentIc = 10.0
            m.insulinValue = 1.5
            m.carbDeficit = 17
            m.onEatingToggled(true)
            assertThat(m.carbsValue).isWithin(tolerance).of(15.0)
        }

        @Test
        fun `off resets carbs and userAdjustedCarbs`() {
            val m = model()
            m.carbDeficit = 17
            m.onEatingToggled(true)
            assertThat(m.carbsValue).isWithin(tolerance).of(17.0)
            m.onEatingToggled(false)
            assertThat(m.carbsValue).isEqualTo(0.0)
            assertThat(m.userAdjustedCarbs).isFalse()
            assertThat(m.eatingChecked).isFalse()
        }

        @Test
        fun `off then on re-engages auto-populate`() {
            val m = model()
            m.carbDeficit = 17
            m.onEatingToggled(true)
            m.carbsValue = 30.0
            m.userAdjustedCarbs = true
            m.onEatingToggled(false)
            m.onEatingToggled(true)
            assertThat(m.carbsValue).isWithin(tolerance).of(17.0)
            assertThat(m.userAdjustedCarbs).isFalse()
        }
    }

    @Nested
    inner class InsulinCarbsSync {

        @Test
        fun `insulin steps sync carbs via IC when eating is checked`() {
            val m = model()
            m.onWizardResult(suggestion = 1.3, deficit = 13, ic = 10.0)
            m.onEatingToggled(true)
            assertThat(m.insulinValue).isWithin(tolerance).of(1.3)
            assertThat(m.carbsValue).isWithin(tolerance).of(13.0)

            m.onInsulinUp() // 1.3 → 1.5
            assertThat(m.insulinValue).isWithin(tolerance).of(1.5)
            assertThat(m.carbsValue).isWithin(tolerance).of(15.0)

            m.onInsulinUp() // 1.5 → 2.0
            assertThat(m.insulinValue).isWithin(tolerance).of(2.0)
            assertThat(m.carbsValue).isWithin(tolerance).of(20.0)

            m.onInsulinDown() // 2.0 → 1.5
            assertThat(m.insulinValue).isWithin(tolerance).of(1.5)
            assertThat(m.carbsValue).isWithin(tolerance).of(15.0)

            m.onInsulinDown() // 1.5 → 1.3 (wizard snap)
            assertThat(m.insulinValue).isWithin(tolerance).of(1.3)
            assertThat(m.carbsValue).isWithin(tolerance).of(13.0)

            m.onInsulinDown() // 1.3 → 1.0
            assertThat(m.insulinValue).isWithin(tolerance).of(1.0)
            assertThat(m.carbsValue).isWithin(tolerance).of(10.0)
        }

        @Test
        fun `user-adjusted carbs breaks sync`() {
            val m = model()
            m.onWizardResult(suggestion = 1.0, deficit = 0, ic = 10.0)
            m.onEatingToggled(true)
            assertThat(m.carbsValue).isWithin(tolerance).of(10.0)

            m.carbsValue = 25.0
            m.userAdjustedCarbs = true

            m.onInsulinUp() // 1.0 → 1.5
            assertThat(m.insulinValue).isWithin(tolerance).of(1.5)
            assertThat(m.carbsValue).isWithin(tolerance).of(25.0)
        }

        @Test
        fun `eating toggle re-engages sync after user adjustment`() {
            val m = model()
            m.onWizardResult(suggestion = 1.0, deficit = 0, ic = 10.0)
            m.onEatingToggled(true)
            m.carbsValue = 25.0
            m.userAdjustedCarbs = true

            m.onEatingToggled(false)
            m.onEatingToggled(true)
            assertThat(m.carbsValue).isWithin(tolerance).of(10.0)
            assertThat(m.userAdjustedCarbs).isFalse()

            m.onInsulinUp() // 1.0 → 1.5
            assertThat(m.carbsValue).isWithin(tolerance).of(15.0)
        }

        @Test
        fun `no sync when eating unchecked`() {
            val m = model()
            m.onWizardResult(suggestion = 1.0, deficit = 0, ic = 10.0)
            assertThat(m.carbsValue).isEqualTo(0.0)
            m.onInsulinUp() // 1.0 → 1.5
            assertThat(m.carbsValue).isEqualTo(0.0)
        }
    }

    @Nested
    inner class FullScenarios {

        @Test
        fun `wizard 1_7U with 17g deficit`() {
            val m = model(iob = 2.0, basalIob = -0.3)
            m.onWizardResult(suggestion = 1.7, deficit = 17, ic = 10.0)
            assertThat(m.insulinValue).isWithin(tolerance).of(1.7)
            assertThat(m.isOnInsulinSuggestion).isTrue()
            assertThat(m.projectedIob).isWithin(tolerance).of(3.7)
            assertThat(m.shouldShowCarbDeficitHint).isTrue()

            m.onEatingToggled(true)
            assertThat(m.carbsValue).isWithin(tolerance).of(17.0)
            assertThat(m.isAtCarbDeficit).isTrue()

            m.onInsulinUp() // 1.7 → 2.0
            assertThat(m.insulinValue).isWithin(tolerance).of(2.0)
            assertThat(m.carbsValue).isWithin(tolerance).of(20.0)
            assertThat(m.isOnInsulinSuggestion).isFalse()
            assertThat(m.isAtCarbDeficit).isFalse()

            m.onInsulinDown() // 2.0 → 1.7
            assertThat(m.insulinValue).isWithin(tolerance).of(1.7)
            assertThat(m.carbsValue).isWithin(tolerance).of(17.0)
            assertThat(m.isOnInsulinSuggestion).isTrue()
            assertThat(m.isAtCarbDeficit).isTrue()

            m.onInsulinDown() // 1.7 → 1.5
            assertThat(m.insulinValue).isWithin(tolerance).of(1.5)
            assertThat(m.carbsValue).isWithin(tolerance).of(15.0)

            m.onInsulinDown() // 1.5 → 1.0
            m.onInsulinDown() // 1.0 → 0.5
            m.onInsulinDown() // 0.5 → 0.0
            assertThat(m.insulinValue).isWithin(tolerance).of(0.0)
            assertThat(m.carbsValue).isWithin(tolerance).of(17.0) // deficit fallback
            assertThat(m.projectedIob).isWithin(tolerance).of(2.0)
        }

        @Test
        fun `no wizard suggestion, user builds from scratch`() {
            val m = model()
            m.onEatingToggled(true)
            assertThat(m.carbsValue).isEqualTo(0.0)

            m.onInsulinUp() // 0 → 0.5
            assertThat(m.insulinValue).isWithin(tolerance).of(0.5)
            assertThat(m.carbsValue).isEqualTo(0.0)

            m.onCarbsUp() // 0 → 5
            assertThat(m.carbsValue).isWithin(tolerance).of(5.0)
            assertThat(m.userAdjustedCarbs).isTrue()

            m.onInsulinUp() // 0.5 → 1.0
            assertThat(m.carbsValue).isWithin(tolerance).of(5.0)
        }

        @Test
        fun `deficit only, no positive wizard suggestion`() {
            val m = model()
            m.onWizardResult(suggestion = 0.0, deficit = 12, ic = 8.0)
            assertThat(m.insulinValue).isEqualTo(0.0)

            m.onEatingToggled(true)
            assertThat(m.carbsValue).isWithin(tolerance).of(12.0)
            assertThat(m.isAtCarbDeficit).isTrue()
            assertThat(m.shouldShowCarbDeficitHint).isTrue()

            m.onInsulinUp() // 0 → 0.5
            assertThat(m.carbsValue).isWithin(tolerance).of(4.0)

            m.onInsulinUp() // 0.5 → 1.0
            assertThat(m.carbsValue).isWithin(tolerance).of(8.0)
        }
    }

    @Nested
    inner class WizardResult {

        @Test
        fun `auto-fills insulin when at zero`() {
            val m = model()
            val autoFilled = m.onWizardResult(suggestion = 2.3, deficit = 0, ic = 10.0)
            assertThat(autoFilled).isTrue()
            assertThat(m.insulinValue).isWithin(tolerance).of(2.3)
        }

        @Test
        fun `does not overwrite user-adjusted insulin`() {
            val m = model()
            m.insulinValue = 1.0
            val autoFilled = m.onWizardResult(suggestion = 2.3, deficit = 0, ic = 10.0)
            assertThat(autoFilled).isFalse()
            assertThat(m.insulinValue).isWithin(tolerance).of(1.0)
        }

        @Test
        fun `with eating checked auto-fills carbs`() {
            val m = model()
            m.eatingChecked = true
            m.onWizardResult(suggestion = 1.5, deficit = 0, ic = 10.0)
            assertThat(m.insulinValue).isWithin(tolerance).of(1.5)
            assertThat(m.carbsValue).isWithin(tolerance).of(15.0)
        }
    }

    @Nested
    inner class StepMode {

        @Test
        fun `cycles through all modes`() {
            val m = model(stepModeIndex = 0)
            assertThat(m.insulinStep).isWithin(tolerance).of(0.1)
            assertThat(m.carbStep).isWithin(tolerance).of(1.0)
            assertThat(m.stepModeLabel).isEqualTo("0.1")

            assertThat(m.onStepModeChanged()).isEqualTo(1)
            assertThat(m.insulinStep).isWithin(tolerance).of(0.5)
            assertThat(m.carbStep).isWithin(tolerance).of(5.0)
            assertThat(m.stepModeLabel).isEqualTo("0.5")

            assertThat(m.onStepModeChanged()).isEqualTo(2)
            assertThat(m.insulinStep).isWithin(tolerance).of(1.0)
            assertThat(m.carbStep).isWithin(tolerance).of(10.0)
            assertThat(m.stepModeLabel).isEqualTo("1")

            assertThat(m.onStepModeChanged()).isEqualTo(0)
            assertThat(m.insulinStep).isWithin(tolerance).of(0.1)
        }

        @Test
        fun `does not alter current values`() {
            val m = model()
            m.insulinValue = 1.7
            m.carbsValue = 17.0
            m.onStepModeChanged()
            assertThat(m.insulinValue).isWithin(tolerance).of(1.7)
            assertThat(m.carbsValue).isWithin(tolerance).of(17.0)
        }
    }

    @Nested
    inner class SaveRestore {

        @Test
        fun `roundtrip preserves all state`() {
            val m = model(iob = 2.0, basalIob = -0.3)
            m.onWizardResult(suggestion = 1.7, deficit = 17, ic = 10.0)
            m.onEatingToggled(true)
            m.onInsulinUp() // 1.7 → 2.0, carbs → 20

            val state = m.saveState()

            val m2 = model(iob = 0.0)
            m2.restoreState(state)

            assertThat(m2.insulinValue).isWithin(tolerance).of(m.insulinValue)
            assertThat(m2.carbsValue).isWithin(tolerance).of(m.carbsValue)
            assertThat(m2.eatingChecked).isEqualTo(m.eatingChecked)
            assertThat(m2.userAdjustedCarbs).isEqualTo(m.userAdjustedCarbs)
            assertThat(m2.wizardSuggestion).isWithin(tolerance).of(m.wizardSuggestion)
            assertThat(m2.currentIob).isWithin(tolerance).of(m.currentIob)
            assertThat(m2.currentBasalIob).isWithin(tolerance).of(m.currentBasalIob)
            assertThat(m2.currentIc).isWithin(tolerance).of(m.currentIc)
            assertThat(m2.carbDeficit).isEqualTo(m.carbDeficit)
        }
    }

    @Nested
    inner class DerivedState {

        @Test
        fun `projectedIob tracks insulin changes`() {
            val m = model(iob = 2.0)
            assertThat(m.projectedIob).isWithin(tolerance).of(2.0)
            m.onInsulinUp() // 0 → 0.5
            assertThat(m.projectedIob).isWithin(tolerance).of(2.5)
            m.onInsulinUp() // 0.5 → 1.0
            assertThat(m.projectedIob).isWithin(tolerance).of(3.0)
        }

        @Test
        fun `buildIobText formats correctly`() {
            assertThat(QuickBolusModel.buildIobText("IOB: ", 2.5, 0.0)).isEqualTo("IOB: 2.5 U")
            assertThat(QuickBolusModel.buildIobText("IOB: ", 2.5, -0.5)).isEqualTo("IOB: 2.5 U  3.0 real")
        }
    }

    @Nested
    inner class ColoringLifecycle {

        @Test
        fun `cyan - wizard fills suggestion, stepping away clears, stepping back restores`() {
            val m = model()
            m.onWizardResult(suggestion = 1.7, deficit = 0, ic = 10.0)
            assertThat(m.isOnInsulinSuggestion).isTrue()

            m.onInsulinUp() // 1.7 → 2.0
            assertThat(m.isOnInsulinSuggestion).isFalse()

            m.onInsulinUp() // 2.0 → 2.5
            assertThat(m.isOnInsulinSuggestion).isFalse()

            m.onInsulinDown() // 2.5 → 2.0
            assertThat(m.isOnInsulinSuggestion).isFalse()

            m.onInsulinDown() // 2.0 → 1.7 (snap)
            assertThat(m.isOnInsulinSuggestion).isTrue()

            m.onInsulinDown() // 1.7 → 1.5
            assertThat(m.isOnInsulinSuggestion).isFalse()

            m.onInsulinUp() // 1.5 → 1.7 (snap)
            assertThat(m.isOnInsulinSuggestion).isTrue()
        }

        @Test
        fun `cyan - no wizard suggestion means never cyan`() {
            val m = model()
            m.onInsulinUp() // 0 → 0.5
            assertThat(m.isOnInsulinSuggestion).isFalse()
            m.onInsulinUp() // 0.5 → 1.0
            assertThat(m.isOnInsulinSuggestion).isFalse()
        }

        @Test
        fun `cyan - on-grid suggestion stays cyan at exact grid value`() {
            val m = model()
            m.onWizardResult(suggestion = 1.5, deficit = 0, ic = 10.0)
            assertThat(m.isOnInsulinSuggestion).isTrue()

            m.onInsulinUp() // 1.5 → 2.0
            assertThat(m.isOnInsulinSuggestion).isFalse()

            m.onInsulinDown() // 2.0 → 1.5
            assertThat(m.isOnInsulinSuggestion).isTrue()
        }

        @Test
        fun `orange - hint shows whenever deficit exists regardless of eating or carb value`() {
            val m = model()
            m.onWizardResult(suggestion = 1.7, deficit = 17, ic = 10.0)

            assertThat(m.eatingChecked).isFalse()
            assertThat(m.carbsValue).isEqualTo(0.0)
            assertThat(m.shouldShowCarbDeficitHint).isTrue()

            m.onEatingToggled(true)
            assertThat(m.shouldShowCarbDeficitHint).isTrue()

            m.onEatingToggled(false)
            assertThat(m.shouldShowCarbDeficitHint).isTrue()
        }

        @Test
        fun `orange - isAtCarbDeficit tracks through carb stepping`() {
            val m = model()
            m.onWizardResult(suggestion = 1.7, deficit = 17, ic = 10.0)
            m.onEatingToggled(true)
            assertThat(m.isAtCarbDeficit).isTrue()

            m.onCarbsUp() // 17 → 20
            assertThat(m.isAtCarbDeficit).isFalse()

            m.onCarbsDown() // 20 → 17 (snap)
            assertThat(m.isAtCarbDeficit).isTrue()

            m.onCarbsDown() // 17 → 15
            assertThat(m.isAtCarbDeficit).isFalse()

            m.onCarbsUp() // 15 → 17 (snap)
            assertThat(m.isAtCarbDeficit).isTrue()

            m.onCarbsUp() // 17 → 20
            assertThat(m.isAtCarbDeficit).isFalse()
        }

        @Test
        fun `orange - insulin stepping syncs carbs through deficit`() {
            val m = model()
            m.onWizardResult(suggestion = 1.7, deficit = 17, ic = 10.0)
            m.onEatingToggled(true)
            assertThat(m.isAtCarbDeficit).isTrue()

            m.onInsulinUp() // 1.7 → 2.0, carbs → 20
            assertThat(m.isAtCarbDeficit).isFalse()

            m.onInsulinDown() // 2.0 → 1.7, carbs → 17
            assertThat(m.isAtCarbDeficit).isTrue()

            m.onInsulinDown() // 1.7 → 1.5, carbs → 15
            assertThat(m.isAtCarbDeficit).isFalse()

            m.onInsulinDown() // 1.5 → 1.0, carbs → 10
            assertThat(m.isAtCarbDeficit).isFalse()
        }

        @Test
        fun `orange - no deficit means never orange`() {
            val m = model()
            m.onWizardResult(suggestion = 1.5, deficit = 0, ic = 10.0)
            assertThat(m.shouldShowCarbDeficitHint).isFalse()
            assertThat(m.isAtCarbDeficit).isFalse()

            m.onEatingToggled(true)
            assertThat(m.shouldShowCarbDeficitHint).isFalse()
            assertThat(m.isAtCarbDeficit).isFalse()
        }

        @Test
        fun `cyan and orange together - both active at wizard suggestion with matching deficit`() {
            val m = model()
            m.onWizardResult(suggestion = 1.7, deficit = 17, ic = 10.0)
            m.onEatingToggled(true)

            assertThat(m.isOnInsulinSuggestion).isTrue()
            assertThat(m.isAtCarbDeficit).isTrue()

            m.onInsulinUp() // 1.7 → 2.0, carbs → 20
            assertThat(m.isOnInsulinSuggestion).isFalse()
            assertThat(m.isAtCarbDeficit).isFalse()

            m.onInsulinDown() // 2.0 → 1.7, carbs → 17
            assertThat(m.isOnInsulinSuggestion).isTrue()
            assertThat(m.isAtCarbDeficit).isTrue()
        }

        @Test
        fun `cyan and orange decouple when user adjusts carbs`() {
            val m = model()
            m.onWizardResult(suggestion = 1.7, deficit = 17, ic = 10.0)
            m.onEatingToggled(true)
            assertThat(m.isOnInsulinSuggestion).isTrue()
            assertThat(m.isAtCarbDeficit).isTrue()

            m.onCarbsUp() // 17 → 20, userAdjustedCarbs = true
            assertThat(m.isOnInsulinSuggestion).isTrue()
            assertThat(m.isAtCarbDeficit).isFalse()

            m.onInsulinUp() // 1.7 → 2.0
            assertThat(m.isOnInsulinSuggestion).isFalse()
            assertThat(m.carbsValue).isWithin(tolerance).of(20.0)
            assertThat(m.isAtCarbDeficit).isFalse()

            m.onInsulinDown() // 2.0 → 1.7
            assertThat(m.isOnInsulinSuggestion).isTrue()
            assertThat(m.isAtCarbDeficit).isFalse()
        }
    }

    @Nested
    inner class NegativeZeroIob {

        @Test
        fun `buildIobText - tiny negative IOB does not produce negative zero`() {
            assertThat(QuickBolusModel.buildIobText("IOB: ", -0.04, 0.0)).isEqualTo("IOB: 0.0 U")
        }
    }

    @Nested
    inner class EdgeCases {

        @Test
        fun `carbs stay at deficit when insulin is zero and eating is checked`() {
            val m = model()
            m.onWizardResult(suggestion = 0.0, deficit = 17, ic = 10.0)
            m.onEatingToggled(true)
            assertThat(m.carbsValue).isWithin(tolerance).of(17.0)
            assertThat(m.onInsulinDown()).isFalse()
        }

        @Test
        fun `rapid eating toggle does not corrupt state`() {
            val m = model()
            m.onWizardResult(suggestion = 1.0, deficit = 10, ic = 10.0)
            m.onEatingToggled(true)
            m.onEatingToggled(false)
            m.onEatingToggled(true)
            m.onEatingToggled(false)
            m.onEatingToggled(true)
            assertThat(m.carbsValue).isWithin(tolerance).of(10.0)
            assertThat(m.userAdjustedCarbs).isFalse()
        }

        @Test
        fun `step mode 0_1 gives fine-grained insulin control`() {
            val m = model(stepModeIndex = 0)
            m.wizardSuggestion = 0.35
            m.onInsulinUp() // 0 → 0.1
            assertThat(m.insulinValue).isWithin(tolerance).of(0.1)
            m.onInsulinUp() // 0.1 → 0.2
            assertThat(m.insulinValue).isWithin(tolerance).of(0.2)
            m.onInsulinUp() // 0.2 → 0.3
            assertThat(m.insulinValue).isWithin(tolerance).of(0.3)
            m.onInsulinUp() // 0.3 → 0.35 (wizard snap)
            assertThat(m.insulinValue).isWithin(tolerance).of(0.35)
            m.onInsulinUp() // 0.35 → 0.4
            assertThat(m.insulinValue).isWithin(tolerance).of(0.4)
        }
    }
}
