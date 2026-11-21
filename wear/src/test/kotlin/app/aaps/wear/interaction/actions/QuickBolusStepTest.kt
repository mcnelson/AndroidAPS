package app.aaps.wear.interaction.actions

import app.aaps.wear.WearTestBase
import app.aaps.wear.interaction.fragments.QuickBolusFragment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import kotlin.test.assertTrue

class QuickBolusStepTest : WearTestBase() {

    private val stepSize = 0.5
    private val tolerance = 0.01
    private val fragment = Mockito.spy(QuickBolusFragment())

    private fun assertApprox(expected: Double, actual: Double, message: String = "") {
        assertEquals(expected, actual, tolerance, message)
    }

    @Test
    fun `test wizard suggestion 0_0 - no wizard suggestion`() {
        assertApprox(0.5, fragment.adjustSteppedValue(0.0, stepSize, 1, 0.0), "0.0 -> 0.5")
        assertApprox(1.0, fragment.adjustSteppedValue(0.5, stepSize, 1, 0.0), "0.5 -> 1.0")
        assertApprox(1.5, fragment.adjustSteppedValue(1.0, stepSize, 1, 0.0), "1.0 -> 1.5")
        assertApprox(2.0, fragment.adjustSteppedValue(1.5, stepSize, 1, 0.0), "1.5 -> 2.0")

        assertApprox(0.0, fragment.adjustSteppedValue(0.5, stepSize, -1, 0.0), "0.5 -> 0.0")
        assertApprox(0.5, fragment.adjustSteppedValue(1.0, stepSize, -1, 0.0), "1.0 -> 0.5")
        assertApprox(1.0, fragment.adjustSteppedValue(1.5, stepSize, -1, 0.0), "1.5 -> 1.0")
        assertApprox(1.5, fragment.adjustSteppedValue(2.0, stepSize, -1, 0.0), "2.0 -> 1.5")
    }

    @Test
    fun `test wizard suggestion 0_5 - on grid`() {
        assertApprox(0.5, fragment.adjustSteppedValue(0.0, stepSize, 1, 0.5), "0.0 -> 0.5")
        assertApprox(1.0, fragment.adjustSteppedValue(0.5, stepSize, 1, 0.5), "0.5 -> 1.0")
        assertApprox(1.5, fragment.adjustSteppedValue(1.0, stepSize, 1, 0.5), "1.0 -> 1.5")

        assertApprox(0.0, fragment.adjustSteppedValue(0.5, stepSize, -1, 0.5), "0.5 -> 0.0")
        assertApprox(0.5, fragment.adjustSteppedValue(1.0, stepSize, -1, 0.5), "1.0 -> 0.5")
        assertApprox(1.0, fragment.adjustSteppedValue(1.5, stepSize, -1, 0.5), "1.5 -> 1.0")
    }

    @Test
    fun `test wizard suggestion 0_2 - off grid`() {
        assertApprox(0.2, fragment.adjustSteppedValue(0.0, stepSize, 1, 0.2), "0.0 -> 0.2 (wizard)")
        assertApprox(0.5, fragment.adjustSteppedValue(0.2, stepSize, 1, 0.2), "0.2 -> 0.5")
        assertApprox(1.0, fragment.adjustSteppedValue(0.5, stepSize, 1, 0.2), "0.5 -> 1.0")
        assertApprox(1.5, fragment.adjustSteppedValue(1.0, stepSize, 1, 0.2), "1.0 -> 1.5")

        assertApprox(0.5, fragment.adjustSteppedValue(1.0, stepSize, -1, 0.2), "1.0 -> 0.5")
        assertApprox(0.2, fragment.adjustSteppedValue(0.5, stepSize, -1, 0.2), "0.5 -> 0.2 (wizard)")
        assertApprox(0.0, fragment.adjustSteppedValue(0.2, stepSize, -1, 0.2), "0.2 -> 0.0")
    }

    @Test
    fun `test wizard suggestion 0_3 - off grid`() {
        assertApprox(0.3, fragment.adjustSteppedValue(0.0, stepSize, 1, 0.3), "0.0 -> 0.3 (wizard)")
        assertApprox(0.5, fragment.adjustSteppedValue(0.3, stepSize, 1, 0.3), "0.3 -> 0.5")
        assertApprox(1.0, fragment.adjustSteppedValue(0.5, stepSize, 1, 0.3), "0.5 -> 1.0")

        assertApprox(0.5, fragment.adjustSteppedValue(1.0, stepSize, -1, 0.3), "1.0 -> 0.5")
        assertApprox(0.3, fragment.adjustSteppedValue(0.5, stepSize, -1, 0.3), "0.5 -> 0.3 (wizard)")
        assertApprox(0.0, fragment.adjustSteppedValue(0.3, stepSize, -1, 0.3), "0.3 -> 0.0")
    }

