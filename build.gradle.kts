// Top-level build file where you can add configuration options common to all sub-projects/modules.

// AGP 9's built-in Kotlin secara default memakai compiler Kotlin versi internal
// yang dibundel di dalam AGP sendiri. Di kombinasi AGP 9.1.1 + Kotlin 2.2.10 proyek ini,
// compiler internal itu ternyata TIDAK cocok secara biner dengan compose-compiler-plugin
// yang di-fetch terpisah oleh plugin kotlin.plugin.compose (versi sama "2.2.10" tapi build
// artifact beda), menyebabkan error saat compileDebugKotlin:
//   "Plugin androidx.compose.compiler.plugins.kotlin.ComposePluginRegistrar is incompatible
//    with the current version of the compiler" (AbstractMethodError: getPluginId()).
// Fix resmi dari Android (lihat "Runtime dependency on Kotlin Gradle plugin" di
// https://developer.android.com/build/releases/agp-9-0-0-release-notes): paksa built-in
// Kotlin memakai artifact Kotlin Gradle Plugin yang eksplisit dari Maven, bukan versi
// internal bawaan AGP, supaya satu compiler yang sama dipakai di semua tempat.
// PENTING: versi di bawah ini HARUS selalu sama persis dengan `kotlin` di libs.versions.toml.
buildscript {
  repositories {
    google()
    mavenCentral()
  }
  dependencies {
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10")
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
