plugins {
    id("specification-library-conventions")
    id("specification-publish-conventions")
}

dependencies {
    api(project(":specification-repository-core"))
    compileOnlyApi(libs.spring.data.jpa)
    compileOnly(libs.jakarta.persistence.api)
    compileOnly(libs.spring.context)
    compileOnly(libs.spring.core)

    testImplementation(libs.spring.data.jpa)
    testImplementation(libs.jakarta.persistence.api)
    testImplementation(libs.spring.context)
    testImplementation(libs.spring.core)
    testImplementation(libs.spring.boot3.starter.test)
    testImplementation(libs.spring.boot3.starter.data.jpa)
    testImplementation(libs.h2)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.postgresql)
}
