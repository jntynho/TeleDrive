import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// AGP resource shrinking requires bundle and multi-APK outputs in separate build directories.
if (providers.gradleProperty("bundleOnly").map(String::toBoolean).getOrElse(false)) {
    layout.buildDirectory.set(layout.projectDirectory.dir("build/bundle-only"))
}

val local = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun setting(name: String): String = System.getenv(name) ?: local.getProperty(name, "")
fun literal(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "") + "\""
val signingNames = listOf("RELEASE_STORE_FILE", "RELEASE_STORE_PASSWORD", "RELEASE_KEY_ALIAS", "RELEASE_KEY_PASSWORD")
val hasSigning = signingNames.all { setting(it).isNotBlank() }
require(signingNames.none { setting(it).isNotBlank() } || hasSigning) { "Provide all four RELEASE_* signing settings, or none." }

android {
    namespace = "app.tgdrive"
    compileSdk = 37
    buildToolsVersion = "37.0.0"
    defaultConfig {
        applicationId = "app.tgdrive.next"
        minSdk = 26
        targetSdk = 37
        versionCode = setting("APP_VERSION_CODE").ifBlank { "24" }.toInt().also { require(it > 0) }
        versionName = setting("APP_VERSION_NAME").ifBlank { "1.0.0" }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean","STARTUP_BENCHMARK","false")
        buildConfigField("String", "TELEGRAM_API_ID", literal(setting("TELEGRAM_API_ID")))
        buildConfigField("String", "TELEGRAM_API_HASH", literal(setting("TELEGRAM_API_HASH")))
    }
    signingConfigs {
        if (hasSigning) create("release") {
            storeFile = file(setting("RELEASE_STORE_FILE"))
            storePassword = setting("RELEASE_STORE_PASSWORD")
            keyAlias = setting("RELEASE_KEY_ALIAS")
            keyPassword = setting("RELEASE_KEY_PASSWORD")
        }
    }
    buildTypes {
        debug {
            // Automated UI tests never initialize Telegram with production application credentials.
            buildConfigField("String", "TELEGRAM_API_ID", literal(""))
            buildConfigField("String", "TELEGRAM_API_HASH", literal(""))
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
        }
        if(providers.gradleProperty("startupBenchmark").orNull=="true")create("benchmark"){
            initWith(getByName("release"))
            applicationIdSuffix=".benchmark"
            buildConfigField("boolean","STARTUP_BENCHMARK","true")
            signingConfig=signingConfigs.getByName("debug")
            isDebuggable=false
            isMinifyEnabled=providers.gradleProperty("startupMeasure").orNull=="true"
            isShrinkResources=isMinifyEnabled
            matchingFallbacks+=listOf("release")
            buildConfigField("String","TELEGRAM_API_ID",literal(""))
            buildConfigField("String","TELEGRAM_API_HASH",literal(""))
        }
    }
    // Opt in for distribution; ordinary debug/emulator builds retain their current packaging.
    splits {
        abi {
            isEnable = providers.gradleProperty("splitApks").map(String::toBoolean).getOrElse(false)
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Compress native libraries for smaller APK downloads; Android extracts them at install.
    packaging { jniLibs.useLegacyPackaging = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            // JDK 21 C2 can crash compiling Robolectric's rewritten SQLiteCursor methods.
            // Exclude only this host-test shim; application/ART compilation is unchanged.
            test.jvmArgs("-XX:CompileCommand=exclude,android.database.sqlite.SQLiteCursor::*")

            test.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
            // Respect the build machine's supported HTTPS proxy/trust configuration in test JVMs.
            listOf("https.proxyHost", "https.proxyPort", "http.proxyHost", "http.proxyPort", "http.nonProxyHosts", "javax.net.ssl.trustStore").forEach { name ->
                System.getProperty(name)?.let { test.systemProperty(name, it) }
            }
        }
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.4")
    implementation(project(":shared"))
    implementation(project(":ui"))
    implementation(project(":skin"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.google.android.material)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.koin.androidx.workmanager)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.exifinterface)
    implementation(libs.zip4j)
    implementation(libs.zxing.core)
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")
    implementation("androidx.fragment:fragment-ktx:1.8.8")
    // Existing native TDLib and Tink retain access to installed TG Drive sessions/vaults.
    implementation("io.github.tdlib-android:core:0.1.1")
    implementation("com.google.crypto.tink:tink-android:1.18.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.koin.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.work.testing)
}
