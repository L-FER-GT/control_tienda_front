import java.util.Properties
import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.play.publisher)
}

// Public client configuration only. Never embed a secret/service-role key.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val envValues = mutableMapOf<String, String>()
listOf(rootProject.file("../control_tienda_backend/.env"), rootProject.file(".env")).forEach { f ->
    if (f.exists()) f.readLines().forEach { line ->
        val clean = line.trim()
        if (clean.isNotEmpty() && !clean.startsWith("#") && clean.contains("=")) {
            val (key, value) = clean.split("=", limit = 2)
            envValues[key.trim()] = value.trim().removeSurrounding("\"").removeSurrounding("'")
        }
    }
}
fun setting(name: String, fallback: String = ""): String =
    System.getenv(name) ?: providers.gradleProperty(name).orNull ?: localProps.getProperty(name) ?: envValues[name] ?: fallback
fun quoted(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
val supabaseKey = setting("SUPABASE_ANON_KEY")
require(!supabaseKey.startsWith("sb_secret_") && !runCatching {
    String(Base64.getUrlDecoder().decode(supabaseKey.split('.')[1])).contains("service_role")
}.getOrDefault(false)) { "Android accepts only SUPABASE_ANON_KEY (anon/publishable), never service_role." }

// Firma de release: keystore.properties (local) o variables de entorno (CI).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val releaseStoreFile: String? = keystoreProps.getProperty("storeFile") ?: System.getenv("ANDROID_KEYSTORE_PATH")

// Versión = tag vX.Y.Z del release (el workflow pasa VERSION_NAME). versionCode usa la misma fórmula
// que AppVersion en el dominio, así la app compara su versión con la del último release de GitHub.
val appVersionName = setting("VERSION_NAME", "0.0.0")
val appVersionCode = Regex("""(\d{1,4})\.(\d{1,2})\.(\d{1,2})""").matchEntire(appVersionName)?.destructured
    ?.let { (major, minor, patch) -> major.toInt() * 10_000 + minor.toInt() * 100 + patch.toInt() }
    ?: error("VERSION_NAME debe ser X.Y.Z con minor y patch entre 0 y 99, no '$appVersionName'.")

android {
    namespace = "com.lfergt.controltienda"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lfergt.controltienda"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode.coerceAtLeast(1)
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", quoted(setting("SUPABASE_URL")))
        buildConfigField("String", "SUPABASE_ANON_KEY", quoted(supabaseKey))
        buildConfigField("String", "SUPABASE_STORAGE_BUCKET", quoted(setting("SUPABASE_STORAGE_BUCKET", "media")))
        resValue("string", "default_web_client_id", setting("GOOGLE_WEB_CLIENT_ID"))
        // Repositorio público cuyos releases ofrece la app como actualización; vacío = desactivado.
        buildConfigField("String", "UPDATE_REPO", quoted(""))
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = keystoreProps.getProperty("storePassword") ?: System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = keystoreProps.getProperty("keyAlias") ?: System.getenv("ANDROID_KEY_ALIAS")
                // En un keystore PKCS12 la llave usa la misma contraseña: ANDROID_KEY_PASSWORD es opcional.
                keyPassword = keystoreProps.getProperty("keyPassword")
                    ?: System.getenv("ANDROID_KEY_PASSWORD")?.takeIf { it.isNotEmpty() } ?: storePassword
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
            // Solo el release busca actualizaciones: debug es otra app (.debug) con otra firma.
            buildConfigField("String", "UPDATE_REPO", quoted(setting("UPDATE_REPO", "L-FER-GT/control_tienda_front")))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        resValues = true
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
