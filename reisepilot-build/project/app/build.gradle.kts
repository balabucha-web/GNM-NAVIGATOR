plugins { id("com.android.application") }

android {
    namespace = "de.balabucha.reisepilot"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.balabucha.reisepilot"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
