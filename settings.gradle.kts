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

rootProject.name = "quine"

include(
    ":core-common",
    ":core-design",
    ":core-gateway",
    ":core-loop",
    ":core-storage",
    ":core-tools",
)
