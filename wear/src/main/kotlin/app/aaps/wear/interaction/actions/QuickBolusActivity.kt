package app.aaps.wear.interaction.actions

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.wear.R
import app.aaps.wear.interaction.fragments.QuickBolusFragment
import app.aaps.wear.interaction.fragments.TreatmentsListFragment
import dagger.android.support.DaggerAppCompatActivity
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject

class QuickBolusActivity : DaggerAppCompatActivity() {

    @Inject lateinit var rxBus: RxBus
    private val disposable = CompositeDisposable()
    private var bolusInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quick_bolus_pager)

        val viewPager = findViewById<ViewPager2>(R.id.view_pager)
        viewPager.adapter = ScreenSlidePagerAdapter(this)

        disposable.add(rxBus
            .toObservable(EventData.BolusProgress::class.java)
            .subscribe {
                bolusInProgress = it.percent < 100
            })
    }

    override fun onPause() {
        super.onPause()
        if (!bolusInProgress) {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable.clear()
    }

    private inner class ScreenSlidePagerAdapter(activity: DaggerAppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> QuickBolusFragment()
                else -> TreatmentsListFragment()
            }
        }
    }
}
