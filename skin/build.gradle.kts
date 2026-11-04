plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
}
kotlin {
    jvmToolchain(21)
    android { namespace="app.tgdrive.skin"; compileSdk=37; minSdk=26
        experimentalProperties["android.experimental.kmp.enableAndroidResources"]=true
    }
    sourceSets {
        commonMain.dependencies { api(libs.cmp.runtime); api(libs.cmp.foundation); api(libs.cmp.ui); api(libs.cmp.material3); implementation(libs.jb.lifecycle.runtime.compose) }
    }
}