    @Test
    fun `test wizard suggestion 0_7 - off grid`() {
        assertApprox(0.5, fragment.adjustSteppedValue(0.0, stepSize, 1, 0.7), "0.0 -> 0.5")
        assertApprox(0.7, fragment.adjustSteppedValue(0.5, stepSize, 1, 0.7), "0.5 -> 0.7 (wizard)")
        assertApprox(1.0, fragment.adjustSteppedValue(0.7, stepSize, 1, 0.7), "0.7 -> 1.0")
        assertApprox(1.5, fragment.adjustSteppedValue(1.0, stepSize, 1, 0.7), "1.0 -> 1.5")

        assertApprox(0.7, fragment.adjustSteppedValue(1.0, stepSize, -1, 0.7), "1.0 -> 0.7 (wizard)")
        assertApprox(0.5, fragment.adjustSteppedValue(0.7, stepSize, -1, 0.7), "0.7 -> 0.5")
        assertApprox(0.0, fragment.adjustSteppedValue(0.5, stepSize, -1, 0.7), "0.5 -> 0.0")
    }

    @Test
    fun `test wizard suggestion 1_0 - on grid`() {
        assertApprox(0.5, fragment.adjustSteppedValue(0.0, stepSize, 1, 1.0), "0.0 -> 0.5")
        assertApprox(1.0, fragment.adjustSteppedValue(0.5, stepSize, 1, 1.0), "0.5 -> 1.0")
        assertApprox(1.5, fragment.adjustSteppedValue(1.0, stepSize, 1, 1.0), "1.0 -> 1.5")

        assertApprox(0.5, fragment.adjustSteppedValue(1.0, stepSize, -1, 1.0), "1.0 -> 0.5")
        assertApprox(1.0, fragment.adjustSteppedValue(1.5, stepSize, -1, 1.0), "1.5 -> 1.0")
    }

    @Test
    fun `test wizard suggestion 1_2 - off grid`() {
        assertApprox(0.5, fragment.adjustSteppedValue(0.0, stepSize, 1, 1.2), "0.0 -> 0.5")
        assertApprox(1.0, fragment.adjustSteppedValue(0.5, stepSize, 1, 1.2), "0.5 -> 1.0")
        assertApprox(1.2, fragment.adjustSteppedValue(1.0, stepSize, 1, 1.2), "1.0 -> 1.2 (wizard)")
        assertApprox(1.5, fragment.adjustSteppedValue(1.2, stepSize, 1, 1.2), "1.2 -> 1.5")
        assertApprox(2.0, fragment.adjustSteppedValue(1.5, stepSize, 1, 1.2), "1.5 -> 2.0")

        assertApprox(1.5, fragment.adjustSteppedValue(2.0, stepSize, -1, 1.2), "2.0 -> 1.5")
        assertApprox(1.2, fragment.adjustSteppedValue(1.5, stepSize, -1, 1.2), "1.5 -> 1.2 (wizard)")
        assertApprox(1.0, fragment.adjustSteppedValue(1.2, stepSize, -1, 1.2), "1.2 -> 1.0")
        assertApprox(0.5, fragment.adjustSteppedValue(1.0, stepSize, -1, 1.2), "1.0 -> 0.5")
    }

    @Test
    fun `test wizard suggestion 1_3 - off grid`() {
        assertApprox(0.5, fragment.adjustSteppedValue(0.0, stepSize, 1, 1.3), "0.0 -> 0.5")
        assertApprox(1.0, fragment.adjustSteppedValue(0.5, stepSize, 1, 1.3), "0.5 -> 1.0")
        assertApprox(1.3, fragment.adjustSteppedValue(1.0, stepSize, 1, 1.3), "1.0 -> 1.3 (wizard)")
        assertApprox(1.5, fragment.adjustSteppedValue(1.3, stepSize, 1, 1.3), "1.3 -> 1.5")

        assertApprox(1.3, fragment.adjustSteppedValue(1.5, stepSize, -1, 1.3), "1.5 -> 1.3 (wizard)")
        assertApprox(1.0, fragment.adjustSteppedValue(1.3, stepSize, -1, 1.3), "1.3 -> 1.0")
    }

