# اپلیکیشن تصور (WebView چندفلیوری + پرداخت درون‌برنامه‌ای بازار/مایکت)

اپ اندرویدی سایت تصور با سه فلیور توزیع جدا (دقیقاً مثل معماری ویست‌ای‌آی):

| فلیور    | برای کجا                          | پرداخت درون‌برنامه‌ای |
|----------|-----------------------------------|------------------------|
| `bazaar` | آپلود در کافه‌بازار                | Poolakey (بازار)      |
| `myket`  | آپلود در مایکت                     | myket-billing-client   |
| `direct` | دانلود مستقیم از خود سایت          | ندارد — زرین‌پال سایت  |

## ساختار پروژه

```
tasavor-app/
├── app/
│   ├── build.gradle.kts               # سه فلیور + وابستگی‌های هرکدام
│   └── src/
│       ├── main/java/ir/tasavor/app/
│       │   └── MainActivity.kt        # WebView + فایل‌چوزر + دوربین
│       ├── bazaar/java/ir/tasavor/app/BazaarBridge.kt   # پیاده‌سازی Poolakey
│       ├── myket/java/ir/tasavor/app/BazaarBridge.kt    # پیاده‌سازی myket-billing-client
│       └── direct/java/ir/tasavor/app/BazaarBridge.kt   # بدون پرداخت درون‌برنامه‌ای
├── .github/workflows/
│   ├── build-apk.yml                  # build دیباگ هر سه فلیور
│   └── build-release.yml              # build امضاشده (AAB+APK) هر سه فلیور
└── README.md
```

## قدم ۱: باز کردن پروژه
Android Studio → Open → پوشه‌ی `tasavor-app`. صبر کن Gradle Sync تموم بشه. چون سه فلیور داره، بالای پنجره (کنار دکمه‌ی Run) یه Build Variant می‌بینی: مثلاً `bazaarDebug`, `myketDebug`, `directDebug`.

## قدم ۲: تنظیم آدرس سایت
`app/src/main/res/values/strings.xml` → مقدار `site_url` (پیش‌فرض: `https://tasavor-ai.ir`).

## قدم ۳: تنظیم افزونه‌ی وردپرست (ai-coin-wallet)
توی پنل وردپرس، افزونه → کیف‌پول → تنظیمات:
1. **شناسه‌ی پکیج اپلیکیشن**: `ir.tasavor.app`
2. **توکن دسترسی کافه‌بازار**: از pishkhan.cafebazaar.ir → اپ شما → پرداخت درون‌برنامه‌ای
3. **توکن دسترسی مایکت (X-Access-Token)**: از devecosystem.myket.ir
4. برای هر بسته‌ی سکه در صفحه‌ی «بسته‌ها»، همون شناسه‌ی (Product ID) که صفحه‌ی تنظیمات نشونت می‌ده رو عیناً در پنل بازار و مایکت هم به‌عنوان محصول درون‌برنامه‌ای تعریف کن.

## قدم ۴: گرفتن کلید RSA بازار و مایکت
- **بازار**: پنل کافه‌بازار → اپ → پرداخت درون‌برنامه‌ای → کلید RSA
- **مایکت**: پنل مایکت → اپ → بخش پرداخت درون‌برنامه‌ای → کلید RSA

این دو کلید هیچ‌جا داخل کد نوشته نمی‌شن — از GitHub Secrets در زمان build تزریق می‌شن (قدم بعد).

## قدم ۵: تنظیم GitHub Secrets
توی ریپو: `Settings → Secrets and variables → Actions → New repository secret`

| اسم Secret         | مقدار                                  |
|---------------------|------------------------------------------|
| `BAZAAR_RSA_KEY`   | کلید RSA از پنل کافه‌بازار                |
| `MYKET_RSA_KEY`    | کلید RSA از پنل مایکت                    |
| `KEYSTORE_BASE64`  | کیستور امضا (base64) — برای build امضاشده |
| `KEYSTORE_PASSWORD`| پسورد کیستور                             |
| `KEY_ALIAS`        | نام alias کیستور                         |
| `KEY_PASSWORD`     | پسورد alias                              |

## قدم ۶: build گرفتن با GitHub Actions
- **نسخه‌ی تست (دیباگ):** تب Actions → «Build Debug APK (all flavors)» → Run workflow. خروجی: سه APK دیباگ (bazaar/myket/direct) در Artifacts، هرکدام قابل نصب مستقیم روی گوشی برای تست.
- **نسخه‌ی نهایی (امضاشده):** یک تگ نسخه بساز و push کن:
  ```bash
  git tag v1.0.0
  git push origin v1.0.0
  ```
  یا از تب Actions → «Build Signed Release (all flavors)» → Run workflow. خروجی: AAB (برای آپلود در بازار/مایکت) و APK (برای دانلود مستقیم از سایت، فلیور direct) در Artifacts.

## قدم ۷: انتشار
- **کافه‌بازار**: از Artifacts فایل AAB مربوط به `bazaarRelease` رو در پنل توسعه‌دهندگان آپلود کن.
- **مایکت**: از Artifacts فایل AAB مربوط به `myketRelease` رو در پنل مایکت آپلود کن.
- **سایت (دانلود مستقیم)**: فایل APK مربوط به `direct-release` رو روی سرور بذار و لینک دانلودش رو به سایت اضافه کن.

## نکات امنیتی
- کلیدهای RSA بازار/مایکت هیچ‌وقت داخل ریپوی گیت‌هاب ذخیره نمی‌شن — فقط زمان build از Secrets خونده می‌شن.
- توکن دسترسی بازار/مایکت سمت وردپرس هم رمزنگاری‌شده ذخیره می‌شه (`AICW_Security`).
- هر خرید فقط یک‌بار می‌تونه پردازش بشه (جدول `aicw_iap_tokens` با UNIQUE KEY روی توکن)، پس تلاش برای جعل شارژ با تکرار توکن ناموفقه.
