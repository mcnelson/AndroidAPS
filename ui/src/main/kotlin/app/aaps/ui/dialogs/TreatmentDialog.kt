package app.aaps.ui.dialogs

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.defs.determineCorrectBolusStepSize
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.extensions.formatColor
import app.aaps.core.objects.extensions.round
import app.aaps.core.objects.extensions.valueToUnits
import app.aaps.core.objects.wizard.BolusWizard
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.extensions.runOnUiThread
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.utils.HtmlHelper
import app.aaps.ui.R
import app.aaps.ui.databinding.DialogTreatmentBinding
import com.google.common.base.Joiner
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.text.DecimalFormat
import java.util.LinkedList
import javax.inject.Inject
import javax.inject.Provider
import kotlin.math.abs

class TreatmentDialog : DialogFragmentWithDate() {

    companion object {
        private const val SP_KEY_STEP_MODE = "quickbolus_step_mode"

        fun adjustSteppedValue(current: Double, stepSize: Double, direction: Int, suggestion: Double = 0.0): Double =
            QuickBolusModel.adjustSteppedValue(current, stepSize, direction, suggestion)

        fun calculateCarbAmount(currentAmount: Double, currentIc: Double, carbDeficit: Int, currentCarbAmount: Int): Int =
            QuickBolusModel.calculateCarbAmount(currentAmount, currentIc, carbDeficit, currentCarbAmount)

        internal fun isOnGrid(value: Double, stepSize: Double): Boolean =
            QuickBolusModel.isOnGrid(value, stepSize)

        fun buildIobText(label: String, iob: Double, currentBasalIob: Double): String =
            QuickBolusModel.buildIobText(label, iob, currentBasalIob)
    }

    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var ctx: Context
    @Inject lateinit var config: Config
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var decimalFormatter: DecimalFormatter
    @Inject lateinit var sp: SP
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var bolusWizardProvider: Provider<BolusWizard>

    private val quickMode get() = arguments?.getBoolean("quickMode", false) ?: false

    private var queryingProtection = false
    private var _binding: DialogTreatmentBinding? = null

    private val disposable = CompositeDisposable()

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!
    private var handler: Handler? = null
    private lateinit var model: QuickBolusModel

