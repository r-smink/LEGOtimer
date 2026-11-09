package nl.nextbuzz.bricktimer.monetize

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import nl.nextbuzz.bricktimer.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Interstitial shown when a timer starts (free version only). Asks for EU consent via Google UMP first;
 * without consent or network nothing is shown and the timer simply runs.
 */
object Ads {
    private val started = AtomicBoolean(false)
    private var ad: InterstitialAd? = null
    private var loading = false

    private fun consent(context: Context): ConsentInformation = UserMessagingPlatform.getConsentInformation(context)

    /** Gathers consent (showing Google's form when required), then starts the SDK and preloads an ad. Call from the activity's onCreate. */
    fun init(activity: Activity) {
        val info = consent(activity)
        info.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { start(activity) }
        }, { start(activity) })
        // Consent from a previous launch is valid right away.
        if (info.canRequestAds()) start(activity)
    }

    private fun start(activity: Activity) {
        if (!consent(activity).canRequestAds() || !started.compareAndSet(false, true)) return
        val app = activity.applicationContext
        MobileAds.initialize(app) { preload(app) }
    }

    private fun preload(context: Context) {
        if (ad != null || loading || !started.get()) return
        loading = true
        InterstitialAd.load(context, BuildConfig.AD_UNIT_TIMER, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(loaded: InterstitialAd) {
                ad = loaded
                loading = false
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                loading = false
            }
        })
    }

    /** Shows the preloaded interstitial if there is one, and loads the next. */
    fun show(activity: Activity) {
        val current = ad ?: return preload(activity.applicationContext)
        ad = null
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = preload(activity.applicationContext)
            override fun onAdFailedToShowFullScreenContent(error: AdError) = preload(activity.applicationContext)
        }
        current.show(activity)
    }

    /** True when the user must be able to change their ad consent from the app (EU). */
    fun privacyOptionsRequired(context: Context) =
        consent(context).privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun showPrivacyOptions(activity: Activity) = UserMessagingPlatform.showPrivacyOptionsForm(activity) {}
}
