package app.aaps.wear.interaction.fragments

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.MetricAffectingSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.SuperscriptSpan
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SnapHelper
import androidx.viewpager2.widget.ViewPager2
import androidx.wear.widget.WearableRecyclerView
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.rx.weardata.EventData.ActionBolusConfirmed
import app.aaps.core.interfaces.rx.weardata.EventData.ActionQuickBolusPreview
import app.aaps.core.interfaces.rx.weardata.EventData.ActionQuickTempTarget
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.wear.R
import app.aaps.wear.tile.Action
import app.aaps.wear.tile.source.TempTargetSource
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.hypot

class QuickBolusFragment : DaggerFragment() {

    companion object {

        private const val RING_FILL_DURATION_MS = 3000L
        private const val RING_FILL_DURATION_CARBS_ONLY_MS = 2000L
        private const val CRESCENT_HOLD_DURATION_MS = 2000L
    }

    @Inject lateinit var sp: SP
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var tempTargetSource: TempTargetSource

    private var currentAmount = 0.0
    private val increment = 0.5
    private var maxBolus = 3.0
    private var wizardSuggestion = 0.0
    private var middleIndex = 1
    private lateinit var recyclerView: WearableRecyclerView
    private var viewPager: ViewPager2? = null
    private var scrollLocked = false

    private var currentIob = 0.0
    private var currentBasalIob = 0.0
    private var currentCob = 0.0
    private var currentIc = 0.0
    private var bolusInProgress = false
    private var bolusAmount = 0.0
    private var carbAmount = 0
    private var carbDeficit = 0
    private var userAdjustedCarbs = false

    private var iobAtButtonPress = 0.0
    private var basalIobAtButtonPress = 0.0
    private var buttonPressed = false

    // Whether the figures currently on screen reflect a confirmed calculator (preview) response
    private var hasReceivedPreviewResponse = false
    private var previewResponsePending = false
    private lateinit var staleCheckRunnable: Runnable

    private lateinit var amountTextView: TextView
    private lateinit var iobSegmentView: TextView
    private lateinit var iobSegmentContainer: LinearLayout
    private lateinit var cobSegmentView: TextView
    private lateinit var cobSegmentContainer: LinearLayout
    private lateinit var eatingCheckbox: CheckBox
    private lateinit var staleWarningIcon: ImageView

    private lateinit var carbDecreaseButton: Button
    private lateinit var carbIncreaseButton: Button
    private lateinit var upButton: Button
    private lateinit var downButton: Button
    private lateinit var goButton: Button
    private lateinit var progressBar: ProgressBar