    // Classic mode text watcher
    private val textWatcher: TextWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable) {}
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            validateInputs()
        }
    }

    // Quick mode text watcher — only validates constraints; model owns state
    private val quickTextWatcher: TextWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable) { _binding?.let { validateInputs() } }
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
    }

    private fun validateInputs() {
        val maxCarbs = constraintChecker.getMaxCarbsAllowed().value().toDouble()
        val maxInsulin = constraintChecker.getMaxBolusAllowed().value()
        if (quickMode) {
            if (binding.qb.carbs.value > maxCarbs) {
                binding.qb.carbs.value = 0.0
                ToastUtils.warnToast(context, R.string.carbs_constraint_applied)
            }
            if (binding.qb.insulin.value > maxInsulin) {
                binding.qb.insulin.value = 0.0
                ToastUtils.warnToast(context, R.string.bolus_constraint_applied)
            }
        } else {
            if (SafeParse.stringToInt(binding.carbs.text) > maxCarbs) {
                binding.carbs.value = 0.0
                ToastUtils.warnToast(context, R.string.carbs_constraint_applied)
            }
            if (SafeParse.stringToDouble(binding.insulin.text) > maxInsulin) {
                binding.insulin.value = 0.0
                ToastUtils.warnToast(context, R.string.bolus_constraint_applied)
            }
        }
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        _binding ?: return
        if (quickMode) {
            val state = model.saveState()
            savedInstanceState.putDouble("insulin", state.insulinValue)
            savedInstanceState.putDouble("carbs", state.carbsValue)
            savedInstanceState.putBoolean("eatingChecked", state.eatingChecked)
            savedInstanceState.putBoolean("userAdjustedCarbs", state.userAdjustedCarbs)
            savedInstanceState.putDouble("wizardSuggestion", state.wizardSuggestion)
            savedInstanceState.putDouble("currentIob", state.currentIob)
            savedInstanceState.putDouble("currentBasalIob", state.currentBasalIob)
            savedInstanceState.putDouble("currentIc", state.currentIc)
            savedInstanceState.putInt("carbDeficit", state.carbDeficit)
        } else {
            savedInstanceState.putDouble("carbs", binding.carbs.value)
            savedInstanceState.putDouble("insulin", binding.insulin.value)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        onCreateViewGeneral()
        _binding = DialogTreatmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (quickMode) {
            binding.recordOnlyLayout.visibility = View.GONE
            binding.insulinLayout.visibility = View.GONE
            binding.carbsLayout.visibility = View.GONE
            binding.qb.root.visibility = View.VISIBLE
            setupQuickMode(savedInstanceState)
        } else {
            setupClassicMode(savedInstanceState)
        }
    }

    private fun setupClassicMode(savedInstanceState: Bundle?) {
        if (config.AAPSCLIENT) {
            binding.recordOnly.isChecked = true
            binding.recordOnly.isEnabled = false
        }
        val maxCarbs = constraintChecker.getMaxCarbsAllowed().value().toDouble()
        val maxInsulin = constraintChecker.getMaxBolusAllowed().value()
        val pumpDescription = activePlugin.activePump.pumpDescription
        binding.carbs.setParams(
            savedInstanceState?.getDouble("carbs")
                ?: 0.0, 0.0, maxCarbs, 1.0, DecimalFormat("0"), false, binding.okcancel.ok, textWatcher
        )
        binding.insulin.setParams(
            savedInstanceState?.getDouble("insulin")
                ?: 0.0,
            0.0,
            maxInsulin,
            pumpDescription.bolusStep,
            decimalFormatter.pumpSupportedBolusFormat(activePlugin.activePump.pumpDescription.bolusStep),
            false,
            binding.okcancel.ok,
            textWatcher
        )
        binding.recordOnlyLayout.visibility = View.GONE
        binding.insulinLabel.labelFor = binding.insulin.editTextId
        binding.carbsLabel.labelFor = binding.carbs.editTextId
    }

    private fun setupQuickMode(savedInstanceState: Bundle?) {
        if (config.AAPSCLIENT) binding.recordOnly.isChecked = true
        val maxInsulin = constraintChecker.getMaxBolusAllowed().value()
        val maxCarbs = constraintChecker.getMaxCarbsAllowed().value().toDouble()
        val bolusStep = activePlugin.activePump.pumpDescription.bolusStep

        val bolusIob = iobCobCalculator.calculateIobFromBolus().round()
        val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
        model = QuickBolusModel(
            maxInsulin = maxInsulin,
            maxCarbs = maxCarbs,
            bolusStep = bolusStep,
            initialIob = bolusIob.iob + basalIob.basaliob,
            initialBasalIob = basalIob.basaliob,
            initialStepModeIndex = sp.getInt(SP_KEY_STEP_MODE, 1)
        )

        if (savedInstanceState != null) {
            model.restoreState(
                QuickBolusModel.SavedState(
                    insulinValue = savedInstanceState.getDouble("insulin"),
                    carbsValue = savedInstanceState.getDouble("carbs"),
                    eatingChecked = savedInstanceState.getBoolean("eatingChecked"),
                    userAdjustedCarbs = savedInstanceState.getBoolean("userAdjustedCarbs"),
                    wizardSuggestion = savedInstanceState.getDouble("wizardSuggestion"),
                    currentIob = savedInstanceState.getDouble("currentIob"),
                    currentBasalIob = savedInstanceState.getDouble("currentBasalIob"),
                    currentIc = savedInstanceState.getDouble("currentIc"),
                    carbDeficit = savedInstanceState.getInt("carbDeficit")
                )
            )
        } else {
            handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
            handler?.post { computeWizardSuggestion(maxInsulin) }
        }

        binding.qb.insulin.setParams(
            model.insulinValue, 0.0, maxInsulin, bolusStep,
            DecimalFormat("0.0"),
            false, binding.okcancel.ok, quickTextWatcher
        )
        binding.qb.carbs.setParams(
            model.carbsValue, 0.0, maxCarbs, model.carbStep,
            DecimalFormat("0"), false, binding.okcancel.ok, quickTextWatcher
        )

        binding.qb.insulin.hideStepButtons()
        binding.qb.insulin.findViewById<android.widget.EditText>(binding.qb.insulin.editTextId)
            ?.setTypeface(null, android.graphics.Typeface.BOLD)
        binding.qb.carbs.hideStepButtons()
        binding.qb.eatingCheckbox.isChecked = model.eatingChecked

        syncViewsFromModel()

        binding.qb.stepSizeButton.setOnClickListener {
            sp.putInt(SP_KEY_STEP_MODE, model.onStepModeChanged())
            binding.qb.insulin.step = model.insulinStep
            binding.qb.carbs.step = model.carbStep
            binding.qb.stepSizeButton.text = model.stepModeLabel
        }

        binding.qb.eatingCheckbox.setOnCheckedChangeListener { _, isChecked ->
            model.onEatingToggled(isChecked)
            syncViewsFromModel()
        }

        binding.qb.eatingRow.setOnClickListener {
            if (!model.eatingChecked) binding.qb.eatingCheckbox.isChecked = true
        }

        binding.qb.insulinDown.setOnClickListener {
            if (model.onInsulinDown()) syncViewsFromModel()
        }

        binding.qb.insulinUp.setOnClickListener {
            if (model.onInsulinUp()) {
                syncViewsFromModel()
            } else {
                ToastUtils.warnToast(requireContext(), rh.gs(app.aaps.core.ui.R.string.constraint_applied))
            }
        }

        binding.qb.carbsDown.setOnClickListener {
            if (model.onCarbsDown()) syncViewsFromModel()
        }

        binding.qb.carbsUp.setOnClickListener {
            if (model.onCarbsUp()) syncViewsFromModel()
        }
    }

    private fun syncViewsFromModel() {
        binding.qb.insulin.value = model.insulinValue
        binding.qb.carbs.value = model.carbsValue
        binding.qb.stepSizeButton.text = model.stepModeLabel
        updateCarbsVisibility()
        updateInsulinColor()
        updateCarbsColor()
        updateIobPreview()
    }

    private var defaultInsulinTextColor: Int? = null
    private var defaultCarbsTextColor: Int? = null

    private fun updateInsulinColor() {
        val editText = binding.qb.insulin.findViewById<android.widget.EditText>(binding.qb.insulin.editTextId) ?: return
        if (defaultInsulinTextColor == null) defaultInsulinTextColor = editText.currentTextColor
        editText.setTextColor(
            if (model.isOnInsulinSuggestion) ContextCompat.getColor(requireContext(), app.aaps.core.ui.R.color.colorInsulinButton)
            else defaultInsulinTextColor!!
        )
    }

    private fun updateCarbsColor() {
        val orange = ContextCompat.getColor(requireContext(), app.aaps.core.ui.R.color.colorCarbsButton)
        binding.qb.eatingCheckbox.setTextColor(
            if (model.shouldShowCarbDeficitHint) orange
            else ContextCompat.getColor(requireContext(), app.aaps.core.ui.R.color.defaultTextColor)
        )
        val editText = binding.qb.carbs.findViewById<android.widget.EditText>(binding.qb.carbs.editTextId) ?: return
        if (defaultCarbsTextColor == null) defaultCarbsTextColor = editText.currentTextColor
        editText.setTextColor(if (model.isAtCarbDeficit) orange else defaultCarbsTextColor!!)
    }

    private fun computeWizardSuggestion(maxInsulin: Double) {
        val profile = profileFunction.getProfile() ?: return
        val profileName = profileFunction.getProfileName()
        val bgReading = iobCobCalculator.ads.actualBg() ?: return
        val cobInfo = iobCobCalculator.getCobInfo("QuickBolus")
        val cob = cobInfo.displayCob ?: return
        val tempTarget = persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now())
        val units = profileFunction.getUnits()

        val ic = profile.getIc()

        val wizard = bolusWizardProvider.get().doCalc(
            profile = profile,
            profileName = profileName,
            tempTarget = tempTarget,
            carbs = 0,
            cob = cob,
            bg = bgReading.valueToUnits(units),
            correction = 0.0,
            percentageCorrection = 100,
            useBg = true,
            useCob = true,
            includeBolusIOB = true,
            includeBasalIOB = true,
            useSuperBolus = false,
            useTT = true,
            useTrend = true,
            useAlarm = false
        )

        val suggestion = wizard.calculatedTotalInsulin.coerceAtLeast(0.0).coerceAtMost(maxInsulin)
        val deficit = if (wizard.carbsEquivalent > 0.0) wizard.carbsEquivalent.toInt() else 0

        runOnUiThread {
            if (_binding == null) return@runOnUiThread
            model.onWizardResult(suggestion, deficit, ic)
            syncViewsFromModel()
        }
    }

    private fun updateCarbsVisibility() {
        val checked = model.eatingChecked
        binding.qb.carbsUp.visibility = if (checked) View.VISIBLE else View.INVISIBLE
        binding.qb.carbsDown.visibility = if (checked) View.VISIBLE else View.INVISIBLE
        val vis = if (checked) View.VISIBLE else View.GONE
        binding.qb.carbs.visibility = vis
        binding.qb.carbsUnit.visibility = vis
        // Show muted "Xg" hint next to checkbox when unchecked with a carb deficit
        if (!checked && model.shouldShowCarbDeficitHint) {
            binding.qb.carbDeficitHint.text = "${model.carbDeficit}g"
            binding.qb.carbDeficitHint.setTextColor(
                ContextCompat.getColor(requireContext(), android.R.color.darker_gray)
            )
            binding.qb.carbDeficitHint.visibility = View.VISIBLE
        } else {
            binding.qb.carbDeficitHint.visibility = View.GONE
        }
    }

    private fun updateIobPreview() {
        val b = _binding ?: return
        val label = (if (model.insulinValue > 0.0) rh.gs(R.string.quick_bolus_new_iob_label) else rh.gs(R.string.quick_bolus_iob_label)) + " "
        b.qb.iobPreview.text = buildIobSpannable(label, model.projectedIob)
    }

    private fun buildIobSpannable(label: String, iob: Double): CharSequence {
        val displayIob = if (Math.abs(iob) < 0.05) 0.0 else iob
        val numStr = "%.1f".format(displayIob)
        val gray = ContextCompat.getColor(requireContext(), android.R.color.darker_gray)
        val ssb = SpannableStringBuilder()
        ssb.append(label, ForegroundColorSpan(gray), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ssb.append(numStr, StyleSpan(Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ssb.append(" U", ForegroundColorSpan(gray), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (model.hasNegativeBasalIob) {
            val redColor = android.graphics.Color.parseColor("#CC6666")
            val suffix = "  ${"%.1f".format(iob - model.currentBasalIob)} real"
            val start = ssb.length
            ssb.append(suffix)
            ssb.setSpan(StyleSpan(Typeface.BOLD), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            ssb.setSpan(ForegroundColorSpan(redColor), start, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return ssb
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler?.looper?.quitSafely()
        disposable.clear()
        _binding = null
    }

    override fun submit(): Boolean {
        if (_binding == null) return false
        val pumpDescription = activePlugin.activePump.pumpDescription
        val insulin = if (quickMode) model.insulinValue else SafeParse.stringToDouble(binding.insulin.text)
        val carbs = if (quickMode) model.carbsValue.toInt() else SafeParse.stringToInt(binding.carbs.text)
        val recordOnlyChecked = binding.recordOnly.isChecked
        val actions: LinkedList<String?> = LinkedList()
        val insulinAfterConstraints = constraintChecker.applyBolusConstraints(ConstraintObject(insulin, aapsLogger)).value()
        val carbsAfterConstraints = constraintChecker.applyCarbsConstraints(ConstraintObject(carbs, aapsLogger)).value()

        if (insulinAfterConstraints > 0) {
            actions.add(
                rh.gs(app.aaps.core.ui.R.string.bolus) + ": " + decimalFormatter.toPumpSupportedBolus(insulinAfterConstraints, activePlugin.activePump.pumpDescription.bolusStep)
                    .formatColor(
                        context, rh,
                        app.aaps.core.ui.R.attr.bolusColor
                    )
            )
            if (recordOnlyChecked)
                actions.add(rh.gs(app.aaps.core.ui.R.string.bolus_recorded_only).formatColor(context, rh, app.aaps.core.ui.R.attr.warningColor))
            if (abs(insulinAfterConstraints - insulin) > pumpDescription.pumpType.determineCorrectBolusStepSize(insulinAfterConstraints))
                actions.add(
                    rh.gs(app.aaps.core.ui.R.string.bolus_constraint_applied_warn, insulin, insulinAfterConstraints).formatColor(context, rh, app.aaps.core.ui.R.attr.warningColor)
                )
        }
        if (carbsAfterConstraints > 0) {
            actions.add(
                rh.gs(app.aaps.core.ui.R.string.carbs) + ": " + rh.gs(app.aaps.core.objects.R.string.format_carbs, carbsAfterConstraints).formatColor(
                    context, rh, app.aaps.core.ui.R.attr.carbsColor
                )
            )
            if (carbsAfterConstraints != carbs)
                actions.add(rh.gs(R.string.carbs_constraint_applied).formatColor(context, rh, app.aaps.core.ui.R.attr.warningColor))
        }
        if (insulinAfterConstraints > 0 || carbsAfterConstraints > 0) {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, rh.gs(app.aaps.core.ui.R.string.overview_treatment_label), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), {
                    dismiss()
                    val action = when {
                        insulinAfterConstraints.equals(0.0) -> Action.CARBS
                        carbsAfterConstraints == 0          -> Action.BOLUS
                        else                                -> Action.TREATMENT
                    }
                    val detailedBolusInfo = DetailedBolusInfo()
                    if (insulinAfterConstraints == 0.0) detailedBolusInfo.eventType = TE.Type.CARBS_CORRECTION
                    if (carbsAfterConstraints == 0) detailedBolusInfo.eventType = TE.Type.CORRECTION_BOLUS
                    detailedBolusInfo.insulin = insulinAfterConstraints
                    detailedBolusInfo.carbs = carbsAfterConstraints.toDouble()
                    detailedBolusInfo.context = context
                    if (recordOnlyChecked) {
                        if (detailedBolusInfo.insulin > 0)
                            disposable += persistenceLayer.insertOrUpdateBolus(
                                bolus = detailedBolusInfo.createBolus(),
                                action = action,
                                source = Sources.TreatmentDialog,
                                note = if (insulinAfterConstraints != 0.0) rh.gs(app.aaps.core.ui.R.string.record) else ""
                            ).subscribe()
                        if (detailedBolusInfo.carbs > 0)
                            disposable += persistenceLayer.insertOrUpdateCarbs(
                                carbs = detailedBolusInfo.createCarbs(),
                                action = action,
                                source = Sources.TreatmentDialog,
                                note = if (carbsAfterConstraints != 0) rh.gs(app.aaps.core.ui.R.string.record) else ""
                            ).subscribe()
                    } else {
                        if (detailedBolusInfo.insulin > 0) {
                            uel.log(
                                action = action,
                                source = Sources.TreatmentDialog,
                                listValues = listOf(
                                    ValueWithUnit.Insulin(insulinAfterConstraints),
                                    ValueWithUnit.Gram(carbsAfterConstraints).takeIf { carbsAfterConstraints != 0 }
                                ).filterNotNull()
                            )
                            commandQueue.bolus(detailedBolusInfo, object : Callback() {
                                override fun run() {
                                    if (!result.success) {
                                        uiInteraction.runAlarm(result.comment, rh.gs(app.aaps.core.ui.R.string.treatmentdeliveryerror), app.aaps.core.ui.R.raw.boluserror)
                                    }
                                }
                            })
                        } else {
                            if (detailedBolusInfo.carbs > 0)
                                disposable += persistenceLayer.insertOrUpdateCarbs(
                                    detailedBolusInfo.createCarbs(),
                                    action = action,
                                    source = Sources.TreatmentDialog
                                ).subscribe()
                        }
                    }
                })
            }
        } else
            activity?.let { activity ->
                OKDialog.show(activity, rh.gs(app.aaps.core.ui.R.string.overview_treatment_label), rh.gs(app.aaps.core.ui.R.string.no_action_selected))
            }
        return false
    }

    override fun onResume() {
        super.onResume()
        if (!queryingProtection) {
            queryingProtection = true
            activity?.let { activity ->
                val cancelFail = {
                    queryingProtection = false
                    aapsLogger.debug(LTag.APS, "Dialog canceled on resume protection: ${this.javaClass.simpleName}")
                    ToastUtils.warnToast(ctx, R.string.dialog_canceled)
                    dismiss()
                }
                protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, { queryingProtection = false }, cancelFail, cancelFail)
            }
        }
    }
}