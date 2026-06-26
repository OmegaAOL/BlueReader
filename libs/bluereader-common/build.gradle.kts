
buildscript {
	repositories {
		mavenCentral()
		google()
		maven { url = uri("https://a8c-libs.s3.amazonaws.com/android/jcenter-mirror/") }
	}
}

plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
	kotlin("plugin.serialization") version("2.2.20") apply(true)
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
	implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
}

kotlin {
	compilerOptions {
		jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
	}
}

configurations.all {
	resolutionStrategy {
		eachDependency {
			// Check for the specific OkHttp group ID and artifact name
			if (requested.group == "com.squareup.okhttp3" && requested.name.startsWith("okhttp")) {
				useVersion("3.12.13")
			}
		}
	}
}
