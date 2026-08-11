package ir.tasavor.app

import android.annotation.SuppressLint
import android.app.Activity
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.ComponentActivity
import ir.cafebazaar.poolakey.Connection
import ir.cafebazaar.poolakey.Payment
import ir.cafebazaar.poolakey.config.PaymentConfiguration
import ir.cafebazaar.poolakey.config.SecurityCheck
import ir.cafebazaar.poolakey.request.PurchaseRequest

/**
 * Bridges Cafe Bazaar's Poolakey billing SDK to the WebView's JavaScript context.
 *
 * From your site's JS, trigger a purchase like this:
 *   AndroidBilling.purchase('your_product_sku')
 *
 * The result is delivered back to the page by calling the JS function
 * `onTasavorPurchaseResult(status, sku, purchaseToken)` if it exists on the page,
 * so your site can verify the token server-side and unlock the user's credit/plan.
 *
 * NOTE: This wraps Cafe Bazaar only. If you also publish on Myket, add a similar
 * wrapper using Myket's IAP SDK and route `purchase()` based on which store the
 * APK was installed from (see MarketUtils.getInstallerPackageName in the README).
 */
class BillingBridge(
    private val activity: ComponentActivity,
    private val webView: WebView
) {

    private val payment: Payment by lazy {
        val securityCheck = SecurityCheck.Enable(
            rsaPublicKey = activity.getString(R.string.bazaar_rsa_key)
        )
        val config = PaymentConfiguration(localSecurityCheck = securityCheck)
        Payment(context = activity, config = config)
    }

    private var connection: Connection? = null
    private var requestCodeCounter = 1000

    fun connect() {
        connection = payment.connect {
            connectionSucceed {
                Log.d(TAG, "Connected to Bazaar billing service")
            }
            connectionFailed { throwable ->
                Log.e(TAG, "Failed to connect to Bazaar billing service", throwable)
            }
            disconnected {
                Log.d(TAG, "Disconnected from Bazaar billing service")
            }
        }
    }

    fun disconnect() {
        connection?.disconnect()
    }

    /** Called from JavaScript: AndroidBilling.purchase('sku_id') */
    @JavascriptInterface
    fun purchase(sku: String) {
        activity.runOnUiThread {
            val request = PurchaseRequest(
                productId = sku,
                requestCode = requestCodeCounter++,
                payload = ""
            )
            payment.purchaseProduct(
                registry = activity.activityResultRegistry,
                request = request
            ) {
                purchaseFlowBegan {
                    Log.d(TAG, "Purchase flow started for $sku")
                }
                failedToBeginFlow { throwable ->
                    Log.e(TAG, "Could not open Bazaar purchase screen", throwable)
                    notifyWeb("error", sku, null)
                }
                purchaseSucceed { purchaseEntity ->
                    Log.d(TAG, "Purchase succeeded: ${purchaseEntity.purchaseToken}")
                    notifyWeb("success", sku, purchaseEntity.purchaseToken)
                }
                purchaseCanceled {
                    Log.d(TAG, "Purchase canceled by user")
                    notifyWeb("canceled", sku, null)
                }
                purchaseFailed { throwable ->
                    Log.e(TAG, "Purchase failed", throwable)
                    notifyWeb("error", sku, null)
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun notifyWeb(status: String, sku: String, purchaseToken: String?) {
        activity.runOnUiThread {
            val tokenArg = if (purchaseToken != null) "'$purchaseToken'" else "null"
            val js = "if (window.onTasavorPurchaseResult) { " +
                "onTasavorPurchaseResult('$status', '$sku', $tokenArg); }"
            webView.evaluateJavascript(js, null)
        }
    }

    companion object {
        private const val TAG = "BillingBridge"
    }
}
