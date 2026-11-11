plugins { id("com.android.test") }
android {
    namespace="app.tgdrive.benchmark.tests"
    compileSdk=37
    targetProjectPath=":app"
    defaultConfig {
        minSdk=28
        targetSdk=37
        testInstrumentationRunner="androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        create("benchmark"){
            isDebuggable=true
            matchingFallbacks+=listOf("release")
        }
    }
    experimentalProperties["android.experimental.self-instrumenting"]=true
    compileOptions {sourceCompatibility=JavaVersion.VERSION_17;targetCompatibility=JavaVersion.VERSION_17}
}
androidComponents {beforeVariants(selector().all()){it.enable=it.buildType=="benchmark"}}
kotlin {compilerOptions {jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)}}
dependencies {
    implementation("androidx.benchmark:benchmark-macro-junit4:1.4.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.test.ext:junit:1.3.0")
    implementation("androidx.test:runner:1.7.0")
}
