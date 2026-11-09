package nl.nextbuzz.bricktimer.monetize

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nl.nextbuzz.bricktimer.data.Repository

/** One-time "premium" purchase through Google Play that removes ads. The result is cached in AppData.premium. */
class Premium private constructor(context: Context) {
    private val repo = Repository.get(context)
    private var details: ProductDetails? = null

    private val _price = MutableStateFlow<String?>(null)
    /** Localized price from Play, or null while unknown (offline, or product not set up yet). */
    val price: StateFlow<String?> = _price.asStateFlow()

    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener { result, purchases -> if (result.responseCode == BillingClient.BillingResponseCode.OK) purchases?.let(::handle) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    /** Connects to Play, restores an earlier purchase and fetches the price. Safe to call repeatedly. */
    fun refresh() {
        if (client.isReady) return query()
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) query()
            }

            override fun onBillingServiceDisconnected() {}
        })
    }

    private fun query() {
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            val owned = purchases.any { PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }
            repo.update { it.copy(premium = owned) }
            handle(purchases)
        }
        val product = QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT_ID).setProductType(BillingClient.ProductType.INAPP).build()
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()) { _, result ->
            details = result.productDetailsList.firstOrNull()
            _price.value = details?.oneTimePurchaseOfferDetails?.formattedPrice
        }
    }

    /** Opens Play's purchase sheet. Returns false when the product isn't available (yet). */
    fun buy(activity: Activity): Boolean {
        val d = details ?: return false.also { refresh() }
        val params = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()
        client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params)).build())
        return true
    }

    private fun handle(purchases: List<Purchase>) {
        for (p in purchases) {
            if (PRODUCT_ID !in p.products || p.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            repo.update { it.copy(premium = true) }
            // Unacknowledged purchases are refunded by Play after three days.
            if (!p.isAcknowledged) client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) {}
        }
    }

    companion object {
        const val PRODUCT_ID = "premium"

        @Volatile private var instance: Premium? = null
        fun get(context: Context): Premium = instance ?: synchronized(this) { instance ?: Premium(context).also { instance = it } }
    }
}
