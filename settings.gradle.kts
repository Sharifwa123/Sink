pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Sink"

// The mesh/crypto/protocol core is a separate, pure-Kotlin Gradle build
// (no Android dependency) so it can be built and tested without the
// Android SDK — see engine/build.gradle.kts and docs/ARCHITECTURE.md.
includeBuild("engine")

include(":app")

include(":core:common")
include(":core:crypto")
include(":core:database")
include(":core:datastore")
include(":core:networking")
include(":core:logging")
include(":core:permissions")

include(":feature:onboarding")
include(":feature:home")
include(":feature:conversations")
include(":feature:chat")
include(":feature:contacts")
include(":feature:discovery")
include(":feature:mesh")
include(":feature:settings")
include(":feature:education")
