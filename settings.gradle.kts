pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "Dopamine Diet"
include(":core-model", ":core-detect", ":core-data", ":service", ":reels", ":wellbeing", ":app")
