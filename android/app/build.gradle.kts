import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.sportage.obd"
    compileSdk = 35

    // Release signing credentials are read from the environment first (CI) and then from
    // android/keystore.properties (local machines). Neither is ever committed.
    // With no credentials at all the release build stays unsigned instead of failing, so
    // ordinary local builds keep working without a keystore.
    val keystoreProps = Properties()
    val keystorePropsFile = rootProject.file("keystore.properties")
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { keystoreProps.load(it) }
    }

    val envKeystorePath = System.getenv("SPORTAGE_KEYSTORE_FILE")
    val releaseStoreFile = when {
        !envKeystorePath.isNullOrBlank() -> rootProject.file(envKeystorePath)
        !keystoreProps.getProperty("storeFile").isNullOrBlank() ->
            rootProject.file(keystoreProps.getProperty("storeFile")!!)
        else -> null
    }
    val releaseStorePassword =
        System.getenv("SPORTAGE_KEYSTORE_PASSWORD") ?: keystoreProps.getProperty("storePassword")
    val releaseKeyAlias =
        System.getenv("SPORTAGE_KEY_ALIAS") ?: keystoreProps.getProperty("keyAlias")
    val releaseKeyPassword =
        System.getenv("SPORTAGE_KEY_PASSWORD") ?: keystoreProps.getProperty("keyPassword")

    val hasReleaseKeystore = releaseStoreFile?.exists() == true &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

    logger.lifecycle(
        if (hasReleaseKeystore) {
            "Release signing: ENABLED (${releaseStoreFile!!.name})"
        } else {
            "Release signing: no credentials - assembleRelease will produce an UNSIGNED apk"
        }
    )

    defaultConfig {
        applicationId = "com.sportage.obd"
        minSdk = 28 // Android 9+ as specified in PRD Section 1.1
        targetSdk = 35
        versionCode = 7
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                // minSdk is 28, so APK Signature Scheme v2 alone is enough; JAR (v1) signing
                // only matters below Android 7.0 and AGP does not emit it above minSdk 24.
                enableV1Signing = false
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
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
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.kotlinx.coroutines.android)
    implementation("org.mapsforge:mapsforge-map-android:0.25.0")
    implementation("org.mapsforge:mapsforge-map-reader:0.25.0")
    // Kotlin 2.0-compatible native release; do not bypass Kotlin metadata checks.
    implementation("org.maplibre.gl:android-sdk:12.0.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