    @Test
    fun `test wizard suggestion 1_7 - off grid`() {
        assertApprox(0.5, fragment.adjustSteppedValue(0.0, stepSize, 1, 1.7), "0.0 -> 0.5")
        assertApprox(1.0, fragment.adjustSteppedValue(0.5, stepSize, 1, 1.7), "0.5 -> 1.0")
        assertApprox(1.5, fragment.adjustSteppedValue(1.0, stepSize, 1, 1.7), "1.0 -> 1.5")
        assertApprox(1.7, fragment.adjustSteppedValue(1.5, stepSize, 1, 1.7), "1.5 -> 1.7 (wizard)")
        assertApprox(2.0, fragment.adjustSteppedValue(1.7, stepSize, 1, 1.7), "1.7 -> 2.0")

        assertApprox(1.7, fragment.adjustSteppedValue(2.0, stepSize, -1, 1.7), "2.0 -> 1.7 (wizard)")
        assertApprox(1.5, fragment.adjustSteppedValue(1.7, stepSize, -1, 1.7), "1.7 -> 1.5")
        assertApprox(1.0, fragment.adjustSteppedValue(1.5, stepSize, -1, 1.7), "1.5 -> 1.0")
    }

    @Test
    fun `test wizard suggestion 1_5 - on grid`() {
        assertApprox(0.5, fragment.adjustSteppedValue(0.0, stepSize, 1, 1.5), "0.0 -> 0.5")
        assertApprox(1.0, fragment.adjustSteppedValue(0.5, stepSize, 1, 1.5), "0.5 -> 1.0")
        assertApprox(1.5, fragment.adjustSteppedValue(1.0, stepSize, 1, 1.5), "1.0 -> 1.5")
        assertApprox(2.0, fragment.adjustSteppedValue(1.5, stepSize, 1, 1.5), "1.5 -> 2.0")

        assertApprox(1.5, fragment.adjustSteppedValue(2.0, stepSize, -1, 1.5), "2.0 -> 1.5")
        assertApprox(1.0, fragment.adjustSteppedValue(1.5, stepSize, -1, 1.5), "1.5 -> 1.0")
    }

    @Test
    fun `test wizard suggestion 2_0 - on grid`() {
        assertApprox(0.5, fragment.adjustSteppedValue(0.0, stepSize, 1, 2.0), "0.0 -> 0.5")
        assertApprox(1.0, fragment.adjustSteppedValue(0.5, stepSize, 1, 2.0), "0.5 -> 1.0")
        assertApprox(1.5, fragment.adjustSteppedValue(1.0, stepSize, 1, 2.0), "1.0 -> 1.5")
        assertApprox(2.0, fragment.adjustSteppedValue(1.5, stepSize, 1, 2.0), "1.5 -> 2.0")

        assertApprox(1.5, fragment.adjustSteppedValue(2.0, stepSize, -1, 2.0), "2.0 -> 1.5")
        assertApprox(1.0, fragment.adjustSteppedValue(1.5, stepSize, -1, 2.0), "1.5 -> 1.0")
    }

    @Test
    fun `test starting from wizard suggestion and navigating`() {
        var current = 1.3
        assertApprox(1.5, fragment.adjustSteppedValue(current, stepSize, 1, 1.3), "1.3 -> 1.5")

        current = 1.5
        assertApprox(2.0, fragment.adjustSteppedValue(current, stepSize, 1, 1.3), "1.5 -> 2.0")

        current = 2.0
        assertApprox(1.5, fragment.adjustSteppedValue(current, stepSize, -1, 1.3), "2.0 -> 1.5")

        current = 1.5
        assertApprox(1.3, fragment.adjustSteppedValue(current, stepSize, -1, 1.3), "1.5 -> 1.3 (wizard)")

        current = 1.3
        assertApprox(1.0, fragment.adjustSteppedValue(current, stepSize, -1, 1.3), "1.3 -> 1.0")
    }

    @Test
    fun `test edge case near zero`() {
        assertApprox(0.2, fragment.adjustSteppedValue(0.0, stepSize, 1, 0.2), "0.0 -> 0.2")
        assertApprox(0.0, fragment.adjustSteppedValue(0.2, stepSize, -1, 0.2), "0.2 -> 0.0")
        assertApprox(0.0, fragment.adjustSteppedValue(0.0, stepSize, -1, 0.2), "0.0 -> 0.0 (floor at zero)")
    }

    @Test
    fun `test multiple decimal places`() {
        assertApprox(1.0, fragment.adjustSteppedValue(0.5, stepSize, 1, 1.23), "0.5 -> 1.0")
        assertApprox(1.23, fragment.adjustSteppedValue(1.0, stepSize, 1, 1.23), "1.0 -> 1.23 (wizard)")
        assertApprox(1.5, fragment.adjustSteppedValue(1.23, stepSize, 1, 1.23), "1.23 -> 1.5")

        assertApprox(1.23, fragment.adjustSteppedValue(1.5, stepSize, -1, 1.23), "1.5 -> 1.23 (wizard)")
        assertApprox(1.0, fragment.adjustSteppedValue(1.23, stepSize, -1, 1.23), "1.23 -> 1.0")
    }

