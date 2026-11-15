pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("${System.getProperty("user.home")}/.m2/repository") }
        google()
        mavenCentral()
    }
}

rootProject.name = "GalaxyFirm"
include(":app")
