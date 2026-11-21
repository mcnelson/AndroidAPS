package app.aaps.wear.interaction.menus

import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.TextView
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.wear.R
import app.aaps.wear.data.ComplicationDataRepository
import app.aaps.wear.interaction.activities.LoopStatusActivity
import android.content.Context
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import com.jjoe64.graphview.series.PointsGraphSeries
import app.aaps.core.graph.data.AreaGraphSeries
import app.aaps.core.graph.data.DataPointWithLabelInterface
import app.aaps.core.graph.data.DoubleDataPoint
import app.aaps.core.graph.data.PointsWithLabelGraphSeries
import app.aaps.core.graph.data.Shape
import app.aaps.core.graph.data.TimeAsXAxisLabelFormatter
import dagger.android.DaggerActivity
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.sin

class GraphActivity : DaggerActivity() {

    @Inject lateinit var sp: SP
    @Inject lateinit var complicationDataRepository: ComplicationDataRepository
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsSchedulers: AapsSchedulers

    private class SimpleTreatmentDataPoint(
        private val timestamp: Long,
        private val y: Double,
        private val treatmentType: TreatmentType,
        override val label: String = ""
    ) : DataPointWithLabelInterface {
        enum class TreatmentType { BOLUS, CARBS, SMB }

        override fun getX(): Double = timestamp.toDouble()
        override fun getY(): Double = y
        override fun setY(y: Double) {}
        override val duration: Long = 0
        override val shape: Shape = when (treatmentType) {
            TreatmentType.SMB -> Shape.SMB
            TreatmentType.CARBS -> Shape.CARBS
            TreatmentType.BOLUS -> Shape.BOLUS
        }
        override val size: Float = when (treatmentType) {
            TreatmentType.SMB -> 1.5f
            TreatmentType.BOLUS, TreatmentType.CARBS -> 2f
        }
        override val paintStyle: Paint.Style = Paint.Style.FILL
        override fun color(context: Context?): Int = when (treatmentType) {
            TreatmentType.CARBS -> Color.parseColor("#FFFB8C00")
            TreatmentType.BOLUS -> Color.CYAN
            TreatmentType.SMB -> Color.parseColor("#CC03A9F4")
        }
    }

