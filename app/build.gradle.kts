import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---- Versi (SemVer) ----------------------------------------------------
// Dikirim dari CI lewat -PversionName=1.2.3 (diambil dari git tag v1.2.3).
val semver = (findProperty("versionName") as String?) ?: "0.1.0"
val semverMatch = Regex("""^(\d+)\.(\d+)\.(\d+)(?:-[0-9A-Za-z.-]+)?$""")
    .matchEntire(semver) ?: error("versionName '$semver' bukan SemVer (MAJOR.MINOR.PATCH)")
val (vMajor, vMinor, vPatch) = semverMatch.destructured
// 1.2.3 -> 1_002_003 : selalu naik mengikuti SemVer (maks minor/patch 999)
val computedVersionCode = vMajor.toInt() * 1_000_000 + vMinor.toInt() * 1_000 + vPatch.toInt()

// ---- Signing -----------------------------------------------------------
// CI: env KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD
// Lokal: keystore.properties (storeFile, storePassword, keyAlias, keyPassword)
val ksProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(env: String, key: String): String? = System.getenv(env) ?: ksProps.getProperty(key)

android {
    namespace = "app.blyric"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.blyric"
        minSdk = 26
        targetSdk = 35
        versionCode = computedVersionCode
        versionName = semver

        buildConfigField("String", "GITHUB_REPO", "\"${findProperty("blyric.githubRepo")}\"")
        buildConfigField("String", "LYRICS_API_BASE", "\"${findProperty("blyric.lyricsApiBase")}\"")
    }

    signingConfigs {
        create("release") {
            val path = signingValue("KEYSTORE_PATH", "storeFile")
            if (path != null) {
                storeFile = rootProject.file(path)
                storePassword = signingValue("KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val rel = signingConfigs.getByName("release")
            // Tanpa keystore -> APK unsigned (CI akan gagal di langkah rename, itu disengaja)
            signingConfig = if (rel.storeFile != null) rel else null
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
}
