import com.google.firebase.appdistribution.gradle.firebaseAppDistribution
import groovy.json.JsonSlurper
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.appdistribution)
    alias(libs.plugins.play.publisher)
}

// ---------------------------------------------------------------------------
// Configuración de Firebase
// app/google-services.json NO se versiona (el repo es público). Si no existe,
// se copia la configuración "demo" que apunta al Firebase Emulator Suite.
// ---------------------------------------------------------------------------
val googleServicesFile = file("google-services.json")
if (!googleServicesFile.exists()) {
    rootProject.file("config/google-services.demo.json").copyTo(googleServicesFile)
}

@Suppress("UNCHECKED_CAST")
val firebaseProjectId: String = run {
    val json = JsonSlurper().parse(googleServicesFile) as Map<String, Any>
    (json["project_info"] as Map<String, Any>)["project_id"] as String
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun prop(name: String): String? =
    localProps.getProperty(name) ?: providers.gradleProperty(name).orNull ?: System.getenv(name.uppercase().replace('.', '_'))

// Los proyectos "demo-*" solo existen en el emulador; con un proyecto real se usa la nube.
val useEmulators: Boolean = prop("firebase.useEmulators")?.toBoolean() ?: firebaseProjectId.startsWith("demo-")
val emulatorHost: String = prop("firebase.emulatorHost") ?: "10.0.2.2"

// Firma de release: keystore.properties (local) o variables de entorno (CI).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val releaseStoreFile: String? = keystoreProps.getProperty("storeFile") ?: System.getenv("ANDROID_KEYSTORE_PATH")

android {
    namespace = "com.lfergt.controltienda"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lfergt.controltienda"
        minSdk = 26
        targetSdk = 37
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("boolean", "USE_EMULATORS", useEmulators.toString())
        buildConfigField("String", "EMULATOR_HOST", "\"$emulatorHost\"")
        buildConfigField("String", "FUNCTIONS_REGION", "\"us-east1\"")
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = keystoreProps.getProperty("storePassword") ?: System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = keystoreProps.getProperty("keyAlias") ?: System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = keystoreProps.getProperty("keyPassword") ?: System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")

            // Firebase App Distribution: credenciales desde variable de entorno (ver docs/06).
            firebaseAppDistribution {
                artifactType = "APK"
                groups = System.getenv("FIREBASE_APP_DISTRIBUTION_GROUPS") ?: "testers"
                releaseNotes = System.getenv("RELEASE_NOTES") ?: "Build interno"
                System.getenv("FIREBASE_SERVICE_ACCOUNT_PATH")?.let { serviceCredentialsFile = it }
            }
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// Google Play (Gradle Play Publisher): preparado, pero solo se activa si existe la credencial.
val playCredentials = rootProject.file(System.getenv("PLAY_SERVICE_ACCOUNT_PATH") ?: "play-service-account.json")
play {
    enabled.set(playCredentials.exists())
    if (playCredentials.exists()) serviceAccountCredentials.set(playCredentials)
    track.set("internal")
    defaultToAppBundles.set(true)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(platform(libs.firebase.bom))
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.mlkit.vision)
    implementation(libs.mlkit.barcode.scanning)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.junit)
}
