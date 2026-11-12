import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val rebrickableKey: String = localProperties.getProperty("REBRICKABLE_API_KEY") ?: ""
val supabaseUrl: String = localProperties.getProperty("SUPABASE_URL") ?: ""
val supabaseAnonKey: String = localProperties.getProperty("SUPABASE_ANON_KEY") ?: ""

android {
    namespace = "nl.nextbuzz.bricktimer"
    compileSdk = 36

    defaultConfig {
        applicationId = "nl.nextbuzz.bricktimer"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "1.7"

        buildConfigField("String", "REBRICKABLE_API_KEY", "\"$rebrickableKey\"")
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
    }

    buildTypes {
        // Google's test interstitial in debug builds: clicking your own live ads gets an AdMob account suspended.
        debug {
            buildConfigField("String", "AD_UNIT_TIMER", "\"ca-app-pub-3940256099942544/1033173712\"")
        }
        release {
            buildConfigField("String", "AD_UNIT_TIMER", "\"ca-app-pub-3296149774798998/9036674263\"")
            // R8 shrinks and obfuscates the code; Play warns about apps that aren't obfuscated.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")

    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("com.google.android.gms:play-services-ads:25.5.0")
    // AdMob brings WorkManager 2.7 (Room 2.2), whose R8 rules predate full mode: its database got stripped
    // and the release build crashed on start. Newer versions ship the right rules.
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
    implementation("com.android.billingclient:billing-ktx:9.1.0")
}
