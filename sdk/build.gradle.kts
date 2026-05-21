import com.vanniktech.maven.publish.SonatypeHost

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.vanniktech.maven.publish") version "0.30.0"
}

android {
    namespace = "ai.origon.sdk"
    compileSdk = 35

    defaultConfig {
        minSdk = 24

        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
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

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
    signAllPublications()

    coordinates(
        groupId = "ai.origon",
        artifactId = "sdk",
        // Real releases pass -PsdkVersion=X.Y.Z via release-android.sh.
        // `0.0.0-LOCAL` is the fallback for local dev — works with
        // `./gradlew :sdk:publishToMavenLocal` so example apps can resolve
        // an unreleased SDK from ~/.m2/repository.
        version = providers.gradleProperty("sdkVersion").getOrElse("0.0.0-LOCAL"),
    )

    pom {
        name.set("Origon Android SDK")
        description.set("Android SDK for the Origon platform")
        url.set("https://origon.ai")
        licenses {
            license {
                name.set("Origon Commercial License")
                url.set("https://github.com/Origon/android-sdk/blob/main/LICENSE")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("origon")
                name.set("Origon")
                url.set("https://origon.ai")
            }
        }
        scm {
            url.set("https://github.com/Origon/android-sdk")
            connection.set("scm:git:git://github.com/Origon/android-sdk.git")
            developerConnection.set("scm:git:ssh://git@github.com/Origon/android-sdk.git")
        }
    }
}
