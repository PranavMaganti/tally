import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

sqldelight {
    databases {
        create("TallyDatabase") {
            packageName.set("me.vanpra.tally.db")
        }
    }
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    android {
       namespace = "me.vanpra.tally.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.mlkit.genai.prompt)
            implementation(libs.sqldelight.android)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.sqldelight.adapters)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.sqldelight.jvm)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.testRunner)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
// Note-reading evals (evals/run-note-evals.sh): opt-in with -Peval, never part of normal test runs.
// The key comes from local.properties and goes straight to the eval process; it is never printed.
tasks.withType<Test>().configureEach {
    if (project.hasProperty("eval")) {
        val keys = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
        filter { includeTestsMatching("me.vanpra.tally.eval.*") }
        systemProperty("tally.eval", "true")
        systemProperty("tally.geminiKey", keys.getProperty("GEMINI_API_KEY", ""))
        systemProperty("tally.deepseekKey", keys.getProperty("DEEPSEEK_API_KEY", ""))
        systemProperty("tally.evalFile", rootProject.file("evals/note-evals.jsonl").path)
        systemProperty("tally.evalModels", project.findProperty("evalModels")?.toString() ?: "gemini-flash-lite-latest")
        systemProperty("tally.evalNano", project.findProperty("evalNano")?.toString() ?: "")
        systemProperty("tally.evalOut", rootProject.file("evals/results").path)
        // Free-tier keys allow only a handful of requests a minute; pace calls so the eval measures reading, not limits.
        systemProperty("tally.evalPaceMs", project.findProperty("evalPaceMs")?.toString() ?: "4500")
        outputs.upToDateWhen { false }
        testLogging { showStandardStreams = true }
    } else {
        filter { excludeTestsMatching("me.vanpra.tally.eval.*") }
    }
}
