pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "TG Drive"
include(":app", ":shared", ":ui", ":skin")

if(providers.gradleProperty("startupBenchmark").orNull=="true")include(":startup-benchmark")
