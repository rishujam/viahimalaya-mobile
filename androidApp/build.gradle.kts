import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.google.gms)
}

// Secrets come from local.properties (gitignored) or the environment for CI.
// Read through providers rather than a plain file read: configuration cache is
// on, and an untracked read would bake a stale value into the next build when
// local.properties changes.
val localProperties = providers.fileContents(
    rootProject.layout.projectDirectory.file("local.properties")
).asText.map { text -> Properties().apply { load(text.reader()) } }

fun secret(name: String): String =
    localProperties.map { it.getProperty(name).orEmpty() }
        .orElse(providers.environmentVariable(name))
        .getOrElse("")

val viaHimalayaApiKey = secret("VIAHIMALAYA_API_KEY")
if (viaHimalayaApiKey.isBlank()) {
    logger.warn(
        "VIAHIMALAYA_API_KEY is not set in local.properties or the environment - " +
            "every API call in this build will come back 401."
    )
}

android {
    namespace = "com.via.himalaya"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.via.himalaya"
        minSdk = 24
        targetSdk = 36
        versionCode = 11
        versionName = "2.0"

        buildConfigField("String", "API_BASE_URL", "\"https://viahimalaya.com\"")
        buildConfigField("String", "API_KEY", "\"$viaHimalayaApiKey\"")
    }
    buildFeatures {
        compose = true
        // Only androidApp generates BuildConfig. The shared module declares the
        // same namespace, so enabling it there too would produce a second
        // com.via.himalaya.BuildConfig.
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("releaseDebug") {
            // Same minify/shrink/proguard setup as release, just signed with the
            // debug key so this variant is installable for local testing without
            // needing the production upload key.
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(projects.shared)
    implementation(platform(libs.androidx.compose.bom))
    
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.lifecycle.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.map.box.compose)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.koin.android)
    implementation(libs.jetbrains.compose.navigation)
    implementation(libs.koin.androidx.compose)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.identity.google)
    implementation(libs.splash)
    implementation(libs.coil.compose)
    implementation(libs.play.services.location)
    implementation(libs.androidx.browser)
}