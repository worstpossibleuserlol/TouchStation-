pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "TouchStation"
include(":app")          // Existing PS1 edition; preserved on this branch.
include(":ps3-app")      // Separate PS3 pre-alpha frontend and PPU prototype.
