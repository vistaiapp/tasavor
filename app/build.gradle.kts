plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ir.tasavor.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "ir.tasavor.app"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        // MARKET_ID / MARKET_RSA_KEY فیلدهای مشترکند؛ هر فلیور (bazaar/myket/direct)
        // پایین‌تر مقدار خودش را روی این‌ها override می‌کند تا MainActivity.kt
        // بدون هیچ تغییری برای هر سه فلیور کامپایل شود.
        buildConfigField("String", "MARKET_ID", "\"direct\"")
        buildConfigField("String", "MARKET_RSA_KEY", "\"\"")
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // سه مسیر توزیع جدا، دقیقاً مثل ویست‌ای‌آی:
    // - bazaar: از کافه‌بازار نصب شده → پرداخت درون‌برنامه‌ای بازار (Poolakey)
    // - myket: از مایکت نصب شده → پرداخت درون‌برنامه‌ای مایکت (myket-billing-client)
    // - direct: نسخه‌ای که مستقیم از خود سایت دانلود می‌شود → هیچ پل جاوااسکریپتی
    //   ثبت نمی‌شود، پس افزونه‌ی کیف‌پول تصور همان فرم زرین‌پال معمولی سایت را نشان می‌دهد.
    flavorDimensions += "market"
    productFlavors {
        create("bazaar") {
            dimension = "market"
            buildConfigField("String", "MARKET_ID", "\"bazaar\"")
            buildConfigField("String", "MARKET_RSA_KEY", "\"${System.getenv("BAZAAR_RSA_KEY") ?: ""}\"")
        }
        create("myket") {
            dimension = "market"
            buildConfigField("String", "MARKET_ID", "\"myket\"")
            buildConfigField("String", "MARKET_RSA_KEY", "\"${System.getenv("MYKET_RSA_KEY") ?: ""}\"")

            // Placeholder های لازم برای مانیفست کتابخانه‌ی myket-billing-client
            // (طبق مستندات رسمی مایکت: https://myket.ir/kb/pages/java/)
            manifestPlaceholders["marketApplicationId"] = "ir.mservices.market"
            manifestPlaceholders["marketBindAddress"] = "ir.mservices.market.InAppBillingService.BIND"
            manifestPlaceholders["marketPermission"] = "ir.mservices.market.BILLING"
        }
        create("direct") {
            dimension = "market"
            buildConfigField("String", "MARKET_ID", "\"direct\"")
            buildConfigField("String", "MARKET_RSA_KEY", "\"\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // Poolakey (پرداخت درون‌برنامه‌ای بازار) فقط برای فلیور bazaar اضافه می‌شود
    // تا مجوز PAY_THROUGH_BAZAAR در نسخه‌ی مایکت/مستقیم اصلاً وجود نداشته باشد.
    // https://github.com/cafebazaar/Poolakey/releases (نسخه فعلی: 2.2.0)
    "bazaarImplementation"("com.github.cafebazaar.Poolakey:poolakey:2.2.0")

    // کتابخانه‌ی رسمی پرداخت درون‌برنامه‌ای مایکت (فقط برای فلیور myket)
    // https://github.com/myketstore/myket-billing-client
    "myketImplementation"("com.github.myketstore:myket-billing-client:1.19")
}
