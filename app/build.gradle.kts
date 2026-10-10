import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use(::load)
    }
}

fun localOrGradleProperty(name: String) =
    providers.gradleProperty(name).orElse(localProperties.getProperty(name) ?: "")

val supabaseUrl = localOrGradleProperty("SUPABASE_URL")
val supabasePublishableKey = localOrGradleProperty("SUPABASE_PUBLISHABLE_KEY")

android {
    namespace = "dev.holdthisthought"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.holdthisthought"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.4.0"
        buildConfigField("String", "SUPABASE_URL", "\"${supabaseUrl.get()}\"")
        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            "\"${supabasePublishableKey.get()}\"",
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.ui:ui:1.10.6")
    implementation("androidx.compose.ui:ui-tooling-preview:1.10.6")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.room:room-runtime:2.8.2")
    implementation("androidx.room:room-ktx:2.8.2")
    implementation("androidx.work:work-runtime-ktx:2.11.1")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("com.google.mlkit:image-labeling:17.0.9")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation(platform("io.github.jan-tennert.supabase:bom:3.7.0"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:functions-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.ktor:ktor-client-android:3.5.1")
    ksp("androidx.room:room-compiler:2.8.2")
    debugImplementation("androidx.compose.ui:ui-tooling:1.10.6")
    testImplementation("junit:junit:4.13.2")
}
