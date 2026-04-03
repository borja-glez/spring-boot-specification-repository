plugins {
    base
    jacoco
}

allprojects {
    group = property("group") as String
    version = property("version") as String
}

tasks.register("coverage") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs coverage verification for library modules."
    dependsOn(
        ":specification-repository-core:jacocoTestCoverageVerification",
        ":specification-repository-jpa:jacocoTestCoverageVerification",
        ":specification-repository-boot3-starter:jacocoTestCoverageVerification",
        ":specification-repository-boot4-starter:jacocoTestCoverageVerification",
        ":specification-repository-test-support:test"
    )
}

tasks.register("quality") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs tests, coverage verification, and formatting checks."
    dependsOn(
        ":specification-repository-core:test",
        ":specification-repository-jpa:test",
        ":specification-repository-boot3-starter:test",
        ":specification-repository-boot4-starter:test",
        ":specification-repository-test-support:test",
        "coverage",
        "spotlessCheckAll"
    )
}

tasks.register("spotlessCheckAll") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs Spotless check on all modules that have the plugin applied."
    dependsOn(
        ":specification-repository-core:spotlessCheck",
        ":specification-repository-jpa:spotlessCheck",
        ":specification-repository-boot3-starter:spotlessCheck",
        ":specification-repository-boot4-starter:spotlessCheck",
        ":specification-repository-test-support:spotlessCheck",
        ":examples:boot3-demo:spotlessCheck",
        ":examples:boot3-postgres-demo:spotlessCheck",
        ":examples:boot4-demo:spotlessCheck",
        ":examples:boot4-postgres-demo:spotlessCheck"
    )
}

tasks.register("spotlessApplyAll") {
    group = "formatting"
    description = "Runs Spotless apply on all modules that have the plugin applied."
    dependsOn(
        ":specification-repository-core:spotlessApply",
        ":specification-repository-jpa:spotlessApply",
        ":specification-repository-boot3-starter:spotlessApply",
        ":specification-repository-boot4-starter:spotlessApply",
        ":specification-repository-test-support:spotlessApply",
        ":examples:boot3-demo:spotlessApply",
        ":examples:boot3-postgres-demo:spotlessApply",
        ":examples:boot4-demo:spotlessApply",
        ":examples:boot4-postgres-demo:spotlessApply"
    )
}
