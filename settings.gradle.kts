pluginManagement {
    includeBuild("build-logic")

    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        mavenCentral()
    }
}

rootProject.name = "spring-boot-specification-repository"

include(
    ":specification-repository-core",
    ":specification-repository-jpa",
    ":specification-repository-boot3-starter",
    ":specification-repository-boot4-starter",
    ":specification-repository-test-support",
    ":examples:boot3-demo",
    ":examples:boot3-postgres-demo",
    ":examples:boot4-demo",
    ":examples:boot4-postgres-demo"
)
