plugins {
    id("com.android.application") version "8.5.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.dagger.hilt.android") version "2.51" apply false
    id("androidx.navigation.safeargs.kotlin") version "2.8.0" apply false
    // ktlint — Kotlin style enforcement (LA-02, CD-AD-06)
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2" apply false
}
