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
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // Cafe Bazaar in-app billing SDK (Poolakey)
    // https://github.com/cafebazaar/Poolakey
    implementation("com.github.cafebazaar.Poolakey:poolakey:1.5.4")

    // NOTE: Myket's IAP SDK is not published on a public Maven repo.
    // Download the AAR from Myket's developer panel (devecosystem.myket.ir)
    // and place it in app/libs/, then uncomment the line below:
    // implementation(files("libs/myket-iab.aar"))
}
