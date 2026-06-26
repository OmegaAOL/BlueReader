pluginManagement {
	repositories {
		gradlePluginPortal()
		google()
		mavenCentral()
		maven { url = uri("https://a8c-libs.s3.amazonaws.com/android/jcenter-mirror/") }
	}
}

dependencyResolutionManagement {
	repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
	repositories {
		google()
		mavenCentral()
		maven { url = uri("https://a8c-libs.s3.amazonaws.com/android/jcenter-mirror/") }
	}
}
include(":bluereader-app")
include(":bluereader-common")

project(":bluereader-common").projectDir = File("./libs/bluereader-common")

include(":bluereader-datamodel")
project(":bluereader-datamodel").projectDir = File("./libs/bluereader-datamodel")
