package ir.tasavor.app

import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity

/**
 * فلیور direct: نسخه‌ای که مستقیم از خود سایت دانلود می‌شود (نه از بازار یا
 * مایکت). هیچ پل جاوااسکریپتی (AndroidBazaar/AndroidMyket) به صفحه اضافه
 * نمی‌شود، بنابراین وب‌سایت این حالت را عیناً مثل یک بازدیدکننده‌ی معمولی از
 * طریق مرورگر در نظر می‌گیرد و فرم شارژ مستقیم زرین‌پال خودِ سایت را نشان
 * می‌دهد (afzoune-ye تصور خودش این تشخیص را در frontend.js انجام می‌دهد).
 */
class BazaarBridge(
    private val activity: AppCompatActivity,
    private val webView: WebView,
    private val rsaKey: String
) {
    fun connect() { /* هیچ اتصالی لازم نیست */ }
    fun disconnect() { /* هیچ اتصالی لازم نیست */ }
}
