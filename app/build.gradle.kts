import com.android.build.api.artifact.SingleArtifact
import java.io.FileInputStream
import java.util.Properties
import com.android.build.api.variant.BuiltArtifactsLoader
import com.android.build.api.variant.VariantOutputConfiguration
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.extensions.stdlib.capitalized

plugins {
    id("com.android.application")
}

val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    load(FileInputStream(keystorePropertiesFile))
}

android {
    signingConfigs {
        create("release") {
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
            storeFile = file(keystoreProperties["storeFile"] as String)
            storePassword = keystoreProperties["storePassword"] as String
        }
    }
    compileSdk = 36
    defaultConfig {
        applicationId = "com.maxwai.nclientv3"
        // Format: MmPPbb
        // M: Major, m: minor, P: Patch, b: build
        versionCode = 410500
        multiDexEnabled = true
        versionName = "4.1.5"
        vectorDrawables.useSupportLibrary = true
        proguardFiles("proguard-rules.pro")
    }
    flavorDimensions += "sdk"
    productFlavors {
        create("post28") {
            dimension = "sdk"
            targetSdk = 36
            minSdk = 28
        }
        create("pre28") {
            dimension = "sdk"
            targetSdk = 28
            minSdk = 26
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            versionNameSuffix = "-release"
            resValue("string", "app_name", "NClientV3")
            signingConfig = signingConfigs.getByName("release")
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "NClientV3 Debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
        disable += "RestrictedApi"
    }
    bundle {
        language {
            // Specifies that the app bundle should not support
            // configuration APKs for language resources. These
            // resources are instead packaged with each base and
            // dynamic feature APK.
            @Suppress("UnstableApiUsage")
            enableSplit = false
        }
    }
    namespace = "com.maxwai.nclientv3"
    buildFeatures {
        buildConfig = true
    }
    androidResources {
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
    }

}

androidComponents {
    onVariants() { variant ->
        var renameTask = tasks.register<CreateRenamedApk>("createRenamedApk${variant.flavorName?.capitalized()}${variant.buildType?.capitalized()}") {
            this.apkFolder.set(variant.artifacts.get(SingleArtifact.APK))
            this.builtArtifactsLoader.set(variant.artifacts.getBuiltArtifactsLoader())
            this.versionName.set(variant.outputs.single().versionName.get().substringBeforeLast("-"))
            this.suffix.set((if (variant.flavorName == "pre28") "_pre28" else "") + (if (variant.buildType == "debug") "_debug" else ""))
        }.get()
        tasks.whenTaskAdded {
            if (name == "assemble${variant.flavorName?.capitalized()}${variant.buildType?.capitalized()}") {
                finalizedBy(renameTask)
            }
        }
    }
}


dependencies {
// Android
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.fragment:fragment:1.8.9")
    implementation("androidx.preference:preference:1.2.1")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.work:work-runtime:2.11.1")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("com.google.android.material:material:1.13.0")

// Other
    // image loading and caching
    implementation("com.github.bumptech.glide:glide:5.0.5") {
        exclude(group = "com.android.support")
    }
    implementation("com.github.bumptech.glide:okhttp3-integration:5.0.5@aar")
    annotationProcessor("com.github.bumptech.glide:compiler:5.0.5")
    // For Http Connection
    implementation("com.squareup.okhttp3:okhttp-urlconnection:5.3.2")
    // Used to store the cookies between runs
    implementation("com.github.franmontiel:PersistentCookieJar:v1.0.1")
    // To parse HTML
    implementation("org.jsoup:jsoup:1.22.1")
    // color picker
    implementation("com.github.yukuku:ambilwarna:2.0.1")
    // fast scroll
    implementation("me.zhanghai.android.fastscroll:library:1.3.0")
}



abstract class CreateRenamedApk : DefaultTask() {
    @get:InputFiles
    abstract val apkFolder: DirectoryProperty

    @get:Internal
    abstract val builtArtifactsLoader: Property<BuiltArtifactsLoader>

    @get:Input
    abstract val versionName: Property<String>

    @get:Input
    abstract val suffix: Property<String>

    @TaskAction
    fun taskAction() {
        val builtArtifacts = builtArtifactsLoader.get().load(apkFolder.get()) ?: throw RuntimeException("Cannot load APKs")
        File(builtArtifacts.elements.single().outputFile).renameTo(
            File(apkFolder.asFile.get(), "NClientV3_${versionName.get()}${suffix.get()}.apk")
        )
    }
}
