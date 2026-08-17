// Top-level build file where you can add configuration options common to all sub-projects/modules.

// AGP 9's built-in Kotlin secara default memakai compiler Kotlin versi internal yang
// dibundel di dalam AGP itu sendiri, dan versi minimum itu BERUBAH per rilis minor AGP:
//   - AGP 9.0.x / 9.1.x -> lantai bawaan KGP 2.2.10
//   - AGP 9.2.0 ke atas  -> lantai bawaan dinaikkan ke KGP 2.3.10
//     (lihat fixed issue resmi "Update Kotlin Gradle plugin dependency to 2.3.10" di
//     https://developer.android.com/build/releases/agp-9-2-0-release-notes)
// Proyek ini memakai AGP 9.2.1 (lihat gradle/libs.versions.toml) tetapi classpath di bawah
// masih dikunci ke 2.2.10 (versi lama, sisa migrasi AGP 9.0). Akibatnya compiler Kotlin
// yang benar-benar dipakai AGP untuk compileDebugKotlin tidak lagi cocok secara biner dengan
// compose-compiler-plugin yang di-fetch oleh plugin kotlin.plugin.compose pada versi 2.2.10
// (lihat alias kotlin-compose di libs.versions.toml, version.ref = "kotlin"), menyebabkan:
//   "Plugin androidx.compose.compiler.plugins.kotlin.ComposePluginRegistrar is incompatible
//    with the current version of the compiler" (AbstractMethodError: getPluginId()).
// Fix resmi dari Android (lihat "Runtime dependency on Kotlin Gradle plugin" di
// https://developer.android.com/build/releases/agp-9-0-0-release-notes): paksa built-in
// Kotlin memakai artifact Kotlin Gradle Plugin yang eksplisit dari Maven, bukan versi
// internal bawaan AGP, supaya satu compiler yang sama dipakai di semua tempat (termasuk oleh
// compose compiler plugin).
// PENTING: versi di bawah ini HARUS selalu sama persis dengan `kotlin` di libs.versions.toml,
// dan HARUS dicek ulang setiap kali AGP dinaikkan versi minor-nya (cek tabel Compatibility +
// bagian "Fixed issues" di release notes AGP yang bersangkutan untuk baris seperti
// "Update Kotlin Gradle plugin dependency to X.Y.Z").
buildscript {
  repositories {
    google()
    mavenCentral()
  }
  dependencies {
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.10")
  }
}

plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
}
