import java.time.Duration

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.services)
}

// ---------------------------------------------------------------------------------------------
// Sürüm bilgisi — yeni bir sürüm yayınlamak için SADECE burayı değiştirin.
// main dalına push edildiğinde GitHub Actions bu sürüm için otomatik olarak Release oluşturur.
// ---------------------------------------------------------------------------------------------
val dakkVersionName = "2.0.1"
val dakkVersionCode = 21

// ---------------------------------------------------------------------------------------------
// İmzalama — SHA-1 parmak izinin HER derlemede aynı kalması için sabit anahtar deposu kullanılır.
// Google ile Giriş (Firebase) bu parmak izine bağlıdır; anahtarı değiştirmeyin!
//   SHA-1  : 05:8B:B2:98:2B:5C:20:CD:F5:CD:29:FC:68:3D:4C:CB:C4:A1:56:21
//   SHA-256: A1:31:5B:56:6C:73:7F:46:3E:27:D8:01:36:03:47:F9:C0:80:06:85:5F:E7:66:E7:B9:6F:E1:FB:35:23:FC:D9
// İsteğe bağlı olarak ortam değişkenleriyle (CI secrets) geçersiz kılınabilir.
// ---------------------------------------------------------------------------------------------
fun envOrNull(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

val dakkKeystorePath: String = envOrNull("DAKK_KEYSTORE_PATH") ?: "${rootDir}/signing/dakk-release.p12"
val dakkKeystorePassword: String = envOrNull("DAKK_KEYSTORE_PASSWORD") ?: "dakk-android-2026"
val dakkKeyAlias: String = envOrNull("DAKK_KEY_ALIAS") ?: "dakk"
val dakkKeyPassword: String = envOrNull("DAKK_KEY_PASSWORD") ?: dakkKeystorePassword

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "dakk.android.co"
    minSdk = 24
    targetSdk = 36
    versionCode = dakkVersionCode
    versionName = dakkVersionName

    buildConfigField("String", "GITHUB_REPO", "\"cnrcrm20-ctrl/Dakk\"")
    buildConfigField("String", "INVITE_BASE_URL", "\"https://cnrcrm20-ctrl.github.io/Dakk/invite/\"")
  }

  signingConfigs {
    create("dakk") {
      storeFile = file(dakkKeystorePath)
      storePassword = dakkKeystorePassword
      keyAlias = dakkKeyAlias
      keyPassword = dakkKeyPassword
      storeType = "PKCS12"
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      isShrinkResources = false
      isCrunchPngs = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("dakk")
    }
    debug {
      // Debug derlemeleri de aynı anahtarla imzalanır; böylece Google girişi her derlemede çalışır.
      signingConfig = signingConfigs.getByName("dakk")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)

  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)

  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.play.services)

  implementation(libs.firebase.auth)
  implementation(libs.firebase.firestore)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)

  implementation(libs.okhttp)
  implementation(libs.coil.compose)
  implementation(libs.zxing)

  testImplementation(libs.junit)
  debugImplementation(libs.androidx.compose.ui.tooling)
}

tasks.withType<Test> {
  timeout.set(Duration.ofMinutes(5))
  testLogging { events("passed", "failed", "skipped") }
}
