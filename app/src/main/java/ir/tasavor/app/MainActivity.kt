package ir.tasavor.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var offlineLayout: View
    private lateinit var billingBridge: BillingBridge

    private val siteUrl by lazy { getString(R.string.site_url) }

    // ---- State for the WebView file chooser (image upload / camera capture) ----
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var pendingCameraUri: Uri? = null
    private var pendingPermissionRequest: PermissionRequest? = null

    // Launches the system chooser (gallery picker + "take photo" option) and
    // delivers the picked/captured image(s) back to the WebView's JS file input.
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = fileChooserCallback
        fileChooserCallback = null

        if (callback == null) return@registerForActivityResult

        if (result.resultCode != Activity.RESULT_OK) {
            callback.onReceiveValue(null)
            return@registerForActivityResult
        }

        val data = result.data
        val uris: Array<Uri>? = when {
            // Multiple images picked from gallery
            data?.clipData != null -> {
                val clipData = data.clipData!!
                Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
            }
            // Single image picked from gallery
            data?.data != null -> arrayOf(data.data!!)
            // Photo taken with the camera
            pendingCameraUri != null -> arrayOf(pendingCameraUri!!)
            else -> null
        }
        callback.onReceiveValue(uris)
    }

    // Runtime CAMERA permission, requested only when the web page asks to use it
    // (e.g. the plugin's "take a photo" reference-image feature).
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val request = pendingPermissionRequest
        pendingPermissionRequest = null
        if (request != null) {
            if (granted) request.grant(request.resources) else request.deny()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        progressBar = findViewById(R.id.progressBar)
        offlineLayout = findViewById(R.id.offlineLayout)
        val retryButton = findViewById<Button>(R.id.retryButton)

        // ---- Billing bridge (Poolakey / Cafe Bazaar) ----
        billingBridge = BillingBridge(this, webView)
        billingBridge.connect()

        // ---- WebView setup ----
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        webView.settings.mediaPlaybackRequiresUserGesture = false

        // Expose the billing bridge to JavaScript as `AndroidBilling`
        // From your site's JS you can call:
        //   AndroidBilling.purchase('product_sku_id')
        webView.addJavascriptInterface(billingBridge, "AndroidBilling")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
            }

            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                showOffline()
            }
        }

        // Handles <input type="file"> clicks (image upload / drag-drop dialog)
        // and getUserMedia() camera permission requests coming from the site's JS,
        // e.g. the Qwen image generator plugin's reference-image uploader.
        webView.webChromeClient = object : WebChromeClient() {

            override fun onShowFileChooser(
                view: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback

                val allowMultiple =
                    fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE

                // "Pick from gallery" intent, restricted to images (matches the
                // plugin's accept="image/png,image/jpeg,image/webp").
                val pickIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple)
                    addCategory(Intent.CATEGORY_OPENABLE)
                }

                // "Take a photo" intent, offered alongside the gallery in the same chooser.
                var cameraIntent: Intent? = null
                try {
                    val photoFile = File.createTempFile(
                        "camera_${System.currentTimeMillis()}_",
                        ".jpg",
                        cacheDir
                    )
                    pendingCameraUri = FileProvider.getUriForFile(
                        this@MainActivity,
                        "$packageName.fileprovider",
                        photoFile
                    )
                    cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                        putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri)
                        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    }
                } catch (e: Exception) {
                    pendingCameraUri = null
                }

                val chooserIntent = Intent(Intent.ACTION_CHOOSER).apply {
                    putExtra(Intent.EXTRA_INTENT, pickIntent)
                    putExtra(Intent.EXTRA_TITLE, "انتخاب یا گرفتن تصویر")
                    if (cameraIntent != null) {
                        putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
                    }
                }

                return try {
                    fileChooserLauncher.launch(chooserIntent)
                    true
                } catch (e: Exception) {
                    fileChooserCallback = null
                    false
                }
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    val needsCamera = request.resources.contains(
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE
                    )
                    if (!needsCamera) {
                        request.grant(request.resources)
                        return@runOnUiThread
                    }
                    val hasCameraPermission = ContextCompat.checkSelfPermission(
                        this@MainActivity, Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED

                    if (hasCameraPermission) {
                        request.grant(request.resources)
                    } else {
                        pendingPermissionRequest = request
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }
            }
        }

        swipeRefresh.setOnRefreshListener { loadSite() }
        retryButton.setOnClickListener { loadSite() }

        loadSite()
    }

    private fun loadSite() {
        if (!isOnline()) {
            showOffline()
            return
        }
        offlineLayout.visibility = View.GONE
        webView.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE
        webView.loadUrl(siteUrl)
    }

    private fun showOffline() {
        progressBar.visibility = View.GONE
        swipeRefresh.isRefreshing = false
        webView.visibility = View.GONE
        offlineLayout.visibility = View.VISIBLE
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        billingBridge.disconnect()
        super.onDestroy()
    }
}
