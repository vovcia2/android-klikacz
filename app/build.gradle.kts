plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// ---------------------------------------------------------------------------------------------
// Automatic versioning.
//
//   versionCode = number of commits reachable from HEAD (monotonic on a linear main branch)
//   versionName = "<tag>"        when HEAD is tagged vX.Y.Z (release builds from a tag)
//               = "<base>.<versionCode>" otherwise, e.g. 0.1.42 (base from gradle.properties)
//
// CI may override both with the VERSION_CODE / VERSION_NAME environment variables.
// The APK is renamed to ClickLog-<versionName>-<variant>.apk.
// ---------------------------------------------------------------------------------------------
fun git(vararg args: String): String? = runCatching {
    providers.exec {
        commandLine("git", *args)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().ifEmpty { null }
}.getOrNull()

val versionBase: String = (findProperty("app.versionBase") as String?) ?: "0.1"
val gitCommitCount: Int = git("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1
val gitSha: String = git("rev-parse", "--short=8", "HEAD") ?: "nogit"
val gitTag: String? = git("describe", "--tags", "--exact-match", "HEAD")
    ?.takeIf { Regex("""v\d+\.\d+\.\d+""").matches(it) }

val appVersionCode: Int = System.getenv("VERSION_CODE")?.toIntOrNull() ?: gitCommitCount
val appVersionName: String = System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() }
    ?: gitTag?.removePrefix("v")
    ?: "$versionBase.$gitCommitCount"

logger.lifecycle("ClickLog version: $appVersionName ($appVersionCode) @ $gitSha")

android {
    namespace = "com.klikacz.clicklog"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.klikacz.clicklog"
        minSdk = 31
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // ClickLog-<versionName>-<variant>.apk
    applicationVariants.all {
        val variant = this
        outputs.all {
            (this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl)?.outputFileName =
                "ClickLog-${variant.versionName}-${variant.name}.apk"
        }
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.media)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.timber)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