    private val disposable = CompositeDisposable()
    private lateinit var handler: Handler
    private lateinit var bolusRunnable: Runnable
    private lateinit var progressAnimator: ValueAnimator


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val recyclerView = WearableRecyclerView(requireContext())
        recyclerView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        recyclerView.clipChildren = false
        recyclerView.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.black))
        recyclerView.isEdgeItemsCenteringEnabled = false
        return recyclerView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view as WearableRecyclerView
        viewPager = (activity as? AppCompatActivity)?.findViewById(R.id.view_pager)
        val screenHeight = resources.displayMetrics.heightPixels
        val crescentHeight = (screenHeight * 0.65).toInt()

        maxBolus = sp.getDouble(getString(R.string.key_treatments_safety_max_bolus), 3.0)

        // Higher targets sit above QuickBolus (north), lower targets and cancel below (south). Manual TT has no fixed target, so it is left out.
        val ttActions = tempTargetSource.getActions(resources).associateBy { it.settingName }
        val northActions = listOfNotNull(ttActions["hypo"], ttActions["activity"])
        val southActions = listOfNotNull(ttActions["eating_soon"], ttActions["cancel"])

        val items = northActions + listOf(Action(buttonText = "QB", activityClass = "", iconRes = 0, action = null)) + southActions
        val colors = northActions.map { getColorForAction(it) } + Color.parseColor("#00FF00") + southActions.map { getColorForAction(it) }
        middleIndex = northActions.size

        val adapter = CirclePagerAdapter(items, colors, middleIndex, screenHeight, crescentHeight)
        recyclerView.adapter = adapter
        ClosestItemSnapHelper().attachToRecyclerView(recyclerView)

        recyclerView.setOnTouchListener { _, event ->
            if (scrollLocked) {
                return@setOnTouchListener true
            }
            false
        }

        recyclerView.post {
            recyclerView.scrollToPosition(middleIndex)
        }

        // Subscribe to the preview-only result (dedicated API — never launches wizard UI)
        disposable.add(rxBus
            .toObservable(EventData.ActionQuickBolusPreviewResult::class.java)
            .subscribe { result ->
                activity?.runOnUiThread {
                    handlePreviewResult(result)
                }
            })

    }

    override fun onDestroyView() {
        super.onDestroyView()
        disposable.clear()
    }

    private fun getColorForAction(action: Action): Int {
        return when {
            action.buttonText?.contains("Activity", true) == true -> Color.parseColor("#336666")
            action.buttonText?.contains("Hypo", true) == true -> Color.parseColor("#663333")
            action.buttonText?.contains("Eating", true) == true -> Color.parseColor("#664422")
            else -> Color.parseColor("#444444")
        }
    }

    private fun initializeQBControls(qbView: View) {
        handler = Handler(Looper.getMainLooper())
        amountTextView = qbView.findViewById(R.id.quick_bolus_amount)
            iobSegmentView = qbView.findViewById(R.id.quick_bolus_iob_text)
            iobSegmentContainer = qbView.findViewById(R.id.quick_bolus_iob_segment)
            cobSegmentView = qbView.findViewById(R.id.quick_bolus_cob_text)
            cobSegmentContainer = qbView.findViewById(R.id.quick_bolus_cob_segment)
        eatingCheckbox = qbView.findViewById(R.id.quick_bolus_eating_checkbox)
        carbDecreaseButton = qbView.findViewById(R.id.quick_bolus_carb_decrease)
        carbIncreaseButton = qbView.findViewById(R.id.quick_bolus_carb_increase)
        upButton = qbView.findViewById(R.id.quick_bolus_up)
        downButton = qbView.findViewById(R.id.quick_bolus_down)
        goButton = qbView.findViewById(R.id.quick_bolus_go)
        progressBar = qbView.findViewById(R.id.go_button_progress)
        staleWarningIcon = qbView.findViewById(R.id.quick_bolus_stale_warning)

        staleCheckRunnable = Runnable {
            if (previewResponsePending && !bolusInProgress) {
                staleWarningIcon.visibility = View.VISIBLE
                iobSegmentContainer.visibility = View.GONE
                cobSegmentContainer.visibility = View.GONE
            }
        }

        updateGoButtonState()

        // Fire an initial preview request to populate IC, bolus suggestion, carb deficit
        requestPreview()

        // Refresh on each Status push (same cadence as complications)
        disposable.add(rxBus
            .toObservable(EventData.Status::class.java)
            .subscribe {
                activity?.runOnUiThread { requestPreview() }
            })

        disposable.add(rxBus
            .toObservable(EventData.BolusProgress::class.java)
            .subscribe { event ->
                activity?.runOnUiThread {
                    handleBolusProgress(event)
                }
            })

        eatingCheckbox.setOnCheckedChangeListener { _, _ ->
            userAdjustedCarbs = false
            updateCarbButtonsVisibility()
            updateAmountDisplay()
        }

        carbDecreaseButton.setOnClickListener {
            val nextValue = adjustSteppedValue(carbAmount.toDouble(), 5.0, -1, carbDeficit.toDouble())
            if (nextValue >= 0.0) {
                carbAmount = nextValue.toInt()
                userAdjustedCarbs = true
                updateEatingCheckboxLabel()
                updateIobPreview()
                updateGoButtonState()
            }
        }

        carbIncreaseButton.setOnClickListener {
            val nextValue = adjustSteppedValue(carbAmount.toDouble(), 5.0, 1, carbDeficit.toDouble())
            carbAmount = nextValue.toInt()
            userAdjustedCarbs = true
            updateEatingCheckboxLabel()
            updateIobPreview()
            updateGoButtonState()
        }

        upButton.setOnClickListener {
            if (bolusInProgress) return@setOnClickListener
            val nextStep = adjustSteppedValue(currentAmount, increment, 1, wizardSuggestion)
            if (nextStep <= maxBolus) {
                currentAmount = nextStep
                updateAmountDisplay()
            } else {
                Toast.makeText(requireContext(), getString(R.string.quick_bolus_max_reached, maxBolus), Toast.LENGTH_SHORT).show()
            }
        }

        downButton.setOnClickListener {
            if (bolusInProgress) return@setOnClickListener
            val prevStep = adjustSteppedValue(currentAmount, increment, -1, wizardSuggestion)
            if (prevStep >= 0.0) {
                currentAmount = prevStep
                updateAmountDisplay()
            }
        }

        bolusRunnable = Runnable {
            val hasCarbs = eatingCheckbox.isChecked && carbAmount > 0
            val hasBolus = currentAmount > 0.0

            if (!hasBolus && !hasCarbs) {
                Toast.makeText(requireContext(), R.string.quick_bolus_zero_amount, Toast.LENGTH_SHORT).show()
            } else if (hasBolus) {
                executeBolus()
            } else {
                rxBus.send(EventWearToMobile(ActionBolusConfirmed(currentAmount, carbAmount)))
                activity?.finish()
            }
        }

        progressAnimator = ValueAnimator.ofInt(0, 100)

        progressAnimator.addUpdateListener { animator ->
            progressBar.progress = animator.animatedValue as Int
        }

        goButton.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (bolusInProgress) return@setOnTouchListener true
                    buttonPressed = true
                    recyclerView.requestDisallowInterceptTouchEvent(true)
                    viewPager?.isUserInputEnabled = false
                    handler.postDelayed({ scrollLocked = true }, 50)
                    iobAtButtonPress = currentIob
                    basalIobAtButtonPress = currentBasalIob
                    val fillDuration = if (currentAmount == 0.0) RING_FILL_DURATION_CARBS_ONLY_MS else RING_FILL_DURATION_MS
                    progressAnimator.duration = fillDuration
                    handler.postDelayed(bolusRunnable, fillDuration)
                    progressBar.visibility = View.VISIBLE
                    progressAnimator.start()
                    upButton.visibility = View.INVISIBLE
                    downButton.visibility = View.INVISIBLE
                    carbDecreaseButton.visibility = View.INVISIBLE
                    carbIncreaseButton.visibility = View.INVISIBLE
                    eatingCheckbox.isEnabled = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val isInBounds = event.x >= 0 && event.x <= v.width &&
                            event.y >= 0 && event.y <= v.height
                    if (!isInBounds) {
                        handler.removeCallbacks(bolusRunnable)
                        if (!bolusInProgress) {
                            restoreControls()
                        }
                        buttonPressed = false
                        scrollLocked = false
                        viewPager?.isUserInputEnabled = true
                        recyclerView.requestDisallowInterceptTouchEvent(false)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    scrollLocked = false
                    viewPager?.isUserInputEnabled = true
                    recyclerView.requestDisallowInterceptTouchEvent(false)
                    handler.removeCallbacks(bolusRunnable)
                    if (!bolusInProgress) {
                        restoreControls()
                    }
                    buttonPressed = false
                }
            }
            true
        }
    }

    private fun handlePreviewResult(result: EventData.ActionQuickBolusPreviewResult) {
        if (bolusInProgress) return
        previewResponsePending = false
        hasReceivedPreviewResponse = true
        handler.removeCallbacks(staleCheckRunnable)
        staleWarningIcon.visibility = View.GONE
        val userHasNotAdjusted = currentAmount == 0.0 && !userAdjustedCarbs

        currentIc = result.ic
        val bolusIob = -result.bolusIob
        val basalIob = -result.basalIob
        val newIob = bolusIob + basalIob
        currentBasalIob = basalIob
        currentCob = result.cob

        if (buttonPressed && (Math.abs(newIob - iobAtButtonPress) > 0.01 || Math.abs(basalIob - basalIobAtButtonPress) > 0.01)) {
            handler.removeCallbacks(bolusRunnable)
            if (!bolusInProgress) {
                restoreControls()
            }
            buttonPressed = false
        }

        currentIob = newIob

        if (result.totalInsulin > 0.0) {
            wizardSuggestion = result.totalInsulin.coerceAtMost(maxBolus)
            if (userHasNotAdjusted) {
                currentAmount = wizardSuggestion
            }
        }

        if (result.carbsEquivalent > 0.0) {
            carbDeficit = result.carbsEquivalent.toInt()
        }

        updateAmountDisplay(animate = true)
    }

    private fun requestPreview() {
        if (bolusInProgress) return
        previewResponsePending = true
        handler.removeCallbacks(staleCheckRunnable)
        handler.postDelayed(staleCheckRunnable, 2000)
        rxBus.send(EventWearToMobile(ActionQuickBolusPreview()))
    }

    private fun flashSegment(view: TextView) {
        view.animate().cancel()
        view.alpha = 0.25f
        view.animate().alpha(1.0f).setDuration(500).start()
    }

    private fun updateAmountDisplay(animate: Boolean = false) {
        amountTextView.text = String.format(Locale.US, "%.1f U", currentAmount)

        val isOnWizardSuggestion = wizardSuggestion > 0.0 && Math.abs(currentAmount - wizardSuggestion) < 0.01
        val amountColorRes = if (isOnWizardSuggestion) R.color.colorInsulinButton else R.color.white
        amountTextView.setTextColor(ContextCompat.getColor(requireContext(), amountColorRes))

        carbAmount = calculateCarbAmount(
            currentAmount = currentAmount,
            currentIc = currentIc,
            carbDeficit = carbDeficit,
            eatingCheckboxChecked = eatingCheckbox.isChecked,
            currentCarbAmount = carbAmount
        )
        updateEatingCheckboxLabel()
        updateIobPreview(animate)
        updateGoButtonState()
    }

    internal fun calculateCarbAmount(
        currentAmount: Double,
        currentIc: Double,
        carbDeficit: Int,
        eatingCheckboxChecked: Boolean,
        currentCarbAmount: Int
    ): Int {
        val shouldUpdateCarbs = !userAdjustedCarbs && eatingCheckboxChecked
        val canUseIcRatio = shouldUpdateCarbs && currentIc > 0.0 && currentAmount > 0.0
        val hasDeficit = carbDeficit > 0

        return when {
            canUseIcRatio -> (currentAmount * currentIc).toInt()
            shouldUpdateCarbs && hasDeficit -> carbDeficit
            else -> currentCarbAmount
        }
    }

    private fun updateEatingCheckboxLabel() {
        if (eatingCheckbox.isChecked) {
            eatingCheckbox.text = "Eating ${carbAmount}g"
            val colorRes = if (carbDeficit > 0 && carbAmount == carbDeficit) R.color.colorCarbsButton else R.color.white
            eatingCheckbox.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
        } else if (carbDeficit > 0) {
            val text = "Eating ${carbDeficit}g"
            val spannable = SpannableString(text)
            val orangeColor = ContextCompat.getColor(requireContext(), R.color.colorCarbsButton)
            val grayColor = ContextCompat.getColor(requireContext(), R.color.gray_700)
            spannable.setSpan(ForegroundColorSpan(orangeColor), 0, 7, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(ForegroundColorSpan(grayColor), 7, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            eatingCheckbox.text = spannable
        } else {
            eatingCheckbox.text = "Eating"
            eatingCheckbox.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_500))
        }
    }

    private fun updateCarbButtonsVisibility() {
        val visible = eatingCheckbox.isChecked
        carbDecreaseButton.visibility = if (visible) View.VISIBLE else View.GONE
        carbIncreaseButton.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun isOnGrid(value: Double, stepSize: Double): Boolean =
        value == Math.round(value / stepSize) * stepSize

    internal fun adjustSteppedValue(current: Double, stepSize: Double, direction: Int, suggestion: Double = 0.0): Double {
        val round = if (direction > 0) Math::ceil else Math::floor

        val currentInSteps = current / stepSize

        val gridValueInSteps = if (isOnGrid(current, stepSize)) {
            currentInSteps + direction
        } else {
            round(currentInSteps)
        }

        var resultValue = gridValueInSteps * stepSize

        val suggestionWanted = suggestion > 0.0 && !isOnGrid(suggestion, stepSize)
        val suggestionInRange = (suggestion - current) * direction > 0 && (resultValue - suggestion) * direction > 0

        if (suggestionWanted && suggestionInRange) {
            return suggestion
        }

        return resultValue.coerceAtLeast(0.0)
    }

    private fun updateIobPreview(animate: Boolean = false) {
        val iob = currentIob + currentAmount
        val absDelta = if (currentBasalIob < -0.1) -currentBasalIob else null
        val showCob = eatingCheckbox.isChecked && currentCob > 0.0
        val gray = ContextCompat.getColor(requireContext(), R.color.grey_500)

        iobSegmentContainer.visibility = if (hasReceivedPreviewResponse) View.VISIBLE else View.GONE
        iobSegmentView.text = buildIobText(iob, absDelta)

        if (showCob) renderSegment(cobSegmentView, buildSegment(R.drawable.ic_carbs_orange, 0, null, (currentCob + carbAmount).toInt().toString(), "g", gray), animate)
        else {
            cobSegmentView.visibility = View.GONE
            cobSegmentContainer.visibility = View.GONE
        }
    }

    private fun buildIobText(iob: Double, absDelta: Double?): SpannableStringBuilder {
        val ssb = SpannableStringBuilder()
        ssb.append("%.1f".format(iob))
        ssb.setSpan(StyleSpan(Typeface.BOLD), 0, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ssb.append(" U")
        if (absDelta != null) {
            ssb.append(" ")
            val supStart = ssb.length
            ssb.append("+%.1f".format(absDelta).replace("+0.", "+."))
            ssb.setSpan(SuperscriptSpan(), supStart, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            ssb.setSpan(RelativeSizeSpan(0.7f), supStart, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            ssb.setSpan(StyleSpan(Typeface.BOLD), supStart, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            ssb.setSpan(LetterSpacingSpan(-0.08f), supStart, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            ssb.setSpan(ForegroundColorSpan(Color.parseColor("#C68080")), supStart, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return ssb
    }

    private fun renderSegment(view: TextView, newContent: SpannableStringBuilder, animate: Boolean) {
        val oldText = view.text?.toString().orEmpty()
        val wasVisible = view.visibility == View.VISIBLE
        view.text = newContent
        view.visibility = View.VISIBLE
        if (view == cobSegmentView) cobSegmentContainer.visibility = View.VISIBLE
        if (animate && wasVisible && oldText.isNotEmpty() && oldText != newContent.toString()) flashSegment(view)
    }

    private fun buildSegment(iconRes: Int, iconSize: Int, iconTint: Int?, num: String, suffix: String, color: Int): SpannableStringBuilder {
        val ssb = SpannableStringBuilder()
        appendIcon(ssb, iconRes, iconSize, iconTint)
        ssb.append(" ")
        val numStart = ssb.length
        ssb.append(num)
        ssb.setSpan(StyleSpan(Typeface.BOLD), numStart, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ssb.append(suffix)
        ssb.setSpan(ForegroundColorSpan(color), numStart, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return ssb
    }

    private fun appendIcon(ssb: SpannableStringBuilder, @DrawableRes drawableRes: Int, sizePx: Int, tint: Int? = null) {
        val drawable = ContextCompat.getDrawable(requireContext(), drawableRes)?.mutate() ?: return
        drawable.setBounds(0, 0, sizePx, sizePx)
        if (tint != null) drawable.setTint(tint)
        val start = ssb.length
        ssb.append(" ") // Placeholder for the icon
        ssb.setSpan(CenteredImageSpan(drawable), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private class CenteredImageSpan(drawable: Drawable) : ImageSpan(drawable) {
        override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
            val b = drawable
            canvas.save()
            val transY = (bottom - b.bounds.bottom) / 2 + top / 2
            canvas.translate(x, transY.toFloat())
            b.draw(canvas)
            canvas.restore()
        }
    }

    private fun updateGoButtonState() {
        if (bolusInProgress) return

        val hasCarbs = eatingCheckbox.isChecked && carbAmount > 0
        val hasBolus = currentAmount > 0.0
        val isEnabled = hasCarbs || hasBolus

        goButton.isEnabled = isEnabled
        goButton.alpha = if (isEnabled) 1.0f else 0.5f

        val iconRes = when {
            !hasBolus && hasCarbs -> R.drawable.ic_carbs
            hasBolus && !hasCarbs -> R.drawable.ic_bolus
            hasBolus && hasCarbs -> R.drawable.ic_bolus_carbs
            else -> null
        }

        if (iconRes != null) {
            goButton.text = ""
            goButton.setCompoundDrawables(null, null, null, null)

            goButton.post {
                val circleDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.circle)
                val iconDrawable = ContextCompat.getDrawable(requireContext(), iconRes)

                val layers = arrayOf(circleDrawable, iconDrawable)
                val layerDrawable = LayerDrawable(layers)

                val inset = if (goButton.width > 0) (goButton.width * 0.2).toInt() else 20
                layerDrawable.setLayerInset(1, inset, inset, inset, inset)

                goButton.background = layerDrawable
                goButton.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.green_500)
            }
        } else {
            goButton.background = ContextCompat.getDrawable(requireContext(), R.drawable.circle)
            goButton.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.green_500)
            goButton.text = ""
        }
    }

    private fun executeBolus() {
        bolusAmount = currentAmount
        bolusInProgress = true
        scrollLocked = false
        val carbs = if (eatingCheckbox.isChecked) carbAmount else 0
        rxBus.send(EventWearToMobile(ActionBolusConfirmed(currentAmount, carbs)))
        showBolusProgressState()
    }

    private fun handleBolusProgress(event: EventData.BolusProgress) {
        if (!bolusInProgress) {
            bolusInProgress = true
            showBolusProgressState()
        }

        if (bolusAmount > 0.0) {
            val deliveredAmount = bolusAmount * (event.percent / 100.0)
            val liveIob = currentIob + deliveredAmount
            val absDelta = if (currentBasalIob < -0.1) -currentBasalIob else null
            iobSegmentView.text = buildIobText(liveIob, absDelta)
        }

        progressBar.progress = event.percent

        if (event.percent >= 100) {
            bolusInProgress = false
            Handler(Looper.getMainLooper()).postDelayed({
                activity?.finish()
            }, 2000)
        }
    }

    private fun restoreControls() {
        progressBar.visibility = View.INVISIBLE
        progressAnimator.cancel()
        progressBar.progress = 0
        upButton.visibility = View.VISIBLE
        downButton.visibility = View.VISIBLE
        updateCarbButtonsVisibility()
        eatingCheckbox.isEnabled = true
    }

    private fun showBolusProgressState() {
        upButton.isEnabled = false
        upButton.alpha = 0.3f
        downButton.isEnabled = false
        downButton.alpha = 0.3f
        goButton.isEnabled = false
        goButton.alpha = 0.3f
        carbDecreaseButton.visibility = View.GONE
        carbIncreaseButton.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        eatingCheckbox.isEnabled = false
    }

    private inner class CirclePagerAdapter(
        val items: List<Action>,
        val colors: List<Int>,
        val middleIndex: Int,
        val screenHeight: Int,
        val crescentHeight: Int
    ) : RecyclerView.Adapter<CirclePagerAdapter.ViewHolder>() {

        inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view)

        override fun getItemCount() = colors.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val lp = RecyclerView.LayoutParams(screenHeight, screenHeight)

            when {
                viewType < middleIndex -> lp.bottomMargin = -crescentHeight
                viewType > middleIndex -> lp.topMargin = -crescentHeight
            }

            val distanceFromMiddle = abs(viewType - middleIndex)
            val elevation = (middleIndex - distanceFromMiddle).toFloat()

            val view = if (viewType == middleIndex) {
                val qbCircle = FrameLayout(parent.context)
                val qbDrawable = parent.context.getDrawable(R.drawable.qb_circle)?.mutate()
                qbDrawable?.setTint(Color.BLACK)
                qbCircle.background = qbDrawable

                val qbContent = LayoutInflater.from(parent.context).inflate(R.layout.quick_bolus_content, qbCircle, false)
                qbCircle.addView(qbContent)

                // Clip to circle
                qbCircle.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setOval(0, 0, view.width, view.height)
                    }
                }
                qbCircle.clipToOutline = true

                initializeQBControls(qbContent)

                qbCircle
            } else {
                val outerContainer = FrameLayout(parent.context)
                val isNorth = viewType < middleIndex
                val circleColor = if (viewType < colors.size) colors[viewType] else Color.GRAY

                val cornerRadius = screenHeight / 2f

                val circleDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(circleColor)
                    if (isNorth) {
                        setCornerRadii(floatArrayOf(cornerRadius, cornerRadius, cornerRadius, cornerRadius, 0f, 0f, 0f, 0f))
                    } else {
                        setCornerRadii(floatArrayOf(0f, 0f, 0f, 0f, cornerRadius, cornerRadius, cornerRadius, cornerRadius))
                    }
                }

                val borderDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(Color.TRANSPARENT)
                    setStroke(2, Color.BLACK)
                    if (isNorth) {
                        setCornerRadii(floatArrayOf(cornerRadius, cornerRadius, cornerRadius, cornerRadius, 0f, 0f, 0f, 0f))
                    } else {
                        setCornerRadii(floatArrayOf(0f, 0f, 0f, 0f, cornerRadius, cornerRadius, cornerRadius, cornerRadius))
                    }
                }

                val layerDrawable = LayerDrawable(arrayOf(circleDrawable, borderDrawable))

                val rippleDrawable = RippleDrawable(
                    ColorStateList.valueOf(Color.argb(80, 255, 255, 255)),
                    layerDrawable,
                    null
                )
                outerContainer.background = rippleDrawable
                outerContainer.isClickable = true

                val innerContainer = LinearLayout(parent.context)
                innerContainer.orientation = LinearLayout.VERTICAL
                innerContainer.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                innerContainer.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

                val hLayout = LinearLayout(parent.context)
                hLayout.orientation = LinearLayout.HORIZONTAL
                hLayout.gravity = Gravity.CENTER_VERTICAL
                hLayout.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)

                val action = items[viewType]
                if (action.iconRes != 0) {
                    val icon = ImageView(parent.context)
                    icon.setImageResource(action.iconRes)
                    icon.layoutParams = LinearLayout.LayoutParams(48, 48)
                    (icon.layoutParams as LinearLayout.LayoutParams).marginEnd = 12
                    hLayout.addView(icon)
                }

                val label = TextView(parent.context)
                label.text = action.buttonText ?: "TT"
                label.setTextColor(Color.WHITE)
                label.textSize = 18f
                label.setTypeface(null, Typeface.BOLD)
                hLayout.addView(label)

                val hlayoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                if (viewType > middleIndex) hlayoutParams.bottomMargin = 50 else hlayoutParams.topMargin = 50
                innerContainer.addView(hLayout, hlayoutParams)

                // Override gravity for south TT circles
                if (viewType > middleIndex) innerContainer.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                outerContainer.addView(innerContainer)

                val holdWipe = if (isNorth) CrescentWipeDrawable(startAngle = 150f, fullSweep = 240f) else CrescentWipeDrawable(startAngle = 210f, fullSweep = -240f)
                outerContainer.foreground = holdWipe
                val holdWipeAnimator = ValueAnimator.ofInt(0, 10000).apply {
                    duration = CRESCENT_HOLD_DURATION_MS
                    addUpdateListener { holdWipe.level = it.animatedValue as Int }
                }

                val commitHold = Runnable {
                    val command = (action.action as? EventData.ActionTempTargetPreCheck)?.command
                    if (command != null) {
                        outerContainer.animate()
                            .scaleX(1.1f)
                            .scaleY(1.1f)
                            .setDuration(150)
                            .withEndAction {
                                outerContainer.animate()
                                    .scaleX(1.0f)
                                    .scaleY(1.0f)
                                    .setDuration(150)
                                    .start()
                            }
                            .start()
                        outerContainer.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        rxBus.send(EventWearToMobile(ActionQuickTempTarget(command)))
                        val message = when {
                            action.buttonText?.contains("Cancel", true) == true -> "Temp target cancelled"
                            else -> "${action.buttonText} activated"
                        }
                        Toast.makeText(parent.context, message, Toast.LENGTH_SHORT).show()
                        recyclerView.smoothScrollToPosition(middleIndex)
                    }
                }

                fun abortHold() {
                    handler.removeCallbacks(commitHold)
                    holdWipeAnimator.cancel()
                    holdWipe.level = 0
                    recyclerView.requestDisallowInterceptTouchEvent(false)
                }

                var holdStartX = 0f
                var holdStartY = 0f
                outerContainer.setOnTouchListener { _, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            holdStartX = event.x
                            holdStartY = event.y
                            recyclerView.requestDisallowInterceptTouchEvent(true)
                            holdWipeAnimator.start()
                            handler.postDelayed(commitHold, CRESCENT_HOLD_DURATION_MS)
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (hypot(event.x - holdStartX, event.y - holdStartY) > goButton.width / 2f) abortHold()
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> abortHold()
                    }
                    true
                }
                outerContainer
            }

            view.layoutParams = lp
            view.elevation = elevation

            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.view.visibility = View.VISIBLE
        }
        override fun getItemViewType(position: Int) = position
    }

    private class CrescentWipeDrawable(private val startAngle: Float, private val fullSweep: Float) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(80, 255, 255, 255) }

        override fun draw(canvas: Canvas) = canvas.drawArc(RectF(bounds), startAngle, fullSweep * level / 10000f, true, paint)
        override fun onLevelChange(level: Int): Boolean {
            invalidateSelf()
            return true
        }
        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    private class LetterSpacingSpan(private val spacing: Float) : MetricAffectingSpan() {
        override fun updateDrawState(tp: TextPaint) { tp.letterSpacing = spacing }
        override fun updateMeasureState(tp: TextPaint) { tp.letterSpacing = spacing }
    }

    private class ClosestItemSnapHelper : SnapHelper() {
        override fun calculateDistanceToFinalSnap(layoutManager: RecyclerView.LayoutManager, targetView: View): IntArray {
            val distance = IntArray(2); distance[0] = 0; distance[1] = targetView.top; return distance
        }
        override fun findSnapView(layoutManager: RecyclerView.LayoutManager?): View? {
            if (layoutManager !is LinearLayoutManager) return null
            var closestView: View? = null; var closestDistance = Int.MAX_VALUE
            for (i in 0 until layoutManager.childCount) {
                val child = layoutManager.getChildAt(i) ?: continue
                val distance = abs(child.top)
                if (distance < closestDistance) { closestDistance = distance; closestView = child }
            }
            return closestView
        }
        override fun findTargetSnapPosition(layoutManager: RecyclerView.LayoutManager, velocityX: Int, velocityY: Int): Int {
            if (layoutManager !is LinearLayoutManager) return RecyclerView.NO_POSITION
            var closestPosition = RecyclerView.NO_POSITION; var closestDistance = Int.MAX_VALUE
            var firstChild: View? = null; var secondChild: View? = null
            for (i in 0 until layoutManager.childCount) {
                val child = layoutManager.getChildAt(i) ?: continue
                if (firstChild == null) firstChild = child else if (secondChild == null) secondChild = child
                val distance = abs(child.top)
                if (distance < closestDistance) { closestDistance = distance; closestPosition = layoutManager.getPosition(child) }
            }
            if (closestPosition == RecyclerView.NO_POSITION) return RecyclerView.NO_POSITION

            val distancePerChild = if (firstChild != null && secondChild != null) {
                abs(secondChild.top - firstChild.top)
            } else {
                firstChild?.height ?: 0
            }
            if (distancePerChild <= 0) return closestPosition

            val flingDistance = calculateScrollDistance(velocityX, velocityY)[1]
            val positionDiff = Math.round(flingDistance.toFloat() / distancePerChild)
            val targetPosition = closestPosition + positionDiff
            return targetPosition.coerceIn(0, layoutManager.itemCount - 1)
        }
    }
}