    private lateinit var graphView: GraphView
    private lateinit var scrollView: HorizontalScrollView
    private lateinit var scaleDetector: ScaleGestureDetector
    private lateinit var chartContainer: android.view.View
    private var baseChartWidth = 0
    private var currentScale = 1.0f
    private var disposable = CompositeDisposable()
    private var initialScrollDone = false
    private var isResumed = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_graph)

        graphView = findViewById(R.id.chart)
        scrollView = findViewById(R.id.scroll_container)
        chartContainer = findViewById(R.id.chart_container)

        scaleDetector = ScaleGestureDetector(this, ScaleListener())

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        baseChartWidth = screenWidth

        chartContainer.post {
            chartContainer.layoutParams.width = baseChartWidth
            chartContainer.requestLayout()
        }

        setupGraphView()

        // Make loop icon clickable → opens LoopStatusActivity
        findViewById<ImageView>(R.id.metric_loop_status_icon)?.setOnClickListener {
            startActivity(Intent(this, LoopStatusActivity::class.java))
        }
    }

    private fun setupGraphView() {
        graphView.gridLabelRenderer.apply {
            setHorizontalLabelsVisible(true)
            setVerticalLabelsVisible(true)
            gridColor = Color.DKGRAY
            setHorizontalAxisTitleColor(Color.WHITE)
            setVerticalAxisTitleColor(Color.WHITE)
            labelsSpace = 5
            numHorizontalLabels = 7
            labelFormatter = TimeAsXAxisLabelFormatter("HH")
        }

        graphView.viewport.apply {
            isXAxisBoundsManual = false
            isYAxisBoundsManual = true
            setMinY(0.0)
            setMaxY(400.0)
            isScalable = false
            isScrollable = false
        }

        graphView.setBackgroundColor(Color.BLACK)
    }

    private fun setupChart(graphData: EventData.GraphData, treatmentData: EventData.TreatmentData) {
        if (graphData.entries.isEmpty()) return

        graphView.removeAllSeries()

        val now = System.currentTimeMillis()
        val fromTime = now - (6 * 60 * 60 * 1000L)
        val toTime = now + (150 * 60 * 1000L)

        val firstBg = graphData.entries.firstOrNull()
        val lowLine = firstBg?.low ?: 70.0
        val highLine = firstBg?.high ?: 180.0

        // 1. In-range background area
        val inRangeAreaDataPoints = arrayOf(
            DoubleDataPoint(fromTime.toDouble(), lowLine, highLine),
            DoubleDataPoint(toTime.toDouble(), lowLine, highLine)
        )
        val inRangeArea = AreaGraphSeries(inRangeAreaDataPoints)
        inRangeArea.color = 0
        inRangeArea.isDrawBackground = true
        inRangeArea.backgroundColor = Color.argb(30, 0, 255, 0)
        graphView.addSeries(inRangeArea)

        // 2. BG readings as colored points
        val inRangePoints = mutableListOf<DataPoint>()
        val highPoints = mutableListOf<DataPoint>()
        val lowPoints = mutableListOf<DataPoint>()

        graphData.entries
            .filter { it.sgv > 0 && it.timeStamp >= fromTime && it.timeStamp <= toTime }
            .forEach { bg ->
                val point = DataPoint(bg.timeStamp.toDouble(), bg.sgv)
                when (bg.sgvLevel) {
                    1L -> highPoints.add(point)
                    -1L -> lowPoints.add(point)
                    else -> inRangePoints.add(point)
                }
            }

        if (inRangePoints.isNotEmpty()) {
            val series = PointsGraphSeries(inRangePoints.toTypedArray())
            series.apply {
                shape = PointsGraphSeries.Shape.POINT
                size = 5f
                color = resources.getColor(R.color.inrange, null)
            }
            graphView.addSeries(series)
        }

        if (highPoints.isNotEmpty()) {
            val series = PointsGraphSeries(highPoints.toTypedArray())
            series.apply {
                shape = PointsGraphSeries.Shape.POINT
                size = 5f
                color = Color.YELLOW
            }
            graphView.addSeries(series)
        }

        if (lowPoints.isNotEmpty()) {
            val series = PointsGraphSeries(lowPoints.toTypedArray())
            series.apply {
                shape = PointsGraphSeries.Shape.POINT
                size = 5f
                color = Color.RED
            }
            graphView.addSeries(series)
        }

        // 3. Treatments (boluses, carbs, SMBs)
        val treatmentPoints = mutableListOf<DataPointWithLabelInterface>()
        treatmentData.boluses.forEach { treatment ->
            if (treatment.date >= fromTime && treatment.date <= toTime) {
                val bgValue = graphData.entries
                    .filter { it.sgv > 0 }
                    .minByOrNull { kotlin.math.abs(it.timeStamp - treatment.date) }
                    ?.sgv?.toDouble() ?: (lowLine + ((highLine - lowLine) / 2))

                if (treatment.isSMB) {
                    treatmentPoints.add(
                        SimpleTreatmentDataPoint(
                            timestamp = treatment.date,
                            y = lowLine - 10,
                            treatmentType = SimpleTreatmentDataPoint.TreatmentType.SMB,
                            label = ""
                        )
                    )
                } else if (treatment.bolus > 0) {
                    treatmentPoints.add(
                        SimpleTreatmentDataPoint(
                            timestamp = treatment.date,
                            y = bgValue,
                            treatmentType = SimpleTreatmentDataPoint.TreatmentType.BOLUS,
                            label = String.format("%.1f", treatment.bolus)
                        )
                    )
                }

                if (treatment.carbs > 0) {
                    treatmentPoints.add(
                        SimpleTreatmentDataPoint(
                            timestamp = treatment.date,
                            y = bgValue,
                            treatmentType = SimpleTreatmentDataPoint.TreatmentType.CARBS,
                            label = "${treatment.carbs.toInt()}g"
                        )
                    )
                }
            }
        }

        if (treatmentPoints.isNotEmpty()) {
            val treatmentSeries = PointsWithLabelGraphSeries(treatmentPoints.toTypedArray())
            graphView.addSeries(treatmentSeries)
        }

        // 4. Basal rates
        if (treatmentData.basals.isNotEmpty() || treatmentData.temps.isNotEmpty()) {
            val maxBasalValueFound = maxOf(
                treatmentData.basals.maxOfOrNull { it.amount } ?: 0.0,
                treatmentData.temps.maxOfOrNull { it.amount } ?: 0.0,
                0.1
            )
            val basalScale = lowLine / maxBasalValueFound / 1.8

            val baseBasalArray = mutableListOf<DataPoint>()
            val tempBasalArray = mutableListOf<DataPoint>()
            val absoluteBasalArray = mutableListOf<DataPoint>()

            var lastBaseBasal = 0.0
            var lastTempBasal = 0.0
            var lastAbsoluteBasal = -1.0
            var time = fromTime

            while (time < toTime) {
                val activeTemp = treatmentData.temps.find { temp ->
                    time >= temp.startTime && time < temp.endTime
                }

                val activeBase = treatmentData.basals.find { basal ->
                    time >= basal.startTime && time < basal.endTime
                }

                var absoluteValue = lastAbsoluteBasal.coerceAtLeast(0.0)

                if (activeTemp != null) {
                    val tempBasalValue = activeTemp.amount * basalScale
                    absoluteValue = tempBasalValue
                    if (tempBasalValue != lastTempBasal) {
                        tempBasalArray.add(DataPoint(time.toDouble(), lastTempBasal))
                        tempBasalArray.add(DataPoint(time.toDouble(), tempBasalValue))
                        lastTempBasal = tempBasalValue
                    }
                    if (lastBaseBasal != 0.0) {
                        baseBasalArray.add(DataPoint(time.toDouble(), lastBaseBasal))
                        baseBasalArray.add(DataPoint(time.toDouble(), 0.0))
                        lastBaseBasal = 0.0
                    }
                } else if (activeBase != null) {
                    val baseBasalValue = activeBase.amount * basalScale
                    absoluteValue = baseBasalValue
                    if (baseBasalValue != lastBaseBasal) {
                        baseBasalArray.add(DataPoint(time.toDouble(), lastBaseBasal))
                        baseBasalArray.add(DataPoint(time.toDouble(), baseBasalValue))
                        lastBaseBasal = baseBasalValue
                    }
                    if (lastTempBasal != 0.0) {
                        tempBasalArray.add(DataPoint(time.toDouble(), lastTempBasal))
                        tempBasalArray.add(DataPoint(time.toDouble(), 0.0))
                        lastTempBasal = 0.0
                    }
                }

                if (absoluteValue != lastAbsoluteBasal) {
                    absoluteBasalArray.add(DataPoint(time.toDouble(), lastAbsoluteBasal.coerceAtLeast(0.0)))
                    absoluteBasalArray.add(DataPoint(time.toDouble(), absoluteValue))
                    lastAbsoluteBasal = absoluteValue
                }

                time += 60 * 1000L
            }

            baseBasalArray.add(DataPoint(toTime.toDouble(), lastBaseBasal))
            tempBasalArray.add(DataPoint(toTime.toDouble(), lastTempBasal))
            absoluteBasalArray.add(DataPoint(toTime.toDouble(), lastAbsoluteBasal.coerceAtLeast(0.0)))

            if (baseBasalArray.isNotEmpty()) {
                val baseBasalSeries = LineGraphSeries(baseBasalArray.toTypedArray())
                baseBasalSeries.apply {
                    isDrawBackground = true
                    backgroundColor = resources.getColor(R.color.baseBasal, null)
                    thickness = 0
                }
                graphView.addSeries(baseBasalSeries)
            }

            if (tempBasalArray.isNotEmpty()) {
                val tempBasalSeries = LineGraphSeries(tempBasalArray.toTypedArray())
                tempBasalSeries.apply {
                    isDrawBackground = true
                    backgroundColor = resources.getColor(R.color.tempbasal, null)
                    thickness = 0
                }
                graphView.addSeries(tempBasalSeries)
            }

            if (absoluteBasalArray.isNotEmpty()) {
                val absoluteBasalSeries = LineGraphSeries(absoluteBasalArray.toTypedArray())
                absoluteBasalSeries.apply {
                    isDrawBackground = false
                    color = resources.getColor(R.color.basal, null)
                    thickness = 4
                }
                graphView.addSeries(absoluteBasalSeries)
            }
        }

        // 5. "Now" line
        val nowLine = LineGraphSeries(arrayOf(
            DataPoint(now.toDouble(), 0.0),
            DataPoint(now.toDouble(), highLine + 40)
        ))
        nowLine.apply {
            color = Color.WHITE
            thickness = 2
            isDrawDataPoints = false
        }
        graphView.addSeries(nowLine)

        // 6. Prediction dots
        var maxPrediction = 0.0
        if (treatmentData.predictions.isNotEmpty()) {
            val filteredPredictions = treatmentData.predictions
                .filter { it.sgv > 0 && it.timeStamp >= fromTime && it.timeStamp <= toTime }

            maxPrediction = filteredPredictions.maxOfOrNull { it.sgv } ?: 0.0

            filteredPredictions.groupBy { it.color }.forEach { (color, predictions) ->
                val points = predictions.map { DataPoint(it.timeStamp.toDouble(), it.sgv) }.toTypedArray()
                val series = PointsGraphSeries(points)
                series.apply {
                    shape = PointsGraphSeries.Shape.POINT
                    size = 3f
                    this.color = if (color != 0) color else Color.MAGENTA
                }
                graphView.addSeries(series)
            }
        }

        // Set viewport bounds
        val maxY = maxOf(highLine + 40, maxPrediction + 20)
        graphView.viewport.apply {
            isXAxisBoundsManual = true
            setMinX(fromTime.toDouble())
            setMaxX(toTime.toDouble())
            isYAxisBoundsManual = true
            setMinY(0.0)
            setMaxY(maxY)
        }
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
        disposable += rxBus
            .toObservable(EventData.Status::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe { updateData() }
        disposable += rxBus
            .toObservable(EventData.SingleBg::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe { updateData() }
        disposable += rxBus
            .toObservable(EventData.GraphData::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe { updateData() }
        disposable += rxBus
            .toObservable(EventData.TreatmentData::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe { updateData() }

        updateData()
    }

    override fun onPause() {
        super.onPause()
        isResumed = false
        disposable.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun updateData() {
        if (!isResumed) return

        scope.launch {
            val data = complicationDataRepository.complicationData.first()

            val graphData = data.graphData
            val treatmentData = data.treatmentData

            if (graphData.entries.isEmpty()) {
                val mock = createMockData()
                setupChart(mock.first, mock.second)
                populateMetrics(data.bgData, data.statusData, mock.second)
            } else {
                setupChart(graphData, treatmentData)
                populateMetrics(data.bgData, data.statusData, treatmentData)
            }

            if (!initialScrollDone) {
                initialScrollDone = true
                val metricsPanel = findViewById<android.view.View>(R.id.metrics_panel)
                scrollView.post {
                    scrollView.post {
                        scrollView.scrollTo(metricsPanel.left.coerceAtLeast(0), 0)
                    }
                }
            }
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount > 1) {
            scaleDetector.onTouchEvent(event)
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        finish()
    }

    private fun applyZoom(scaleFactor: Float, focusX: Float) {
        val newScale = currentScale * scaleFactor

        if (newScale < 0.5f || newScale > 3.0f) return

        val oldScrollX = scrollView.scrollX
        val oldChartWidth = chartContainer.width
        val focusPositionInChart = oldScrollX + focusX
        val focusRatio = if (oldChartWidth > 0) focusPositionInChart / oldChartWidth else 0.5f

        currentScale = newScale

        val newChartWidth = (baseChartWidth * currentScale).toInt()
        chartContainer.layoutParams.width = newChartWidth
        chartContainer.requestLayout()

        chartContainer.post {
            val newFocusPositionInChart = focusRatio * chartContainer.width
            val newScrollX = (newFocusPositionInChart - focusX).toInt()
            scrollView.scrollTo(newScrollX.coerceAtLeast(0), 0)
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleX = detector.currentSpanX / detector.previousSpanX
            if (scaleX.isNaN() || scaleX.isInfinite()) return false
            applyZoom(scaleX, detector.focusX)
            return true
        }
    }

    private fun createMockData(): Pair<EventData.GraphData, EventData.TreatmentData> {
        val now = System.currentTimeMillis()
        val entries = ArrayList<EventData.SingleBg>()

        for (i in 0..180) {
            val timeOffset = (180 - i) * 5 * 60 * 1000L
            val timestamp = now - timeOffset
            val baseValue = 120.0
            val variation = sin(i / 20.0) * 40.0 + (Math.random() * 20 - 10)
            val sgv = (baseValue + variation).coerceIn(60.0, 250.0)

            entries.add(
                EventData.SingleBg(
                    dataset = 0,
                    timeStamp = timestamp,
                    sgvString = sgv.toInt().toString(),
                    glucoseUnits = "mg/dl",
                    slopeArrow = "→",
                    delta = "0",
                    deltaDetailed = "0.0",
                    avgDelta = "0",
                    avgDeltaDetailed = "0.0",
                    sgvLevel = when {
                        sgv > 180 -> 1
                        sgv < 70 -> -1
                        else -> 0
                    },
                    sgv = sgv,
                    high = 180.0,
                    low = 70.0,
                    color = when {
                        sgv > 180 -> Color.YELLOW
                        sgv < 70 -> Color.RED
                        else -> Color.WHITE
                    }
                )
            )
        }

        val graphData = EventData.GraphData(entries)

        val boluses = ArrayList<EventData.TreatmentData.Treatment>()
        boluses.add(EventData.TreatmentData.Treatment(date = now - 60 * 60 * 1000L, bolus = 4.0, carbs = 50.0, isSMB = false, isValid = true))
        boluses.add(EventData.TreatmentData.Treatment(date = now - 120 * 60 * 1000L, bolus = 2.5, carbs = 0.0, isSMB = true, isValid = true))
        boluses.add(EventData.TreatmentData.Treatment(date = now - 180 * 60 * 1000L, bolus = 0.0, carbs = 30.0, isSMB = false, isValid = true))
        boluses.add(EventData.TreatmentData.Treatment(date = now - 90 * 60 * 1000L, bolus = 1.5, carbs = 0.0, isSMB = false, isValid = true))

        val basals = ArrayList<EventData.TreatmentData.Basal>()
        val basalRates = listOf(0.8, 0.75, 0.7, 0.65, 0.7, 0.85, 0.95, 1.0, 1.1, 0.95, 0.9, 0.85)
        for (i in 0..11) {
            val startOffset = (11 - i) * 15 * 60 * 1000L
            val endOffset = (10 - i) * 15 * 60 * 1000L
            basals.add(EventData.TreatmentData.Basal(startTime = now - startOffset, endTime = now - endOffset, amount = basalRates[i]))
        }

        val temps = ArrayList<EventData.TreatmentData.TempBasal>()
        temps.add(EventData.TreatmentData.TempBasal(startTime = now - 150 * 60 * 1000L, startBasal = 0.85, endTime = now - 120 * 60 * 1000L, endBasal = 1.4, amount = 1.4))
        temps.add(EventData.TreatmentData.TempBasal(startTime = now - 75 * 60 * 1000L, startBasal = 1.0, endTime = now - 45 * 60 * 1000L, endBasal = 0.3, amount = 0.3))
        temps.add(EventData.TreatmentData.TempBasal(startTime = now - 30 * 60 * 1000L, startBasal = 0.95, endTime = now, endBasal = 1.8, amount = 1.8))

        val predictions = ArrayList<EventData.SingleBg>()
        for (i in 1..36) {
            val timeOffset = i * 5 * 60 * 1000L
            predictions.add(EventData.SingleBg(dataset = 0, timeStamp = now + timeOffset, sgv = 110.0 - i * 0.5, high = 180.0, low = 70.0, color = Color.GRAY))
        }

        val treatmentData = EventData.TreatmentData(temps = temps, basals = basals, boluses = boluses, predictions = predictions)

        return Pair(graphData, treatmentData)
    }

    private fun populateMetrics(latestBg: EventData.SingleBg, status: EventData.Status, treatmentData: EventData.TreatmentData) {
        findViewById<TextView>(R.id.metric_bg)?.text = latestBg.sgvString
        findViewById<TextView>(R.id.metric_direction)?.text = latestBg.slopeArrow

        val minutes = if (latestBg.timeStamp > 0) ((System.currentTimeMillis() - latestBg.timeStamp) / 60000).toInt() else 0
        findViewById<TextView>(R.id.metric_ago)?.text = "$minutes m ago"

        findViewById<TextView>(R.id.metric_delta)?.text = "Δ: ${latestBg.delta}"
        findViewById<TextView>(R.id.metric_delta_15)?.text = "Δ15: ${latestBg.avgDelta}"
        findViewById<TextView>(R.id.metric_delta_40)?.text = "Δ40: ${latestBg.deltaDetailed}"

        findViewById<TextView>(R.id.metric_iob)?.text = status.iobSum
        findViewById<TextView>(R.id.metric_cob)?.text = status.cob

        val now = System.currentTimeMillis()
        val activeTemp = treatmentData.temps.firstOrNull { temp ->
            now >= temp.startTime && now <= temp.endTime
        }

        val basalIcon = findViewById<ImageView>(R.id.basal_icon)
        val basalText = findViewById<TextView>(R.id.metric_basal)
        if (activeTemp != null) {
            basalText?.text = String.format("%.2f U/h", activeTemp.amount)
            basalIcon?.setColorFilter(Color.CYAN)
            basalText?.setTextColor(Color.CYAN)
        } else {
            basalText?.text = status.currentBasal
            basalIcon?.setColorFilter(resources.getColor(R.color.purple_500, null))
            basalText?.setTextColor(Color.WHITE)
        }

        findViewById<TextView>(R.id.metric_autosens)?.text = ""

        findViewById<TextView>(R.id.metric_battery)?.text = status.battery
        findViewById<TextView>(R.id.metric_reservoir)?.text = status.reservoirString
        findViewById<TextView>(R.id.metric_sensor_age)?.text = ""

        findViewById<TextView>(R.id.metric_profile_name)?.text = ""
        findViewById<TextView>(R.id.metric_profile_percentage)?.text = ""
        findViewById<TextView>(R.id.metric_target)?.text = status.tempTarget

        // Loop icon: color based on openApsStatus age
        val loopIcon = findViewById<ImageView>(R.id.metric_loop_status_icon)
        if (status.openApsStatus != -1L) {
            val loopMinutes = ((now - status.openApsStatus) / 1000 / 60).toInt()
            val iconRes = when {
                loopMinutes > 14 -> R.drawable.ic_loop_disconnected
                loopMinutes > 6 -> R.drawable.ic_loop_open
                else -> R.drawable.ic_loop_closed_green
            }
            loopIcon?.setImageResource(iconRes)
        } else {
            loopIcon?.setImageResource(R.drawable.ic_loop_disabled)
        }

        // BG color
        val bgColor = when {
            latestBg.sgv > latestBg.high -> Color.YELLOW
            latestBg.sgv < latestBg.low -> Color.RED
            else -> Color.GREEN
        }
        findViewById<TextView>(R.id.metric_bg)?.setTextColor(bgColor)
    }
}
