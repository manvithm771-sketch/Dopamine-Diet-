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
        google()
        mavenCentral()
    }
}
rootProject.name = "FocusHub"
include(":core-model", ":core-detect", ":core-data", ":service", ":dopamine", ":mindful", ":app")
project(":core-model").projectDir = file("../Dopamine-Diet/DopamineDiet/core-model")
project(":core-detect").projectDir = file("../Dopamine-Diet/DopamineDiet/core-detect")
project(":core-data").projectDir = file("../Dopamine-Diet/DopamineDiet/core-data")
project(":service").projectDir = file("../Dopamine-Diet/DopamineDiet/service")
