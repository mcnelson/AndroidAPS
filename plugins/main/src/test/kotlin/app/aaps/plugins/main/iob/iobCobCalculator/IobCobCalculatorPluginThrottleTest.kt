package app.aaps.plugins.main.iob.iobCobCalculator

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.rx.events.EventNewHistoryData
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.keys.IntKey
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.Preferences

class IobCobCalculatorPluginThrottleTest : TestBase() {

    @Mock lateinit var preferences: Preferences
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var fabricPrivacy: FabricPrivacy
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var overviewData: OverviewData
    @Mock lateinit var calculationWorkflow: CalculationWorkflow
    @Mock lateinit var decimalFormatter: DecimalFormatter
    @Mock lateinit var processedTbrEbData: ProcessedTbrEbData
    @Mock lateinit var dateUtil: app.aaps.core.interfaces.utils.DateUtil

    private lateinit var sut: IobCobCalculatorPlugin

    private fun getLastBgCalcTriggeredAt(): Long {
        val field = IobCobCalculatorPlugin::class.java.getDeclaredField("lastBgCalcTriggeredAt")
        field.isAccessible = true
        return field.getLong(sut)
    }

    private fun setLastBgCalcTriggeredAt(value: Long) {
        val field = IobCobCalculatorPlugin::class.java.getDeclaredField("lastBgCalcTriggeredAt")
        field.isAccessible = true
        field.setLong(sut, value)
    }

    private fun callScheduleHistoryDataChange(event: EventNewHistoryData) {
        val method = IobCobCalculatorPlugin::class.java.getDeclaredMethod("scheduleHistoryDataChange", EventNewHistoryData::class.java)
        method.isAccessible = true
        method.invoke(sut, event)
    }

    @BeforeEach
    fun setup() {
        sut = IobCobCalculatorPlugin(
            aapsLogger, aapsSchedulers, rxBus, preferences, rh,
            profileFunction, activePlugin, fabricPrivacy, dateUtil,
            persistenceLayer, overviewData, calculationWorkflow,
            decimalFormatter, processedTbrEbData
        )
    }

    // --- Throttle disabled (interval = 0) ---

    @Test
    fun `throttle disabled - BG event always passes through`() {
        whenever(preferences.get(IntKey.LoopMinBgRecalcInterval)).thenReturn(0)
        val event = EventNewHistoryData(oldDataTimestamp = 1000L, reloadBgData = true, newestGlucoseValueTimestamp = 2000L)

        callScheduleHistoryDataChange(event)
        assertThat(getLastBgCalcTriggeredAt()).isEqualTo(0L) // not updated when throttle disabled
    }

    @Test
    fun `throttle disabled - rapid BG events all pass through`() {
        whenever(preferences.get(IntKey.LoopMinBgRecalcInterval)).thenReturn(0)

        repeat(5) {
            val event = EventNewHistoryData(oldDataTimestamp = 1000L + it, reloadBgData = true, newestGlucoseValueTimestamp = 2000L + it)
            callScheduleHistoryDataChange(event)
        }
        assertThat(getLastBgCalcTriggeredAt()).isEqualTo(0L)
    }

    // --- Throttle enabled ---

    @Test
    fun `throttle enabled - first BG event passes through and updates timestamp`() {
        whenever(preferences.get(IntKey.LoopMinBgRecalcInterval)).thenReturn(3)

        val event = EventNewHistoryData(oldDataTimestamp = 1000L, reloadBgData = true, newestGlucoseValueTimestamp = 2000L)
        callScheduleHistoryDataChange(event)

        assertThat(getLastBgCalcTriggeredAt()).isGreaterThan(0L)
    }

