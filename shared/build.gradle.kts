// Modified for TG Drive: Android integration, safety fixes and visual adaptation; see third_party/driveinfinity/SOURCE.md.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlin.serialization)
}
kotlin {
    jvmToolchain(21)
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
    android { namespace="com.infinity.drive.shared"; compileSdk=37; minSdk=26; withHostTestBuilder {} }
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            api(libs.androidx.paging.common)
            api(libs.androidx.room.runtime)
            api(libs.androidx.room.paging)
            api(libs.androidx.sqlite.bundled)
            api(libs.androidx.datastore.preferences.core)
        }
        val jvmCommonMain=create("jvmCommonMain") { dependsOn(commonMain.get()) }
        named("androidMain") { dependsOn(jvmCommonMain) }
        val jvmCommonTest=create("jvmCommonTest") { dependsOn(commonTest.get()); dependencies { implementation(libs.junit); implementation(libs.kotlinx.coroutines.test) } }
        named("androidHostTest") { dependsOn(jvmCommonTest) }
    }
}
room { schemaDirectory("$projectDir/schemas") }
dependencies { add("kspAndroid",libs.androidx.room.compiler) }

// AGP's host-test lint model consumes the KSP test source directories too.
tasks.matching { it.name.contains("lint",ignoreCase=true) && it.name.contains("AndroidHostTest") }.configureEach {
    dependsOn("kspAndroidHostTest")
}
