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
    ":app",
    ":core-common",
    ":core-design",
    ":core-gateway",
    ":core-loop",
    ":core-sandbox",
    ":core-storage",
    ":core-tools",
    ":feature-chat",
    ":feature-onboarding",
    ":feature-settings",
)
