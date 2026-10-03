plugins {
    `java-platform`
    id("specification-publish-conventions")
}

description = "Bill of materials that aligns the versions of every published specification-repository module"

// Only this project's modules are constrained. Third-party versions (Spring, Jakarta, Jackson, ...)
// stay owned by the Spring Boot BOM of the consuming application.
dependencies {
    constraints {
        api(project(":specification-repository-core"))
        api(project(":specification-repository-jpa"))
        api(project(":specification-repository-http"))
        api(project(":specification-repository-boot3-starter"))
        api(project(":specification-repository-boot4-starter"))
    }
}
