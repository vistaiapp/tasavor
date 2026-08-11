# اپلیکیشن تصور (WebView + پرداخت درون‌برنامه‌ای)

اسکلت آماده‌ی یک اپ اندرویدی WebView برای سایت تصور، با پل جاوااسکریپت به پرداخت درون‌برنامه‌ای کافه‌بازار (Poolakey).

## ساختار پروژه

```
tasavor-app/
├── app/
│   ├── build.gradle.kts          # وابستگی‌ها (از جمله Poolakey)
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/ir/tasavor/app/
│       │   ├── MainActivity.kt   # WebView + مدیریت آفلاین/آنلاین
│       │   └── BillingBridge.kt  # پل بین جاوااسکریپت سایت و Poolakey
│       └── res/                  # layout، رنگ‌ها، آیکون
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## قدم ۱: باز کردن پروژه
۱. [Android Studio](https://developer.android.com/studio) (آخرین نسخه) رو نصب کن.
۲. پوشه‌ی `tasavor-app` رو با گزینه‌ی **Open** در Android Studio باز کن.
۳. وقتی Android Studio پیام "Gradle wrapper missing" داد، گزینه‌ی **OK / Fix** رو بزن تا خودش wrapper رو بسازه (چون در این پکیج به‌خاطر عدم اتصال شبکه فایل جار wrapper ساخته نشده).
۴. صبر کن Gradle Sync تموم بشه.

## قدم ۲: تنظیم آدرس سایت
فایل `app/src/main/res/values/strings.xml` رو باز کن و مقدار `site_url` رو به آدرس واقعی سایتت تغییر بده (اگه tasavor.ir هست، نیازی به تغییر نیست).

## قدم ۳: گرفتن کلید RSA کافه‌بازار
۱. وارد [پنل توسعه‌دهندگان کافه‌بازار](https://pishkhan.cafebazaar.ir) شو.
۲. اپ رو ثبت کن (حتی به‌صورت پیش‌نویس/Draft).
۳. از بخش «پرداخت درون‌برنامه‌ای»، کلید عمومی RSA اپ رو کپی کن.
۴. اون رو در `strings.xml` جای `PUT_YOUR_BAZAAR_RSA_KEY_HERE` بذار.
۵. محصولات (SKU) رو در همون پنل تعریف کن — این SKU ID ها همونایی هستن که در سایتت هنگام صدا زدن `AndroidBilling.purchase('SKU_ID')` استفاده می‌کنی.

## قدم ۴: وصل کردن دکمه‌ی خرید سایت به اپ
توی جاوااسکریپت سایت وردپرسی‌ات (تِم tasavor)، هرجا دکمه‌ی خرید/شارژ اعتبار هست:

```javascript
if (window.AndroidBilling) {
  // فقط وقتی داخل اپ اندرویدی هستیم این آبجکت وجود داره
  AndroidBilling.purchase('credit_100_pack'); // همون Product ID پنل بازار
} else {
  // حالت مرورگر معمولی: درگاه پرداخت وب رو نشون بده
}

// نتیجه‌ی خرید اینجا برمی‌گرده:
window.onTasavorPurchaseResult = function(status, sku, purchaseToken) {
  if (status === 'success') {
    // purchaseToken رو به سرور وردپرست بفرست تا با API کافه‌بازار وریفای بشه
    // و اعتبار/اشتراک کاربر فعال بشه
  } else if (status === 'canceled') {
    // کاربر انصراف داد
  } else {
    // خطا
  }
};
```

⚠️ **مهم:** حتماً سمت سرور (وردپرس) توکن خرید رو با [REST API کافه‌بازار](http://developers.cafebazaar.ir/en/docs/developer-api-v2-introduction/) وریفای کن، وگرنه هرکسی می‌تونه بدون پرداخت واقعی، status موفق جعل کنه.

## قدم ۵: افزودن مایکت (اختیاری ولی توصیه‌شده)
مایکت SDK جدا داره و روی Maven عمومی نیست:
۱. از [پنل توسعه‌دهندگان مایکت](https://devecosystem.myket.ir) فایل IAP SDK رو دانلود کن.
۲. فایل `.aar` رو در `app/libs/` بذار.
۳. در `app/build.gradle.kts` خط مربوط به Myket رو از حالت کامنت خارج کن.
۴. یک کلاس مشابه `BillingBridge.kt` برای Myket بساز و بر اساس اینکه اپ از کدوم مارکت نصب شده (با `getInstallerPackageName`) مسیر پرداخت مناسب رو انتخاب کن.

## قدم ۶: آیکون اپ
لوگوی فعلی (`assets/images/logo.png` از تم وردپرست) به‌صورت خام در `res/drawable/ic_logo_foreground.png` و `res/mipmap-hdpi/` گذاشته شده. برای نتیجه‌ی حرفه‌ای:
- راست‌کلیک روی `app` → **New → Image Asset** → لوگو رو انتخاب کن تا Android Studio همه‌ی سایزها رو خودش بسازه.

## قدم ۷: ساخت نسخه‌ی امضاشده (Release)
۱. `Build → Generate Signed Bundle / APK`
۲. یک Keystore جدید بساز (فایل `.jks` و پسوردها رو جای امن نگه دار — گم بشه دیگه نمی‌تونی اپ رو آپدیت کنی).
۳. فرمت **AAB** برای گیت‌هاب و همچنین بازار/مایکت (که هردو AAB و APK قبول می‌کنن) بساز.

## قدم ۸: انتشار در گیت‌هاب
```bash
cd tasavor-app
git init
git add .
git commit -m "Initial commit: Tasavor WebView app"
git branch -M main
git remote add origin https://github.com/USERNAME/tasavor-app.git
git push -u origin main
```
فایل `.gitignore` طوری تنظیم شده که کیستور و پوشه‌ی build آپلود نشن.

## قدم ۹: انتشار در بازار و مایکت
- در پنل هرکدوم، اپ جدید بساز، فایل AAB رو آپلود کن، اسکرین‌شات و توضیحات و دسته‌بندی (تولید محتوا/هوش‌مصنوعی) رو پر کن.
- محصولات درون‌برنامه‌ای (In-App Products) رو با همون SKU ID هایی که در کد استفاده کردی تعریف کن.
- منتظر تایید بمون (معمولاً چند ساعت تا چند روز).

## نکات امنیتی
- کلید RSA بازار رو مستقیم توی `strings.xml` نذار برای پروژه‌ی واقعی — بهتره با `gradle.properties` محلی (که در `.gitignore` هست) تزریقش کنی تا در گیت‌هاب عمومی لو نره.
- همیشه توکن خرید رو سمت سرور وریفای کن، نه فقط سمت کلاینت.
