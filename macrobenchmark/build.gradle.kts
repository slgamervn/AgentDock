plugins {
    id("com.android.test")
    id("androidx.benchmark")
}

android {
    namespace = "com.slg.agentdock.macrobenchmark"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    buildTypes {
        create("benchmark") {
            matchingFallbacks += listOf("release")
        }
    }
}

dependencies {
    androidTestImplementation("androidx.benchmark:benchmark-macro-junit4:1.5.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
