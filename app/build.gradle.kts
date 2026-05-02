import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.BuiltArtifactsLoader
import org.gradle.internal.extensions.stdlib.capitalized
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
}

val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    load(FileInputStream(keystorePropertiesFile))
}

val majorVersion = 4
val minorVersion = 2
val patchVersion = 6
val buildVersion = 0
val version = "${majorVersion}.${minorVersion}.${patchVersion}"

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
        versionCode =
            "%d%d%02d%02d".format(majorVersion, minorVersion, patchVersion, buildVersion).toInt()
        multiDexEnabled = true
        versionName = version
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
        create("RelWithDebInfo") {
            initWith(buildTypes["release"])
            versionNameSuffix = "-relwithdebinfo"
            isDebuggable = true
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
        resValues = true
    }
    androidResources {
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
    }

}

androidComponents {
    var apkFolder = layout.buildDirectory.dir("outputs/apk").get()
    var deleteFolder = tasks.register<Delete>("deleteReleaseAPKs") {
        delete(apkFolder.dir("release"))
    }
    tasks.named("assemble") {
        dependsOn(deleteFolder)
    }
    onVariants { variant ->
        var suffix = ""
        if (variant.flavorName == "pre28") {
            suffix += "_pre28"
        }
        if (variant.buildType == "debug") {
            suffix += "_debug"
        } else if (variant.buildType == "RelWithDebInfo") {
            suffix += "_relwithdebinfo"
        }
        var renameTask =
            tasks.register<CreateRenamedApk>("createRenamedApk${variant.flavorName?.capitalized()}${variant.buildType?.capitalized()}") {
                this.apkFolder.set(variant.artifacts.get(SingleArtifact.APK))
                this.builtArtifactsLoader.set(variant.artifacts.getBuiltArtifactsLoader())
                this.versionName.set(
                    variant.outputs.single().versionName.get().substringBeforeLast("-")
                )
                this.suffix.set(suffix)
            }.get()
        tasks.whenTaskAdded {
            if (name == "assemble${variant.flavorName?.capitalized()}${variant.buildType?.capitalized()}") {
                finalizedBy(renameTask)
                dependsOn(deleteFolder)
            } else if (name == "assemble") {
                dependsOn(renameTask)
            }
        }
    }
    beforeVariants {
        it.enableAndroidTest = false
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
    implementation("androidx.work:work-runtime:2.11.2")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("com.google.android.material:material:1.13.0")

// Other
    // image loading and caching
    implementation("com.github.bumptech.glide:glide:5.0.7") {
        exclude(group = "com.android.support")
    }
    implementation("com.github.bumptech.glide:okhttp3-integration:5.0.7@aar")
    annotationProcessor("com.github.bumptech.glide:compiler:5.0.7")
    // For Http Connection
    implementation("com.squareup.okhttp3:okhttp-urlconnection:5.3.2")
    // Used to store the cookies between runs
    implementation("com.github.franmontiel:PersistentCookieJar:v1.0.1")
    // To parse HTML
    implementation("org.jsoup:jsoup:1.22.2")
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
        val builtArtifacts = builtArtifactsLoader.get().load(apkFolder.get())
            ?: throw RuntimeException("Cannot load APKs")
        val newName = "NClientV3_${versionName.get()}${suffix.get()}.apk"
        val newFile = apkFolder.asFile.get().resolve(newName)
        File(builtArtifacts.elements.single().outputFile).renameTo(newFile)
        if (!suffix.get().contains("_debug")) {
            val releaseFolder =
                apkFolder.get().asFile.parentFile.parentFile.resolve("release").resolve(newName)
            newFile.copyTo(releaseFolder)
        }
    }
}
