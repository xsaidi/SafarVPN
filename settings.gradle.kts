pluginManagement {
    repositories {
        maven(providers.gradleProperty("googleMavenUrl").orElse("https://dl-ssl.google.com/dl/android/maven2/").get())
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven(providers.gradleProperty("googleMavenUrl").orElse("https://dl-ssl.google.com/dl/android/maven2/").get())
        mavenCentral()
    }
}
rootProject.name = "SafarVPN"
include(":app")
