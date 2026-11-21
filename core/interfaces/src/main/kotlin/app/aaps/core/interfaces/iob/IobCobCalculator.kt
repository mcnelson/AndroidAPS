package app.aaps.core.interfaces.iob

import app.aaps.core.data.aps.BasalData
import app.aaps.core.data.iob.CobInfo
import app.aaps.core.interfaces.aps.AutosensData
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.aps.MealData
import app.aaps.core.interfaces.profile.Profile

interface IobCobCalculator {

    var ads: AutosensDataStore

    fun getMealDataWithWaitingForCalculationFinish(): MealData
    fun getLastAutosensDataWithWaitForCalculationFinish(reason: String): AutosensData?

    fun calculateFromTreatmentsAndTemps(toTime: Long, profile: Profile): IobTotal

    fun calculateDetectionStart(from: Long, limitDataToOldestAvailable: Boolean): Long

    fun getBasalData(profile: Profile, fromTime: Long): BasalData

    fun calculateIobArrayInDia(profile: Profile): Array<IobTotal>
    fun calculateIobArrayForSMB(lastAutosensResult: AutosensResult, exerciseMode: Boolean, halfBasalExerciseTarget: Int, isTempTarget: Boolean): Array<IobTotal>
    fun iobArrayToString(array: Array<IobTotal>): String

    fun clearCache()

    /**
     *  Calculate CobInfo to now()
     *
     *  @param reason caller identification
     *  @return CobInfo
     */
    fun getCobInfo(reason: String): CobInfo

    /**
     * Calculate IobTotal from boluses and extended boluses to now().
     * NOTE: Only isValid == true boluses are included
     * NOTE: if faking by TBR by extended boluses is enabled, extended boluses are not included
     *  and are calculated towards temporary basals
     *
     * @return calculated iob
     */
    fun calculateIobFromBolus(): IobTotal

    /**
     *  Calculate IOB of base basal insulin (usually not accounted towards IOB)
     *
     *  @param  toTime
     *  @return IobTotal
     */
    fun calculateAbsoluteIobFromBaseBasals(toTime: Long): IobTotal

    /**
     *  Calculate IOB from Temporary basals and Extended boluses (if emulation is enabled) to the the time specified
     *
     *  @param  toTime    time to calculate to
     *  @return IobTotal
     */
    fun calculateIobToTimeFromTempBasalsIncludingConvertedExtended(toTime: Long): IobTotal

    /**
     *  Calculate IOB from Temporary basals and Extended boluses (if emulation is enabled) to now
     *
     *  @return IobTotal
     */
    fun calculateIobFromTempBasalsIncludingConvertedExtended(): IobTotal

    /**
     * Find the start of the current "bolus session": the most recent point in time, scanning
     * backward from now, at which total IOB was at or below [threshold]. This marks the boundary
     * where IOB last rose from (near) zero, e.g. the start of a meal or correction episode, as
     * opposed to the instantaneous IOB snapshot which stacks every subsequent correction.
     *
     * Caller must confirm current IOB is above [threshold] before calling. This always performs
     * the backward search; it does not short-circuit "no active session" itself, since that check
     * belongs with whatever current-IOB value the caller already has on hand.
     *
     * @param profile   current profile (for DIA, used to bound how far back to search)
     * @param threshold IOB (U) at or below which is considered "no active session"
     * @return timestamp of session start
     */
    fun findBolusSessionStart(profile: Profile, threshold: Double): Long
}