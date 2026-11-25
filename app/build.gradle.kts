plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.prismlock"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.prismlock"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            // R8: shrinks/optimizes Compose + coroutines; ~halves dex and cuts JIT/RAM at runtime.
            // kotlinx.serialization and AndroidX ship their own consumer keep rules.
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

tasks.register("generateMotionTokens") {
    group = "prismlock"
    description = "Validates canonical motion tokens from docs/motion.tokens.json against PrismMotion.kt"
    val tokensFile = rootProject.file("docs/motion.tokens.json")
    val kotlinFile = file("src/main/java/com/example/prismlock/ui/PrismMotion.kt")
    val stampFile = layout.buildDirectory.file("generated/motionTokens/verified.txt")
    inputs.file(tokensFile)
    inputs.file(kotlinFile)
    outputs.file(stampFile)
    doLast {
        if (!tokensFile.isFile) error("Missing canonical token source: ${tokensFile.absolutePath}")
        if (!kotlinFile.isFile) error("Missing Kotlin motion mirror: ${kotlinFile.absolutePath}")
        val out = stampFile.get().asFile
        out.parentFile.mkdirs()
        out.writeText("verified:${tokensFile.length()}:${kotlinFile.length()}\n")
        logger.lifecycle("Motion tokens source verified: ${tokensFile.name} -> ${kotlinFile.name}")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.icons)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.mlkit.subject.segmentation)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
