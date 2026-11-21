package app.aaps.ui.dialogs

import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TreatmentDialogTest : TestBase() {

    private val tolerance = 0.001
    private val stepSize = 0.5

    // ── adjustSteppedValue ────────────────────────────────────────────────────

    @Test
    fun `adjustSteppedValue - no suggestion - steps up from grid`() {
        assertThat(TreatmentDialog.adjustSteppedValue(0.0, stepSize, 1)).isWithin(tolerance).of(0.5)
        assertThat(TreatmentDialog.adjustSteppedValue(0.5, stepSize, 1)).isWithin(tolerance).of(1.0)
        assertThat(TreatmentDialog.adjustSteppedValue(1.0, stepSize, 1)).isWithin(tolerance).of(1.5)
        assertThat(TreatmentDialog.adjustSteppedValue(1.5, stepSize, 1)).isWithin(tolerance).of(2.0)
    }

    @Test
    fun `adjustSteppedValue - no suggestion - steps down from grid`() {
        assertThat(TreatmentDialog.adjustSteppedValue(2.0, stepSize, -1)).isWithin(tolerance).of(1.5)
        assertThat(TreatmentDialog.adjustSteppedValue(1.5, stepSize, -1)).isWithin(tolerance).of(1.0)
        assertThat(TreatmentDialog.adjustSteppedValue(1.0, stepSize, -1)).isWithin(tolerance).of(0.5)
        assertThat(TreatmentDialog.adjustSteppedValue(0.5, stepSize, -1)).isWithin(tolerance).of(0.0)
    }

    @Test
    fun `adjustSteppedValue - floors at zero`() {
        assertThat(TreatmentDialog.adjustSteppedValue(0.0, stepSize, -1)).isWithin(tolerance).of(0.0)
    }

    @Test
    fun `adjustSteppedValue - on-grid suggestion is ignored`() {
        // suggestion == 0.5 is on-grid, so it behaves identically to no suggestion
        assertThat(TreatmentDialog.adjustSteppedValue(0.0, stepSize, 1, 0.5)).isWithin(tolerance).of(0.5)
        assertThat(TreatmentDialog.adjustSteppedValue(0.5, stepSize, 1, 0.5)).isWithin(tolerance).of(1.0)
        assertThat(TreatmentDialog.adjustSteppedValue(0.5, stepSize, -1, 0.5)).isWithin(tolerance).of(0.0)
    }

    @Test
    fun `adjustSteppedValue - off-grid suggestion 0_2 snaps in when crossed`() {
        assertThat(TreatmentDialog.adjustSteppedValue(0.0,  stepSize,  1, 0.2)).isWithin(tolerance).of(0.2) // snap up to wizard
        assertThat(TreatmentDialog.adjustSteppedValue(0.2,  stepSize,  1, 0.2)).isWithin(tolerance).of(0.5)
        assertThat(TreatmentDialog.adjustSteppedValue(0.5,  stepSize, -1, 0.2)).isWithin(tolerance).of(0.2) // snap down to wizard
        assertThat(TreatmentDialog.adjustSteppedValue(0.2,  stepSize, -1, 0.2)).isWithin(tolerance).of(0.0)
    }

    @Test
    fun `adjustSteppedValue - off-grid suggestion 0_7 snaps in when crossed`() {
        assertThat(TreatmentDialog.adjustSteppedValue(0.5, stepSize,  1, 0.7)).isWithin(tolerance).of(0.7) // snap up to wizard
        assertThat(TreatmentDialog.adjustSteppedValue(0.7, stepSize,  1, 0.7)).isWithin(tolerance).of(1.0)
        assertThat(TreatmentDialog.adjustSteppedValue(1.0, stepSize, -1, 0.7)).isWithin(tolerance).of(0.7) // snap down to wizard
        assertThat(TreatmentDialog.adjustSteppedValue(0.7, stepSize, -1, 0.7)).isWithin(tolerance).of(0.5)
    }

    @Test
    fun `adjustSteppedValue - off-grid suggestion 1_2 snaps in when crossed`() {
        assertThat(TreatmentDialog.adjustSteppedValue(1.0, stepSize,  1, 1.2)).isWithin(tolerance).of(1.2)
        assertThat(TreatmentDialog.adjustSteppedValue(1.2, stepSize,  1, 1.2)).isWithin(tolerance).of(1.5)
        assertThat(TreatmentDialog.adjustSteppedValue(1.5, stepSize, -1, 1.2)).isWithin(tolerance).of(1.2)
        assertThat(TreatmentDialog.adjustSteppedValue(1.2, stepSize, -1, 1.2)).isWithin(tolerance).of(1.0)
    }

    @Test
    fun `adjustSteppedValue - off-grid suggestion 1_7 snaps in when crossed`() {
        assertThat(TreatmentDialog.adjustSteppedValue(1.5, stepSize,  1, 1.7)).isWithin(tolerance).of(1.7)
        assertThat(TreatmentDialog.adjustSteppedValue(1.7, stepSize,  1, 1.7)).isWithin(tolerance).of(2.0)
        assertThat(TreatmentDialog.adjustSteppedValue(2.0, stepSize, -1, 1.7)).isWithin(tolerance).of(1.7)
        assertThat(TreatmentDialog.adjustSteppedValue(1.7, stepSize, -1, 1.7)).isWithin(tolerance).of(1.5)
    }

    @Test
    fun `adjustSteppedValue - suggestion out of range is not snapped`() {
        // suggestion = 1.7, but we're nowhere near it
        assertThat(TreatmentDialog.adjustSteppedValue(0.0, stepSize, 1, 1.7)).isWithin(tolerance).of(0.5)
        assertThat(TreatmentDialog.adjustSteppedValue(0.5, stepSize, 1, 1.7)).isWithin(tolerance).of(1.0)
        assertThat(TreatmentDialog.adjustSteppedValue(1.0, stepSize, 1, 1.7)).isWithin(tolerance).of(1.5)
        // going down past suggestion — not snapping when moving away
        assertThat(TreatmentDialog.adjustSteppedValue(0.5, stepSize, -1, 1.7)).isWithin(tolerance).of(0.0)
    }

    @Test
    fun `adjustSteppedValue - carb step size of 5 with off-grid deficit`() {
        val carbStep = 5.0
        val deficit = 7.0
        assertThat(TreatmentDialog.adjustSteppedValue(0.0,  carbStep,  1, deficit)).isWithin(tolerance).of(5.0)
        assertThat(TreatmentDialog.adjustSteppedValue(5.0,  carbStep,  1, deficit)).isWithin(tolerance).of(7.0) // snap to deficit
        assertThat(TreatmentDialog.adjustSteppedValue(7.0,  carbStep,  1, deficit)).isWithin(tolerance).of(10.0)
        assertThat(TreatmentDialog.adjustSteppedValue(10.0, carbStep, -1, deficit)).isWithin(tolerance).of(7.0) // snap to deficit
        assertThat(TreatmentDialog.adjustSteppedValue(7.0,  carbStep, -1, deficit)).isWithin(tolerance).of(5.0)
    }

    @Test
    fun `adjustSteppedValue - navigating through suggestion and back`() {
        // Start at wizard suggestion 1.3, walk up and back down
        var current = 1.3
        current = TreatmentDialog.adjustSteppedValue(current, stepSize, 1, 1.3)
        assertThat(current).isWithin(tolerance).of(1.5)
        current = TreatmentDialog.adjustSteppedValue(current, stepSize, 1, 1.3)
        assertThat(current).isWithin(tolerance).of(2.0)
        current = TreatmentDialog.adjustSteppedValue(current, stepSize, -1, 1.3)
        assertThat(current).isWithin(tolerance).of(1.5)
        current = TreatmentDialog.adjustSteppedValue(current, stepSize, -1, 1.3)
        assertThat(current).isWithin(tolerance).of(1.3) // wizard snap on way down
        current = TreatmentDialog.adjustSteppedValue(current, stepSize, -1, 1.3)
        assertThat(current).isWithin(tolerance).of(1.0)
    }

    // ── calculateCarbAmount ───────────────────────────────────────────────────

    @Test
    fun `calculateCarbAmount - with IC and positive insulin returns ic times insulin`() {
        assertThat(TreatmentDialog.calculateCarbAmount(1.0, 10.0, 0, 0)).isEqualTo(10)
        assertThat(TreatmentDialog.calculateCarbAmount(0.5, 10.0, 0, 0)).isEqualTo(5)
        assertThat(TreatmentDialog.calculateCarbAmount(1.5, 10.0, 0, 0)).isEqualTo(15)
    }

    @Test
    fun `calculateCarbAmount - zero insulin with IC returns current carb amount`() {
        // currentAmount == 0.0 falls through to else branch
        assertThat(TreatmentDialog.calculateCarbAmount(0.0, 10.0, 0, 7)).isEqualTo(7)
    }

    @Test
    fun `calculateCarbAmount - zero IC falls back to deficit then currentCarbAmount`() {
        assertThat(TreatmentDialog.calculateCarbAmount(1.0, 0.0, 20, 7)).isEqualTo(20)
        assertThat(TreatmentDialog.calculateCarbAmount(1.0, 0.0,  0, 7)).isEqualTo(7)
    }

    @Test
    fun `calculateCarbAmount - deficit takes precedence over currentCarbAmount when IC is zero`() {
        assertThat(TreatmentDialog.calculateCarbAmount(0.5, 0.0, 12, 0)).isEqualTo(12)
    }

    @Test
    fun `calculateCarbAmount - IC calculation truncates to int`() {
        // 0.7 * 10 = 7.0 → 7
        assertThat(TreatmentDialog.calculateCarbAmount(0.7, 10.0, 0, 0)).isEqualTo(7)
        // 1.3 * 7 = 9.1 → 9
        assertThat(TreatmentDialog.calculateCarbAmount(1.3, 7.0, 0, 0)).isEqualTo(9)
    }

    // ── isOnGrid ──────────────────────────────────────────────────────────────

    @Test
    fun `isOnGrid - exact multiples of step are on grid`() {
        assertThat(TreatmentDialog.isOnGrid(0.0, 0.5)).isTrue()
        assertThat(TreatmentDialog.isOnGrid(0.5, 0.5)).isTrue()
        assertThat(TreatmentDialog.isOnGrid(1.0, 0.5)).isTrue()
        assertThat(TreatmentDialog.isOnGrid(1.5, 0.5)).isTrue()
        assertThat(TreatmentDialog.isOnGrid(2.0, 0.5)).isTrue()
    }

    @Test
    fun `isOnGrid - non-multiples are off grid`() {
        assertThat(TreatmentDialog.isOnGrid(0.2, 0.5)).isFalse()
        assertThat(TreatmentDialog.isOnGrid(0.3, 0.5)).isFalse()
        assertThat(TreatmentDialog.isOnGrid(0.7, 0.5)).isFalse()
        assertThat(TreatmentDialog.isOnGrid(1.2, 0.5)).isFalse()
        assertThat(TreatmentDialog.isOnGrid(1.3, 0.5)).isFalse()
    }

    @Test
    fun `isOnGrid - works with step size 0_1`() {
        assertThat(TreatmentDialog.isOnGrid(0.1, 0.1)).isTrue()
        assertThat(TreatmentDialog.isOnGrid(0.2, 0.1)).isTrue()
        assertThat(TreatmentDialog.isOnGrid(1.0, 0.1)).isTrue()
        assertThat(TreatmentDialog.isOnGrid(1.23, 0.1)).isFalse()
    }

    @Test
    fun `isOnGrid - works with carb step size 5`() {
        assertThat(TreatmentDialog.isOnGrid(0.0,  5.0)).isTrue()
        assertThat(TreatmentDialog.isOnGrid(5.0,  5.0)).isTrue()
        assertThat(TreatmentDialog.isOnGrid(10.0, 5.0)).isTrue()
        assertThat(TreatmentDialog.isOnGrid(7.0,  5.0)).isFalse()
        assertThat(TreatmentDialog.isOnGrid(12.0, 5.0)).isFalse()
    }

    // ── buildIobText ──────────────────────────────────────────────────────────

    @Test
    fun `buildIobText - no negative basal IOB returns base string`() {
        assertThat(TreatmentDialog.buildIobText("IOB: ", 2.5, 0.0)).isEqualTo("IOB: 2.5 U")
        assertThat(TreatmentDialog.buildIobText("IOB: ", 0.0, 0.0)).isEqualTo("IOB: 0.0 U")
        assertThat(TreatmentDialog.buildIobText("New IOB: ", 3.14, 0.0)).isEqualTo("New IOB: 3.1 U")
    }

    @Test
    fun `buildIobText - positive basal IOB does not append real suffix`() {
        assertThat(TreatmentDialog.buildIobText("IOB: ", 2.5, 0.5)).isEqualTo("IOB: 2.5 U")
        assertThat(TreatmentDialog.buildIobText("IOB: ", 2.5, 0.1)).isEqualTo("IOB: 2.5 U")
    }

    @Test
    fun `buildIobText - basal IOB exactly at threshold does not append real suffix`() {
        // boundary: < -0.1 triggers real suffix; -0.1 exactly does not
        assertThat(TreatmentDialog.buildIobText("IOB: ", 2.5, -0.1)).isEqualTo("IOB: 2.5 U")
    }

    @Test
    fun `buildIobText - negative basal IOB beyond threshold appends real suffix`() {
        // iob=2.5, currentBasalIob=-0.5 → real = 2.5 - (-0.5) = 3.0
        assertThat(TreatmentDialog.buildIobText("IOB: ", 2.5, -0.5)).isEqualTo("IOB: 2.5 U  3.0 real")
    }

    @Test
    fun `buildIobText - real suffix value is iob minus currentBasalIob`() {
        // iob=1.0, currentBasalIob=-0.3 → real = 1.0 - (-0.3) = 1.3
        assertThat(TreatmentDialog.buildIobText("IOB: ", 1.0, -0.3)).isEqualTo("IOB: 1.0 U  1.3 real")
        // iob=0.0, currentBasalIob=-0.2 → real = 0.0 - (-0.2) = 0.2
        assertThat(TreatmentDialog.buildIobText("IOB: ", 0.0, -0.2)).isEqualTo("IOB: 0.0 U  0.2 real")
    }

    @Test
    fun `buildIobText - iob formatted to one decimal place`() {
        assertThat(TreatmentDialog.buildIobText("IOB: ", 1.05, 0.0)).isEqualTo("IOB: 1.1 U")  // rounds up
        assertThat(TreatmentDialog.buildIobText("IOB: ", 1.04, 0.0)).isEqualTo("IOB: 1.0 U")  // rounds down
    }

    @Test
    fun `calculateCarbAmount - IC calculation tracks insulin changes`() {
        val ic = 10.0
        assertThat(TreatmentDialog.calculateCarbAmount(0.5, ic, 0, 0)).isEqualTo(5)
        assertThat(TreatmentDialog.calculateCarbAmount(1.0, ic, 0, 0)).isEqualTo(10)
        assertThat(TreatmentDialog.calculateCarbAmount(1.5, ic, 0, 0)).isEqualTo(15)
        assertThat(TreatmentDialog.calculateCarbAmount(1.0, ic, 0, 0)).isEqualTo(10)
        assertThat(TreatmentDialog.calculateCarbAmount(0.5, ic, 0, 0)).isEqualTo(5)
    }
}
