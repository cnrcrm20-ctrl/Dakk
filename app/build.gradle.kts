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
val dakkVersionName = "2.1.0"
val dakkVersionCode = 22

// ---------------------------------------------------------------------------------------------
// İmzalama — Google ile Giriş (Firebase) APK'nın imza parmak izine bağlıdır.
// Resmi anahtar repoda YALNIZCA şifreli olarak durur: signing/dakk-release.p12.enc
// GitHub Actions, DAKK_SIGNING_PASSPHRASE secret'ı ile anahtarı çözer ve şu ortam değişkenlerini verir:
//   DAKK_KEYSTORE_PATH, DAKK_KEYSTORE_PASSWORD, (isteğe bağlı) DAKK_KEY_ALIAS, DAKK_KEY_PASSWORD
// Resmi anahtarın parmak izleri (Firebase Console'da kayıtlı olmalı):
//   SHA-1  : 1B:8A:A6:D1:88:69:43:31:9A:70:65:4F:A6:DA:DC:7D:F9:E3:5A:E1
//   SHA-256: A1:E4:F4:6F:E3:DE:83:B8:C7:B9:DF:3F:C9:92:EC:F8:EE:17:91:6B:DE:EB:85:C1:C5:FB:41:8E:56:FB:F6:BC
// Anahtar yoksa (ör. yerel geliştirme veya fork) derleme Android debug anahtarıyla imzalanır;
// bu APK çalışır ama Google ile Giriş hata verir.
// ---------------------------------------------------------------------------------------------
fun envOrNull(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

val dakkKeystoreFile: File? = envOrNull("DAKK_KEYSTORE_PATH")?.let { file(it) }?.takeIf { it.isFile }
val dakkKeystorePassword: String? = envOrNull("DAKK_KEYSTORE_PASSWORD")
val dakkKeyAlias: String = envOrNull("DAKK_KEY_ALIAS") ?: "dakk"
val dakkKeyPassword: String? = envOrNull("DAKK_KEY_PASSWORD") ?: dakkKeystorePassword
val hasOfficialSigning: Boolean = dakkKeystoreFile != null && dakkKeystorePassword != null

if (!hasOfficialSigning) {
  logger.warn("Dakk: resmi imza anahtarı bulunamadı — APK debug anahtarıyla imzalanacak (Google ile Giriş çalışmaz).")
}

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
    if (hasOfficialSigning) {
      create("dakk") {
        storeFile = dakkKeystoreFile
        storePassword = dakkKeystorePassword
        keyAlias = dakkKeyAlias
        keyPassword = dakkKeyPassword
        storeType = "PKCS12"
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      isShrinkResources = false
      isCrunchPngs = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.findByName("dakk") ?: signingConfigs.getByName("debug")
    }
    debug {
      // Resmi anahtar mevcutsa debug derlemeleri de onunla imzalanır; böylece Google girişi çalışır.
      signingConfigs.findByName("dakk")?.let { signingConfig = it }
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