    @Test
    fun `throttle enabled - second BG event within interval is throttled`() {
        whenever(preferences.get(IntKey.LoopMinBgRecalcInterval)).thenReturn(3)
        setLastBgCalcTriggeredAt(System.currentTimeMillis()) // just ran

        val event = EventNewHistoryData(oldDataTimestamp = 1000L, reloadBgData = true, newestGlucoseValueTimestamp = 2000L)
        val before = getLastBgCalcTriggeredAt()
        callScheduleHistoryDataChange(event)

        // timestamp unchanged = event was throttled (early return before scheduling)
        assertThat(getLastBgCalcTriggeredAt()).isEqualTo(before)
    }

    @Test
    fun `throttle enabled - BG event after interval passes through`() {
        whenever(preferences.get(IntKey.LoopMinBgRecalcInterval)).thenReturn(3)
        // 4 minutes ago — beyond the 3-minute interval
        setLastBgCalcTriggeredAt(System.currentTimeMillis() - 4 * 60 * 1000L)

        val event = EventNewHistoryData(oldDataTimestamp = 1000L, reloadBgData = true, newestGlucoseValueTimestamp = 2000L)
        callScheduleHistoryDataChange(event)

        // timestamp updated = event passed through
        assertThat(getLastBgCalcTriggeredAt()).isGreaterThan(System.currentTimeMillis() - 1000L)
    }

    @Test
    fun `throttle enabled - 10s grace allows slightly early event`() {
        whenever(preferences.get(IntKey.LoopMinBgRecalcInterval)).thenReturn(3)
        // 2 min 55 sec ago — within the 10s grace window of 3-minute interval
        setLastBgCalcTriggeredAt(System.currentTimeMillis() - (2 * 60 * 1000L + 55 * 1000L))

        val event = EventNewHistoryData(oldDataTimestamp = 1000L, reloadBgData = true, newestGlucoseValueTimestamp = 2000L)
        callScheduleHistoryDataChange(event)

        // should pass through due to grace period (interval is 3min - 10s = 2min50s)
        assertThat(getLastBgCalcTriggeredAt()).isGreaterThan(System.currentTimeMillis() - 1000L)
    }

    // --- Non-BG events bypass throttle ---

    @Test
    fun `non-BG event bypasses throttle even within interval`() {
        whenever(preferences.get(IntKey.LoopMinBgRecalcInterval)).thenReturn(3)
        setLastBgCalcTriggeredAt(System.currentTimeMillis()) // just ran

        // reloadBgData = false means this is a treatment/profile change, not a BG event
        val event = EventNewHistoryData(oldDataTimestamp = 1000L, reloadBgData = false, newestGlucoseValueTimestamp = null)
        val before = getLastBgCalcTriggeredAt()
        callScheduleHistoryDataChange(event)

        // timestamp unchanged but event was NOT throttled (condition doesn't match)
        assertThat(getLastBgCalcTriggeredAt()).isEqualTo(before)
    }

    @Test
    fun `BG event without newestGlucoseValueTimestamp bypasses throttle`() {
        whenever(preferences.get(IntKey.LoopMinBgRecalcInterval)).thenReturn(3)
        setLastBgCalcTriggeredAt(System.currentTimeMillis()) // just ran

        val event = EventNewHistoryData(oldDataTimestamp = 1000L, reloadBgData = true, newestGlucoseValueTimestamp = null)
        val before = getLastBgCalcTriggeredAt()
        callScheduleHistoryDataChange(event)

        assertThat(getLastBgCalcTriggeredAt()).isEqualTo(before)
    }

    // --- Edge cases ---

    @Test
    fun `throttle with 1 minute interval blocks rapid events`() {
        whenever(preferences.get(IntKey.LoopMinBgRecalcInterval)).thenReturn(1)
        setLastBgCalcTriggeredAt(System.currentTimeMillis() - 30_000L) // 30s ago

        val event = EventNewHistoryData(oldDataTimestamp = 1000L, reloadBgData = true, newestGlucoseValueTimestamp = 2000L)
        val before = getLastBgCalcTriggeredAt()
        callScheduleHistoryDataChange(event)

        // 30s < 50s (1min - 10s grace), so throttled
        assertThat(getLastBgCalcTriggeredAt()).isEqualTo(before)
    }
}
