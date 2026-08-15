package ir.tasavor.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
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
    private lateinit var bazaarBridge: BazaarBridge

    private val siteUrl by lazy { getString(R.string.site_url) }

    // ---- State for the WebView file chooser (image upload / camera capture) ----
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var pendingCameraUri: Uri? = null

    // ---- State for a download waiting on the WRITE_EXTERNAL_STORAGE permission
    // (only relevant on Android 9 (API 28) and below; 10+ doesn't need it for
    // DownloadManager's own public Downloads destination). ----
    private data class PendingDownload(
        val url: String,
        val userAgent: String?,
        val contentDisposition: String?,
        val mimeType: String?
    )
    private var pendingDownload: PendingDownload? = null

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val dl = pendingDownload
        pendingDownload = null
        if (granted && dl != null) {
            enqueueDownload(dl.url, dl.userAgent, dl.contentDisposition, dl.mimeType)
        } else if (!granted) {
            Toast.makeText(this, "برای دانلود، دسترسی ذخیره‌سازی لازم است.", Toast.LENGTH_LONG).show()
        }
    }

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

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        progressBar = findViewById(R.id.progressBar)
        offlineLayout = findViewById(R.id.offlineLayout)
        val retryButton = findViewById<Button>(R.id.retryButton)

        // ---- Billing bridge: implementation differs per build flavor ----
        // (Poolakey for "bazaar", myket-billing-client for "myket", no-op for
        // "direct" — see app/src/<flavor>/.../BazaarBridge.kt). BuildConfig
        // fields are set per flavor in app/build.gradle.kts.
        bazaarBridge = BazaarBridge(this, webView, BuildConfig.MARKET_RSA_KEY)
        if (BuildConfig.MARKET_ID != "direct") {
            bazaarBridge.connect()
        }

        // ---- WebView setup ----
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        webView.settings.mediaPlaybackRequiresUserGesture = false

        // لازم است تا onCreateWindow پایین‌تر فراخوانی شود؛ بدون این، کلیک روی
        // لینک‌های دانلود با target="_blank" (دکمه‌ی دانلود تصویر/ویدیوی گالری)
        // به‌جای این‌که به ما فرصت بدهد لینک را بگیریم، بی‌صدا همین صفحه‌ی سایت را
        // با آدرس خامِ فایل جایگزین می‌کند (نه دانلود واقعی، نه خطا — فقط هیچ‌اتفاقی).
        webView.settings.setSupportMultipleWindows(true)
        webView.settings.javaScriptCanOpenWindowsAutomatically = true

        // دانلود واقعی فایل (عکس/ویدیوی خروجی از qwen-image / seedance) روی حافظه‌ی
        // گوشی. این برای هر لینک/ناوبری‌ای که WebView خودش نتواند نمایش بدهد (یا
        // ویژگی HTML «download» رویش باشد) صدا زده می‌شود.
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            requestDownload(url, userAgent, contentDisposition, mimeType)
        }

        // Expose the billing bridge to JavaScript under the name the site's
        // frontend.js (ai-coin-wallet plugin) expects: AndroidBazaar or
        // AndroidMyket. The "direct" flavor exposes neither, so the site
        // falls back to its own ZarinPal checkout automatically.
        when (BuildConfig.MARKET_ID) {
            "bazaar" -> webView.addJavascriptInterface(bazaarBridge, "AndroidBazaar")
            "myket" -> webView.addJavascriptInterface(bazaarBridge, "AndroidMyket")
        }

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

        // Handles <input type="file"> clicks (image upload / drag-drop dialog),
        // offering both "pick from gallery" and "take a photo" (system Camera app).
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

            // لینک‌های دکمه‌ی دانلود در گالری‌ها با target="_blank" باز می‌شوند
            // (و vistai از window.open هم استفاده می‌کند). چون این اپ پنجره‌ی
            // واقعی جدید نمی‌سازد، به‌جایش آدرس را با یک WebView موقت/نامرئی
            // می‌گیریم و مستقیم به دانلودر می‌فرستیم؛ خودِ پنجره هرگز نمایش
            // داده نمی‌شود.
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                val transportWebView = WebView(this@MainActivity)
                transportWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        v: WebView?,
                        request: android.webkit.WebResourceRequest?
                    ): Boolean {
                        val targetUrl = request?.url?.toString()
                        if (targetUrl != null) {
                            requestDownload(targetUrl, null, null, null)
                        }
                        return true
                    }
                }
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = transportWebView
                resultMsg?.sendToTarget()
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                // The plugin currently has no getUserMedia() (live camera) feature,
                // and the app no longer declares the CAMERA permission (it isn't
                // needed for the "take a photo" option in the file chooser below,
                // which delegates to the phone's own Camera app instead).
                // Deny any such request here to avoid a crash from requesting a
                // runtime permission the manifest doesn't declare.
                runOnUiThread { request.deny() }
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

    /**
     * درخواست دانلودِ یک فایل (عکس/ویدیوی خروجی). قبل از استفاده‌ی واقعی از
     * DownloadManager، در اندروید ۹ و پایین‌تر (API ≤ 28) مجوز WRITE_EXTERNAL_STORAGE
     * را چک/درخواست می‌کند؛ در اندروید ۱۰ به بعد این مجوز برای پوشه‌ی عمومی Download
     * لازم نیست.
     */
    private fun requestDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) { // API 28 و پایین‌تر
            val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                pendingDownload = PendingDownload(url, userAgent, contentDisposition, mimeType)
                storagePermissionLauncher.launch(permission)
                return
            }
        }
        enqueueDownload(url, userAgent, contentDisposition, mimeType)
    }

    private fun enqueueDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        try {
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                // مهم: کوکیِ سشنِ WebView را همراه درخواست می‌فرستیم. اگر فایل‌های
                // آینده نیاز به لاگین داشته باشند (برخلاف wp-content/uploads فعلی
                // که عمومی است)، این باعث می‌شود DownloadManager هم لاگین را ببیند.
                addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url))
                addRequestHeader("User-Agent", userAgent ?: webView.settings.userAgentString)
                setMimeType(mimeType)
                setDescription(fileName)
                setTitle(fileName)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            Toast.makeText(this, "دانلود شروع شد…", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "دانلود ناموفق بود. دوباره تلاش کنید.", Toast.LENGTH_LONG).show()
        }
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
        if (BuildConfig.MARKET_ID != "direct") {
            bazaarBridge.disconnect()
        }
        super.onDestroy()
    }
}
