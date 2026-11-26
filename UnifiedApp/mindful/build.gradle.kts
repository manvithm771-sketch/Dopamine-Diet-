plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
}

android {
    namespace = "com.mindful.android"
    compileSdk = 35
    defaultConfig { minSdk = 29 }
    sourceSets["main"].java.srcDirs("../../Mindful/android/app/src/main/java")
    sourceSets["main"].res.srcDirs("../../Mindful/android/app/src/main/res")
    sourceSets["main"].manifest.srcFile("src/main/AndroidManifest.xml")
    buildFeatures {
        viewBinding = true
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

kapt {
    arguments { arg("room.schemaLocation", "$projectDir/schemas") }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.work.runtime.ktx)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    // NativeDatabaseProvider and MindfulNativeDatabase are consumed by :app.
    api(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)
    implementation(libs.datastore.preferences)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
