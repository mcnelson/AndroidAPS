package app.aaps.plugins.aps.di

import app.aaps.plugins.aps.loop.CarbSuggestionReceiver
import app.aaps.plugins.aps.loop.LoopFragment
import app.aaps.plugins.aps.loop.LoopIntervalPreference
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
@Suppress("unused")
abstract class LoopModule {
    @ContributesAndroidInjector abstract fun contributesLoopFragment(): LoopFragment
    @ContributesAndroidInjector abstract fun contributesCarbSuggestionReceiver(): CarbSuggestionReceiver
    @ContributesAndroidInjector abstract fun contributesLoopIntervalPreference(): LoopIntervalPreference
}