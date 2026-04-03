plugins {
    `java-library`
    jacoco
    id("specification-java-base-conventions")
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).apply {
        // doclint is disabled because the project does not yet have comprehensive
        // Javadoc comments on all public APIs. Re-enable once a documentation
        // standard is established and all public members are properly documented.
        addStringOption("Xdoclint:none", "-quiet")
    }
}

tasks.withType<JacocoCoverageVerification>().configureEach {
    violationRules {
        rule {
            limit {
                minimum = "1.0".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "1.0".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn("jacocoTestCoverageVerification")
}
