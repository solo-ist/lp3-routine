plugins { id("com.android.application") }

android {
    namespace = "ist.solo.routine"
    compileSdk = 34

    defaultConfig {
        applicationId = "ist.solo.routine"
        minSdk = 34
        targetSdk = 34
        versionCode = 3
        versionName = "0.2.1"
        resValue("string", "app_label", "Routine")
    }

    signingConfigs {
        create("soloist") {
            storeFile = file(
                System.getenv("ROUTINE_SIGNING_STORE")
                    ?: "${System.getProperty("user.home")}/.android-keys/soloist-routine.jks",
            )
            storePassword = System.getenv("ROUTINE_SIGNING_PASSWORD").orEmpty()
            keyAlias = "soloist-routine"
            keyPassword = System.getenv("ROUTINE_SIGNING_PASSWORD").orEmpty()
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        debug {
            // Never the release identity, and a separate applicationId: a
            // debuggable build must not be able to replace the release app.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_label", "Routine (debug)")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("soloist")
        }
    }

    buildFeatures {
        resValues = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Test-only. The app itself has no dependencies beyond the platform;
    // org.json is the real implementation of the stubs in android.jar.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
