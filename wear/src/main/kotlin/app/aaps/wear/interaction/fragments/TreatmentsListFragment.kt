package app.aaps.wear.interaction.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.wear.widget.WearableLinearLayoutManager
import androidx.wear.widget.WearableRecyclerView
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.wear.R
import app.aaps.wear.data.ComplicationDataRepository
import app.aaps.wear.data.TreatmentItem
import app.aaps.wear.interaction.adapters.TreatmentsAdapter
import app.aaps.wear.interaction.utils.DisplayFormat
import app.aaps.wear.interaction.utils.MenuListActivity
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

class TreatmentsListFragment : DaggerFragment() {

    @Inject lateinit var complicationDataRepository: ComplicationDataRepository
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var displayFormat: DisplayFormat

    private lateinit var recyclerView: WearableRecyclerView
    private var disposable = CompositeDisposable()
    private val treatments = mutableListOf<TreatmentItem>()
    private var scope: CoroutineScope? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_treatments_list, container, false)
        recyclerView = view.findViewById(R.id.treatments_recycler_view)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        setupRecyclerView()
        return view
    }

    private fun setupRecyclerView() {
        val layoutManager = WearableLinearLayoutManager(requireContext())
        val isScreenRound = resources.configuration.isScreenRound

        if (isScreenRound) {
            layoutManager.layoutCallback = MenuListActivity.CustomScrollingLayoutCallback()
            recyclerView.isEdgeItemsCenteringEnabled = true
        } else {
            recyclerView.setPadding(0, 50, 0, 0)
        }

        recyclerView.setHasFixedSize(true)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = TreatmentsAdapter(treatments, displayFormat)
    }

    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventData.TreatmentData::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe {
                loadData()
            }
        loadData()
    }

    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    private fun loadData() {
        scope?.launch {
            val data = complicationDataRepository.complicationData.first()
            updateData(data.treatmentData)
        }
    }

    private fun updateData(treatmentData: EventData.TreatmentData) {
        treatments.clear()

        val sortedTreatments = treatmentData.boluses.sortedByDescending { it.date }

        // Group treatments within 30s of each other
        val timeWindow = 30000L
        val groups = mutableListOf<MutableList<EventData.TreatmentData.Treatment>>()

        sortedTreatments.forEach { treatment ->
            val existingGroup = groups.find { group ->
                group.any { Math.abs(it.date - treatment.date) <= timeWindow }
            }
            if (existingGroup != null) {
                existingGroup.add(treatment)
            } else {
                groups.add(mutableListOf(treatment))
            }
        }

        // A bolus-only and a carbs-only entry within the window are shown as one combined row
        groups.forEach { group ->
            val pair = group.takeIf { it.size == 2 }
            if (pair != null && pair.any { it.isInsulinOnly() } && pair.any { it.isCarbsOnly() }) {
                val (first, second) = pair
                treatments.add(
                    TreatmentItem(
                        timestamp = minOf(first.date, second.date),
                        bolus = first.bolus + second.bolus,
                        carbs = first.carbs + second.carbs,
                        isSMB = first.isSMB || second.isSMB,
                        isValid = first.isValid && second.isValid
                    )
                )
            } else {
                group.mapTo(treatments) { TreatmentItem(it.date, it.bolus, it.carbs, it.isSMB, it.isValid) }
            }
        }

        treatments.sortByDescending { it.timestamp }
        recyclerView.adapter?.notifyDataSetChanged()
    }

    private fun EventData.TreatmentData.Treatment.isInsulinOnly() = bolus > 0 && carbs <= 0
    private fun EventData.TreatmentData.Treatment.isCarbsOnly() = carbs > 0 && bolus <= 0

    override fun onDestroyView() {
        super.onDestroyView()
        scope?.cancel()
        scope = null
        disposable.clear()
    }
}
