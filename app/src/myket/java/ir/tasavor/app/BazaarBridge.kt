package ir.tasavor.app

import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import ir.myket.billingclient.IabHelper
import ir.myket.billingclient.util.IabResult
import ir.myket.billingclient.util.Inventory
import ir.myket.billingclient.util.Purchase
import org.json.JSONObject

/**
 * پل پرداخت درون‌برنامه‌ای مایکت (فلیور myket)، با کتابخانه‌ی رسمی
 * myket-billing-client: https://github.com/myketstore/myket-billing-client
 * طبق مستندات: https://myket.ir/kb/pages/in-app-purchase-implementation-steps/
 *
 * امضای عمومی این کلاس دقیقاً مثل نسخه‌ی بازار (BazaarBridge در فلیور bazaar)
 * است تا MainActivity.kt بدون هیچ تغییری روی هر دو فلیور کامپایل شود.
 */
class BazaarBridge(
    private val activity: AppCompatActivity,
    private val webView: WebView,
    private val rsaKey: String
) {

    private var helper: IabHelper? = null

    fun connect() {
        val iabHelper = IabHelper(activity, rsaKey)
        helper = iabHelper

        iabHelper.startSetup { setupResult ->
            if (!setupResult.isSuccess) {
                notifyWeb("connection_failed", mapOf("message" to (setupResult.message ?: "unknown error")))
                return@startSetup
            }
            recoverUnconsumedPurchases()
        }
    }

    private fun recoverUnconsumedPurchases() {
        val iabHelper = helper ?: return
        iabHelper.queryInventoryAsync { queryResult: IabResult, inventory: Inventory? ->
            if (queryResult.isFailure || inventory == null) return@queryInventoryAsync
            for (p in inventory.allPurchases) {
                notifyWeb("purchase_success", mapOf("sku" to p.sku, "purchaseToken" to p.token))
            }
        }
    }

    /** از جاوااسکریپت سایت صدا زده می‌شود: AndroidMyket.purchase('pkg_xxxx') */
    @JavascriptInterface
    fun purchase(sku: String) {
        activity.runOnUiThread {
            val iabHelper = helper
            if (iabHelper == null) {
                notifyWeb("purchase_failed", mapOf("message" to "not connected to myket"))
                return@runOnUiThread
            }
            iabHelper.launchPurchaseFlow(
                activity,
                sku,
                { purchaseResult: IabResult, purchase: Purchase? ->
                    if (purchaseResult.isFailure || purchase == null) {
                        val message = purchaseResult.message ?: "unknown error"
                        if (purchaseResult.response == IabHelper.IABHELPER_USER_CANCELLED) {
                            notifyWeb("purchase_canceled", mapOf("message" to "canceled by user"))
                        } else {
                            notifyWeb("purchase_failed", mapOf("message" to message))
                        }
                        return@launchPurchaseFlow
                    }
                    notifyWeb(
                        "purchase_success",
                        mapOf("sku" to purchase.sku, "purchaseToken" to purchase.token)
                    )
                },
                "" // developerPayload؛ صحت‌سنجی نهایی خرید سمت سرور بر اساس purchaseToken انجام می‌شود
            )
        }
    }

    /**
     * بعد از تایید سمت سرور، خرید را در مایکت هم «مصرف» می‌کند تا کاربر
     * بتواند دوباره همان بسته را بخرد.
     */
    @JavascriptInterface
    fun confirmConsume(purchaseToken: String) {
        activity.runOnUiThread {
            val iabHelper = helper ?: return@runOnUiThread
            iabHelper.queryInventoryAsync { queryResult: IabResult, inventory: Inventory? ->
                if (queryResult.isFailure || inventory == null) return@queryInventoryAsync
                val purchase = inventory.allPurchases.firstOrNull { it.token == purchaseToken }
                    ?: return@queryInventoryAsync
                iabHelper.consumeAsync(purchase) { _, _ -> /* موفق یا ناموفق */ }
            }
        }
    }

    fun disconnect() {
        helper?.dispose()
        helper = null
    }

    private fun notifyWeb(event: String, data: Map<String, String>) {
        activity.runOnUiThread {
            val json = JSONObject(data).toString()
            val js = "window.onMyketEvent && window.onMyketEvent('$event', $json);"
            webView.evaluateJavascript(js, null)
        }
    }
}