    @Test
    fun `test carb snapping with deficit suggestion`() {
        val carbStepSize = 5.0
        val carbDeficit = 7.0

        assertApprox(5.0, fragment.adjustSteppedValue(0.0, carbStepSize, 1, carbDeficit), "0g -> 5g")
        assertApprox(7.0, fragment.adjustSteppedValue(5.0, carbStepSize, 1, carbDeficit), "5g -> 7g (deficit)")
        assertApprox(10.0, fragment.adjustSteppedValue(7.0, carbStepSize, 1, carbDeficit), "7g -> 10g")
        assertApprox(7.0, fragment.adjustSteppedValue(10.0, carbStepSize, -1, carbDeficit), "10g -> 7g (deficit)")
        assertApprox(5.0, fragment.adjustSteppedValue(7.0, carbStepSize, -1, carbDeficit), "7g -> 5g")
    }

    @Test
    fun `test carb deficit with IC ratio tracking`() {
        val carbDeficit = 7
        val currentIc = 10.0
        var eatingCheckboxChecked = false
        var currentAmount = 0.0
        var carbAmount = 0

        assertTrue(carbDeficit > 0)

        eatingCheckboxChecked = true
        carbAmount = fragment.calculateCarbAmount(currentAmount, currentIc, carbDeficit, eatingCheckboxChecked, carbAmount)
        assertEquals(0, carbAmount)

        currentAmount = 0.5
        carbAmount = fragment.calculateCarbAmount(currentAmount, currentIc, carbDeficit, eatingCheckboxChecked, carbAmount)
        assertEquals(5, carbAmount)

        currentAmount = 1.0
        carbAmount = fragment.calculateCarbAmount(currentAmount, currentIc, carbDeficit, eatingCheckboxChecked, carbAmount)
        assertEquals(10, carbAmount)

        currentAmount = 0.5
        carbAmount = fragment.calculateCarbAmount(currentAmount, currentIc, carbDeficit, eatingCheckboxChecked, carbAmount)
        assertEquals(5, carbAmount)
    }

    @Test
    fun `test IC ratio tracking without carb deficit`() {
        val carbDeficit = 0
        val currentIc = 10.0
        val eatingCheckboxChecked = true
        var currentAmount = 0.0
        var carbAmount = 0

        currentAmount = 0.0
        carbAmount = fragment.calculateCarbAmount(currentAmount, currentIc, carbDeficit, eatingCheckboxChecked, carbAmount)
        assertEquals((currentAmount * currentIc).toInt(), carbAmount)

        currentAmount = 0.5
        carbAmount = fragment.calculateCarbAmount(currentAmount, currentIc, carbDeficit, eatingCheckboxChecked, carbAmount)
        assertEquals((currentAmount * currentIc).toInt(), carbAmount)

        currentAmount = 1.0
        carbAmount = fragment.calculateCarbAmount(currentAmount, currentIc, carbDeficit, eatingCheckboxChecked, carbAmount)
        assertEquals((currentAmount * currentIc).toInt(), carbAmount)

        currentAmount = 1.5
        carbAmount = fragment.calculateCarbAmount(currentAmount, currentIc, carbDeficit, eatingCheckboxChecked, carbAmount)
        assertEquals((currentAmount * currentIc).toInt(), carbAmount)

        currentAmount = 1.0
        carbAmount = fragment.calculateCarbAmount(currentAmount, currentIc, carbDeficit, eatingCheckboxChecked, carbAmount)
        assertEquals((currentAmount * currentIc).toInt(), carbAmount)

        currentAmount = 0.5
        carbAmount = fragment.calculateCarbAmount(currentAmount, currentIc, carbDeficit, eatingCheckboxChecked, carbAmount)
        assertEquals((currentAmount * currentIc).toInt(), carbAmount)

        currentAmount = 0.0
        carbAmount = fragment.calculateCarbAmount(currentAmount, currentIc, carbDeficit, eatingCheckboxChecked, carbAmount)
        assertEquals((currentAmount * currentIc).toInt(), carbAmount)
    }

    @Test
    fun `test IC ratio fails when IC is zero`() {
        val carbDeficit = 0
        val currentIc = 0.0
        val eatingCheckboxChecked = true
        var currentAmount = 0.0
        var carbAmount = 0

        currentAmount = 0.5
        carbAmount = fragment.calculateCarbAmount(currentAmount, currentIc, carbDeficit, eatingCheckboxChecked, carbAmount)
        assertEquals(0, carbAmount, "When IC=0, carbs should stay 0 (broken behavior)")

        currentAmount = 1.0
        carbAmount = fragment.calculateCarbAmount(currentAmount, currentIc, carbDeficit, eatingCheckboxChecked, carbAmount)
        assertEquals(0, carbAmount, "When IC=0, carbs should stay 0 (broken behavior)")
    }
}
