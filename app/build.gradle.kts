import java.io.File
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
    id("androidx.navigation.safeargs.kotlin")
}

// Quality gates — deliberate configuration, 2026-09-15.
//
// ktlint and JaCoCo were both removed on purpose.
//
//   ktlint  was permanently failing (accumulated style debt plus an upstream
//           crash in the argument-list-wrapping rule). A gate that can never go
//           green trains everyone to pass -x flags, which is how genuine Android
//           Lint errors went unnoticed. Formatting is now an IDE concern.
//
//   JaCoCo  declared a 95% line/branch threshold that was never wired into
//           `check`, so it never ran. It was an assurance claim with no evidence
//           behind it. SRS requirement NF-11 (>= 70% line coverage on domain and
//           data layers) consequently has NO automated enforcement — this is
//           recorded in the SRS rather than left implied.
//
// Android Lint remains active and is the one real gate. Do not re-add a coverage
// threshold without also attaching it to `check`; an unattached gate is worse
// than none.

// Single source of truth for the app version. Release APKs in releases/ are named
// from this value, so the filename can never disagree with what the app reports.
val appVersionName = "3.02"

// major*100 + minor keeps codes monotonic across the whole v1.0 -> v2.8 history
// (1.0 -> 100, 2.8 -> 208) and leaves room for 99 minor releases per major.
val appVersionCode = appVersionName.split(".").let { it[0].toInt() * 100 + it[1].toInt() }

// Release signing. Google Play rejects unsigned uploads, so `bundleRelease` needs
// a real key -- but the credentials must never enter the repository. They live in
// keystore.properties (gitignored) and point at a keystore stored outside the
// project tree entirely, so it cannot be added by an over-broad `git add`.
//
// When the file is absent (CI, a fresh clone, anyone who is not the publisher)
// the build still works: release simply stays unsigned and the release-packaging
// script refuses to produce a Play artifact. A missing key degrades the build; it
// must never break it.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    val stream = FileInputStream(keystorePropertiesFile)
    stream.use { input -> keystoreProperties.load(input) }
}
val keystoreStorePath: String? = keystoreProperties.getProperty("storeFile")
val hasSigningConfig = keystoreStorePath != null && File(keystoreStorePath).exists()

android {
    namespace = "com.drivingcoach"
    compileSdk = 36

    defaultConfig {
        // The identity Google Play binds to this app. Permanent from the first
        // upload onward: it can never be changed without shipping a new listing
        // that existing installs will not upgrade to. The Kotlin namespace below
        // is deliberately left as com.drivingcoach -- Play never sees it, and
        // renaming it touches every source file for no external benefit.
        applicationId = "io.github.emidiofaria.trillian"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "com.drivingcoach.HiltTestRunner"

        // NF-20: telemetry upload is off in every build.
        //
        // The upload worker, its API surface and its tests are all kept intact,
        // because a backend is planned -- but until one exists the feature can
        // only fail, and its UI told the driver that sessions were being
        // uploaded, which contradicted the Play data-safety declaration.
        //
        // The release build additionally ships no INTERNET permission, so this
        // flag is defence in depth rather than the sole guarantee. Turning it
        // back on means restoring that permission and updating Data Safety in
        // the same commit.
        buildConfigField("boolean", "UPLOAD_ENABLED", "false")
    }

    signingConfigs {
        create("release") {
            if (hasSigningConfig) {
                storeFile = File(keystoreStorePath!!)
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        // Required for BuildConfig.VERSION_NAME/VERSION_CODE, consumed by the About screen.
        // AGP 8 stopped generating BuildConfig unless explicitly asked.
        buildConfig = true
    }
    
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    sourceSets {
        // MigrationTestHelper opens the exported schemas at runtime on the device,
        // so the JSON Room writes at build time has to be packaged with the tests.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")

        // The lap fixtures are shared with the L1 suite rather than copied. The L2
        // end-to-end test asserts the same 12 laps that LapDetectionIncident15Test
        // asserts on the JVM, and that claim is only worth making if both levels
        // read the same bytes. A second copy would let the two drift apart silently.
        getByName("androidTest").assets.srcDir("$projectDir/src/test/resources/lapfixtures")
    }

    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl)
                .outputFileName = "DrivingCoach-v$appVersionName-$name.apk"
        }
    }
}

kapt {
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
    correctErrorTypes = true
}

// ─── Test task inputs ─────────────────────────────────────────────────────────

// BundledTrackCatalogTest and BaltarSurveyCorroborationTest read the shipped
// catalogue straight off disk, by path, because the point of those tests is to
// assert against the exact bytes that go into the APK rather than a copy.
//
// Gradle cannot see that. The asset is not on the unit-test runtime classpath, so
// editing tracks.json left `testDebugUnitTest` UP-TO-DATE and the catalogue tests
// silently did not run. A wrong circuit could be committed through a green build
// -- the precise failure the catalogue tests exist to prevent.
//
// Declaring the directory as an explicit input restores the link.
tasks.withType<Test>().configureEach {
    inputs.dir("$projectDir/src/main/assets/tracks")
        .withPropertyName("trackCatalogue")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    // CircuitEvidenceAnnexTest reads the SRS and Annex A by path for the same reason, and
    // needs the same declaration. TL-18's whole value is that a circuit cannot be added
    // without its evidence; an UP-TO-DATE build would hand back that guarantee for free.
    inputs.dir("$rootDir/01_requirements")
        .withPropertyName("requirementsDocuments")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

// ─── Dependencies ─────────────────────────────────────────────────────────────

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Material Design
    implementation("com.google.android.material:material:1.12.0")

    // ConstraintLayout
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.8.0")
    implementation("androidx.navigation:navigation-ui-ktx:2.8.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")

    // Hilt Dependency Injection
    implementation("com.google.dagger:hilt-android:2.51")
    kapt("com.google.dagger:hilt-compiler:2.51")

    // WorkManager with Hilt
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    kapt("androidx.hilt:hilt-compiler:1.2.0")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Google Play Services - Location (FusedLocationProvider)
    implementation("com.google.android.gms:play-services-location:21.2.0")

    // Retrofit & OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // MPAndroidChart for speed charts
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // ViewPager2
    implementation("androidx.viewpager2:viewpager2:1.1.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("androidx.work:work-testing:2.9.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.json:json:20231013")  // For org.json.JSONObject in JVM tests
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test:core-ktx:1.5.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.5.1")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.work:work-testing:2.9.0")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")

    // Hilt testing
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.51")
    kaptAndroidTest("com.google.dagger:hilt-compiler:2.51")

    // Fragment testing
    debugImplementation("androidx.fragment:fragment-testing:1.8.0")

    // Navigation testing
    androidTestImplementation("androidx.navigation:navigation-testing:2.8.0")
}
