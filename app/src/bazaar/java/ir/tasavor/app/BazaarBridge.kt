package ir.tasavor.app

import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import ir.cafebazaar.poolakey.Payment
import ir.cafebazaar.poolakey.config.PaymentConfiguration
import ir.cafebazaar.poolakey.config.SecurityCheck
import ir.cafebazaar.poolakey.request.PurchaseRequest
import org.json.JSONObject

/**
 * پل پرداخت درون‌برنامه‌ای کافه‌بازار (فلیور bazaar)، با کتابخانه‌ی رسمی
 * Poolakey: https://github.com/cafebazaar/Poolakey
 *
 * نتیجه‌ی هر رویداد خرید با فراخوانی window.onBazaarEvent(event, data) به
 * جاوااسکریپت سایت (افزونه‌ی تصور — کیف‌پول سکه‌ای) اطلاع داده می‌شود.
 */
class BazaarBridge(
    private val activity: AppCompatActivity,
    private val webView: WebView,
    private val rsaKey: String
) {

    private var payment: Payment? = null

    fun connect() {
        val securityCheck = SecurityCheck.Enable(rsaKey)
        val paymentConfig = PaymentConfiguration(localSecurityCheck = securityCheck)
        payment = Payment(activity, paymentConfig)

        payment?.connect {
            connectionSucceed {
                // اتصال به بازار موفق بود؛ خریدهای معلق (مصرف‌نشده) را چک می‌کنیم
                // تا اگر کاربر وسط فرآیند خرید اپ را بسته، دفعه‌ی بعد که باز
                // می‌کند شارژ کیف‌پول از دست نرود.
                recoverUnconsumedPurchases()
            }
            connectionFailed { throwable ->
                notifyWeb("connection_failed", mapOf("message" to (throwable.message ?: "unknown error")))
            }
            disconnected {
                // اتصال قطع شد
            }
        }
    }

    private fun recoverUnconsumedPurchases() {
        payment?.getPurchasedProducts {
            querySucceed { purchasedItems ->
                for (item in purchasedItems) {
                    notifyWeb(
                        "purchase_success",
                        mapOf("sku" to item.productId, "purchaseToken" to item.purchaseToken)
                    )
                }
            }
            queryFailed {
                // مشکلی نیست؛ دفعه‌ی بعد که اپ باز شود دوباره تلاش می‌شود
            }
        }
    }

    /** از جاوااسکریپت سایت صدا زده می‌شود: AndroidBazaar.purchase('pkg_xxxx') */
    @JavascriptInterface
    fun purchase(sku: String) {
        activity.runOnUiThread {
            payment?.purchaseProduct(activity.activityResultRegistry, PurchaseRequest(productId = sku)) {
                purchaseSucceed { purchaseInfo ->
                    notifyWeb(
                        "purchase_success",
                        mapOf("sku" to purchaseInfo.productId, "purchaseToken" to purchaseInfo.purchaseToken)
                    )
                }
                purchaseFailed { throwable ->
                    notifyWeb("purchase_failed", mapOf("message" to (throwable.message ?: "unknown error")))
                }
                purchaseCanceled {
                    notifyWeb("purchase_canceled", mapOf("message" to "canceled by user"))
                }
            }
        }
    }

    /**
     * بعد از این‌که سایت (سمت سرور) خرید را تایید و کیف‌پول را شارژ کرد، این
     * متد را صدا می‌زند تا خرید در بازار هم «مصرف» شود و کاربر بتواند دوباره
     * همان بسته را بخرد.
     */
    @JavascriptInterface
    fun confirmConsume(purchaseToken: String) {
        activity.runOnUiThread {
            payment?.consumeProduct(purchaseToken) {
                consumeSucceed { /* مصرف موفق */ }
                consumeFailed { /* دفعه‌ی بعد recoverUnconsumedPurchases دوباره پیدایش می‌کند */ }
            }
        }
    }

    fun disconnect() {
        // Poolakey 2.x خودکار با از بین رفتن Activity قطع می‌شود
    }

    private fun notifyWeb(event: String, data: Map<String, String>) {
        activity.runOnUiThread {
            val json = JSONObject(data).toString()
            val js = "window.onBazaarEvent && window.onBazaarEvent('$event', $json);"
            webView.evaluateJavascript(js, null)
        }
    }
}
